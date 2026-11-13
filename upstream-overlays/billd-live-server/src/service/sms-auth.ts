import axios from 'axios';
import crypto from 'crypto';

import { peerAddress } from '@/app/request-rate';
import { signJwt } from '@/app/auth/authJwt';
import {
  PHONE_HASH_SECRET,
  SMS_ENABLED,
  SMS_PROVIDER_SECRET,
  SMS_PROVIDER_TEMPLATE_ID,
  SMS_PROVIDER_URL,
} from '@/config/secret';
import sequelize from '@/config/mysql';
import { CustomError } from '@/model/customError.model';
import User from '@/model/user.model';
import UserPhoneIdentity from '@/model/userPhoneIdentity.model';
import { redisClient } from '@/config/redis';
import userService from '@/service/user.service';

const PHONE_PATTERN = /^1[3-9]\d{9}$/;
const CODE_TTL_SECONDS = 300;
const SEND_COOLDOWN_SECONDS = 60;
const SEND_LIMIT_PER_PHONE_PER_DAY = 10;
const SEND_LIMIT_PER_IP_PER_DAY = 50;
const SMS_CODE_KEY = (hash: string) => `auth:sms:code:${hash}`;
const SMS_COOLDOWN_KEY = (hash: string) => `auth:sms:cooldown:${hash}`;
const SMS_PHONE_DAY_KEY = (hash: string) => `auth:sms:phone-day:${hash}`;
const SMS_IP_DAY_KEY = (hash: string) => `auth:sms:ip-day:${hash}`;

const unavailable = () => new CustomError('短信登录服务暂不可用', 503, 503);

export function isSmsLoginConfigured() {
  if (!SMS_ENABLED || PHONE_HASH_SECRET.length < 32 || SMS_PROVIDER_SECRET.length < 32
    || !SMS_PROVIDER_TEMPLATE_ID) return false;
  try {
    const url = new URL(SMS_PROVIDER_URL);
    return url.protocol === 'https:' && !url.username && !url.password;
  } catch {
    return false;
  }
}

function smsProviderUrl() {
  if (!isSmsLoginConfigured()) throw unavailable();
  return new URL(SMS_PROVIDER_URL);
}

function phoneHash(phone: string) {
  return crypto.createHmac('sha256', PHONE_HASH_SECRET).update(phone).digest('hex');
}

function otpHash(hash: string, code: string) {
  return crypto.createHmac('sha256', PHONE_HASH_SECRET).update(`sms-code:${hash}:${code}`).digest('hex');
}

async function claimSendSlot(hash: string, ip: string) {
  if (!redisClient.isReady) throw new CustomError('验证码服务暂不可用，请稍后重试', 503, 503);
  const ipHash = crypto.createHmac('sha256', PHONE_HASH_SECRET).update(`ip:${ip}`).digest('hex');
  const script = [
    "local cooldown = redis.call('SET', KEYS[1], '1', 'EX', tonumber(ARGV[1]), 'NX')",
    'if not cooldown then return -1 end',
    "local phoneCount = redis.call('INCR', KEYS[2])",
    'if phoneCount == 1 then redis.call(\'EXPIRE\', KEYS[2], tonumber(ARGV[2])) end',
    "local ipCount = redis.call('INCR', KEYS[3])",
    'if ipCount == 1 then redis.call(\'EXPIRE\', KEYS[3], tonumber(ARGV[2])) end',
    'if phoneCount > tonumber(ARGV[3]) or ipCount > tonumber(ARGV[4]) then return -2 end',
    'return 1',
  ].join('\n');
  try {
    return Number(await redisClient.eval(script, {
      keys: [SMS_COOLDOWN_KEY(hash), SMS_PHONE_DAY_KEY(hash), SMS_IP_DAY_KEY(ipHash)],
      arguments: [String(SEND_COOLDOWN_SECONDS), '86400', String(SEND_LIMIT_PER_PHONE_PER_DAY), String(SEND_LIMIT_PER_IP_PER_DAY)],
    }));
  } catch {
    throw new CustomError('验证码服务暂不可用，请稍后重试', 503, 503);
  }
}

async function storeCodeAndSend(phone: string, hash: string, providerUrl: URL) {
  const code = String(crypto.randomInt(100000, 1000000));
  const codeKey = SMS_CODE_KEY(hash);
  try {
    await redisClient.setEx(codeKey, CODE_TTL_SECONDS, `${otpHash(hash, code)}|0`);
  } catch {
    throw new CustomError('验证码服务暂不可用，请稍后重试', 503, 503);
  }

  const requestId = crypto.randomUUID();
  const payload = JSON.stringify({
    request_id: requestId,
    to: phone,
    template_id: SMS_PROVIDER_TEMPLATE_ID,
    parameters: { code, expires_minutes: CODE_TTL_SECONDS / 60 },
  });
  const timestamp = String(Math.floor(Date.now() / 1000));
  const signature = crypto.createHmac('sha256', SMS_PROVIDER_SECRET)
    .update(`${timestamp}.${payload}`)
    .digest('hex');

  try {
    await axios.post(providerUrl.toString(), payload, {
      timeout: 8000,
      maxRedirects: 0,
      maxContentLength: 64 * 1024,
      maxBodyLength: 64 * 1024,
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': requestId,
        'X-Request-Timestamp': timestamp,
        'X-Request-Signature': `sha256=${signature}`,
      },
    });
  } catch {
    await redisClient.del(codeKey).catch(() => undefined);
    throw new CustomError('短信发送失败，请稍后重试', 503, 503);
  }
}

export async function sendSmsLoginCode(value: unknown, ctx) {
  if (typeof value !== 'string' || !PHONE_PATTERN.test(value.trim()))
    throw new CustomError('请输入有效的中国大陆手机号', 400, 400);
  const phone = value.trim();
  const providerUrl = smsProviderUrl();
  const hash = phoneHash(phone);
  const slot = await claimSendSlot(hash, peerAddress(ctx));
  if (slot === -1) throw new CustomError('验证码已发送，请稍后再试', 429, 429);
  if (slot === -2) throw new CustomError('验证码请求次数过多，请明天再试', 429, 429);
  if (slot !== 1) throw new CustomError('验证码服务暂不可用，请稍后重试', 503, 503);
  await storeCodeAndSend(phone, hash, providerUrl);
}

async function consumeCode(hash: string, code: string) {
  const expected = otpHash(hash, code);
  const script = [
    "local value = redis.call('GET', KEYS[1])",
    'if not value then return -1 end',
    "local separator = string.find(value, '|', 1, true)",
    'if not separator then redis.call(\'DEL\', KEYS[1]); return -1 end',
    'local saved = string.sub(value, 1, separator - 1)',
    'local attempts = tonumber(string.sub(value, separator + 1)) or 0',
    'if attempts >= 5 then redis.call(\'DEL\', KEYS[1]); return -2 end',
    'if saved ~= ARGV[1] then',
    '  attempts = attempts + 1',
    '  if attempts >= 5 then redis.call(\'DEL\', KEYS[1]); return -2 end',
    "  local ttl = redis.call('PTTL', KEYS[1])",
    "  if ttl <= 0 then redis.call('DEL', KEYS[1]); return -1 end",
    "  redis.call('SET', KEYS[1], saved .. '|' .. attempts, 'PX', ttl)",
    '  return 0',
    'end',
    "redis.call('DEL', KEYS[1])",
    'return 1',
  ].join('\n');
  try {
    return Number(await redisClient.eval(script, { keys: [SMS_CODE_KEY(hash)], arguments: [expected] }));
  } catch {
    throw new CustomError('验证码服务暂不可用，请稍后重试', 503, 503);
  }
}

export async function loginWithSms(phoneValue: unknown, codeValue: unknown) {
  if (typeof phoneValue !== 'string' || !PHONE_PATTERN.test(phoneValue.trim())
    || typeof codeValue !== 'string' || !/^\d{6}$/.test(codeValue)) {
    throw new CustomError('手机号或验证码格式有误', 400, 400);
  }
  smsProviderUrl();
  if (!redisClient.isReady) throw new CustomError('验证码服务暂不可用，请稍后重试', 503, 503);
  const hash = phoneHash(phoneValue.trim());
  const consumed = await consumeCode(hash, codeValue);
  if (consumed === -2) throw new CustomError('验证码错误次数过多，请重新获取', 429, 429);
  if (consumed !== 1) throw new CustomError('验证码错误或已过期', 400, 400);

  return sequelize.transaction(async transaction => {
    const identity: any = await UserPhoneIdentity.findOne({
      where: { phone_hash: hash },
      transaction,
      lock: transaction.LOCK.UPDATE,
    });
    let user: any;
    if (identity) {
      user = await User.findByPk(identity.user_id, {
        attributes: ['id', 'username', 'status'],
        transaction,
        lock: transaction.LOCK.UPDATE,
      });
      if (!user) throw new CustomError('手机号账号暂不可用，请联系平台处理', 503, 503);
    } else {
      const username = `m_${crypto.randomBytes(9).toString('hex')}`;
      user = await userService.create({
        username,
        password: crypto.randomBytes(32).toString('base64url'),
      }, transaction);
      await UserPhoneIdentity.create({ user_id: user.id, phone_hash: hash }, { transaction });
    }

    if (user.status === 2) throw new CustomError('账号已禁用，请联系平台处理', 403, 403);
    const token = signJwt({ userInfo: { id: Number(user.id) }, exp: 24 });
    await user.update({ token }, { transaction });
    return token;
  });
}
