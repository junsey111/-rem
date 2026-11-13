import { randomInt, timingSafeEqual } from 'node:crypto';
import Router from 'koa-router';
import nodemailer from 'nodemailer';

import { authJwt, signJwt } from '@/app/auth/authJwt';
import { validPassword } from '@/app/auth/password';
import { peerAddress, RequestRateLimiter } from '@/app/request-rate';
import successHandler from '@/app/handler/success-handle';
import { QQ_EMAIL_PASS, QQ_EMAIL_USER } from '@/config/secret';
import { redisClient } from '@/config/redis';
import sequelize from '@/config/mysql';
import { THIRD_PLATFORM } from '@/constant';
import { CustomError } from '@/model/customError.model';
import EmailUser from '@/model/emailUser.model';
import ThirdUser from '@/model/thirdUser.model';
import User from '@/model/user.model';
import userService from '@/service/user.service';

const router = new Router({ prefix: '/user/email' });
const sendLimit = new RequestRateLimiter();
const codeLifetime = 300;
const smtpUser = process.env.SMTP_USER || QQ_EMAIL_USER;
const smtpPass = process.env.SMTP_PASS || QQ_EMAIL_PASS;
const smtpHost = process.env.SMTP_HOST || (QQ_EMAIL_USER ? 'smtp.qq.com' : '');
const smtpPort = Number(process.env.SMTP_PORT || 465);
const smtpFrom = process.env.SMTP_FROM || smtpUser;
const localStoreEnabled = process.env.EMAIL_CODE_LOCAL_STORE === 'true';
const localCodes = new Map<string, { value: string; expiresAt: number }>();

function localValue(key: string): string | null {
  const entry = localCodes.get(key);
  if (!entry) return null;
  if (entry.expiresAt <= Date.now()) { localCodes.delete(key); return null; }
  return entry.value;
}

async function readCode(key: string) {
  return localStoreEnabled ? localValue(key) : redisClient.get(key);
}

async function saveCode(key: string, value: string, seconds: number) {
  if (localStoreEnabled) localCodes.set(key, { value, expiresAt: Date.now() + seconds * 1000 });
  else await redisClient.setEx(key, seconds, value);
}

async function reserveCooldown(key: string): Promise<boolean> {
  if (localStoreEnabled) {
    if (localValue(key)) return false;
    localCodes.set(key, { value: '1', expiresAt: Date.now() + 60_000 });
    return true;
  }
  return await redisClient.set(key, '1', { EX: 60, NX: true }) === 'OK';
}

async function countAttempt(key: string): Promise<number> {
  if (localStoreEnabled) {
    const count = Number(localValue(key) || 0) + 1;
    localCodes.set(key, { value: String(count), expiresAt: Date.now() + codeLifetime * 1000 });
    return count;
  }
  const count = await redisClient.incr(key);
  if (count === 1) await redisClient.expire(key, codeLifetime);
  return count;
}

async function deleteCodes(...keys: string[]) {
  if (localStoreEnabled) keys.forEach(key => localCodes.delete(key));
  else await redisClient.del(keys);
}

function requireCodeService() {
  if (!smtpHost || !smtpUser || !smtpPass || !smtpFrom || !Number.isInteger(smtpPort) || smtpPort < 1 || smtpPort > 65535) {
    throw new CustomError('邮箱验证码服务尚未配置，请稍后再试', 503, 503);
  }
  if (!localStoreEnabled && !redisClient.isOpen) throw new CustomError('邮箱验证码服务暂不可用，请稍后再试', 503, 503);
}

function emailFrom(body: any): string {
  const email = typeof body?.email === 'string' ? body.email.trim().toLowerCase() : '';
  if (email.length > 254 || !/^[^\s@]{1,64}@[^\s@]{1,190}$/.test(email)) {
    throw new CustomError('请输入有效的邮箱地址', 400, 400);
  }
  return email;
}

async function authenticatedId(ctx): Promise<number> {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id) {
    throw new CustomError(auth.message || '请重新登录', auth.code || 401, auth.code || 401);
  }
  return Number(auth.userInfo.id);
}

async function boundEmail(userId: number) {
  const relation: any = await ThirdUser.findOne({ where: { user_id: userId, third_platform: THIRD_PLATFORM.email } });
  return relation ? EmailUser.findByPk(relation.third_user_id) : null;
}

async function ownerOf(email: string): Promise<number | null> {
  const record: any = await EmailUser.findOne({ where: { email } });
  if (!record) return null;
  const relation: any = await ThirdUser.findOne({ where: { third_user_id: record.id, third_platform: THIRD_PLATFORM.email } });
  return relation ? Number(relation.user_id) : null;
}

function codeKey(purpose: string, email: string, userId?: number) {
  return `qumi-email-${purpose}:${userId || 0}:${email}`;
}

async function verifyCode(purpose: string, email: string, code: unknown, userId?: number) {
  if (typeof code !== 'string' || !/^[0-9]{6}$/.test(code)) throw new CustomError('请输入6位数字验证码', 400, 400);
  const key = codeKey(purpose, email, userId);
  const attemptsKey = `${key}:attempts`;
  const attempts = await countAttempt(attemptsKey);
  if (attempts > 5) throw new CustomError('验证码尝试过多，请5分钟后重试', 429, 429);
  const saved = await readCode(key);
  if (!saved || !timingSafeEqual(Buffer.from(saved), Buffer.from(code))) {
    throw new CustomError('验证码错误或已过期', 400, 400);
  }
  return { key, attemptsKey };
}

router.post('/code', async ctx => {
  const email = emailFrom(ctx.request.body);
  const purpose = ctx.request.body?.purpose;
  if (purpose !== 'login' && purpose !== 'bind' && purpose !== 'register') throw new CustomError('验证码用途无效', 400, 400);
  const userId = purpose === 'bind' ? await authenticatedId(ctx) : undefined;
  requireCodeService();
  if (sendLimit.consume(`email:${peerAddress(ctx)}`, 10)) throw new CustomError('发送过于频繁，请稍后再试', 429, 429);
  if (purpose === 'login' && !await ownerOf(email)) {
    throw new CustomError('该邮箱尚未绑定账号，请先用密码登录并在设置中绑定', 400, 400);
  }
  if (purpose === 'register' && await EmailUser.findOne({ where: { email } })) {
    throw new CustomError('该邮箱已绑定账号，请直接登录', 400, 400);
  }
  if (purpose === 'bind') {
    if (await boundEmail(userId!)) throw new CustomError('当前账号已绑定邮箱', 400, 400);
    if (await ownerOf(email)) throw new CustomError('该邮箱已绑定其他账号', 400, 400);
  }
  const key = codeKey(purpose, email, userId);
  const cooldown = `${key}:cooldown`;
  if (!await reserveCooldown(cooldown)) {
    throw new CustomError('请60秒后再发送验证码', 429, 429);
  }
  const code = String(randomInt(100000, 1000000));
  try {
    await saveCode(key, code, codeLifetime);
    const transport = nodemailer.createTransport({ host: smtpHost, port: smtpPort, secure: smtpPort === 465,
      auth: { user: smtpUser, pass: smtpPass } });
    await transport.sendMail({ from: smtpFrom, to: email, subject: `现场${purpose === 'register' ? '注册' : purpose === 'bind' ? '绑定' : '登录'}验证码`,
      text: `验证码：${code}。5分钟内有效，请勿告知他人。` });
  } catch (_) {
    await deleteCodes(key, cooldown);
    throw new CustomError('验证码发送失败，请稍后重试', 503, 503);
  }
  successHandler({ ctx, message: '验证码已发送' });
});

export async function registerWithEmail(ctx, next) {
  requireCodeService();
  const { username, password, code } = ctx.request.body || {};
  const email = emailFrom(ctx.request.body);
  if (typeof username !== 'string' || !username.trim() || username !== username.trim()
    || Array.from(username).length > 20 || !validPassword(password)) {
    throw new CustomError('请填写有效的用户名和密码', 400, 400);
  }
  if (await userService.isSameName(username)) throw new CustomError('用户名已被使用', 400, 400);
  if (await EmailUser.findOne({ where: { email } })) throw new CustomError('该邮箱已绑定账号', 400, 400);
  const verified = await verifyCode('register', email, code);
  const userId = await sequelize.transaction(async transaction => {
    const user: any = await userService.create({ username, password }, transaction);
    const record: any = await EmailUser.create({ email }, { transaction });
    await ThirdUser.create({ user_id: user.id, third_user_id: record.id, third_platform: THIRD_PLATFORM.email }, { transaction });
    return Number(user.id);
  });
  await deleteCodes(verified.key, verified.attemptsKey);
  const user: any = await userService.find(userId);
  successHandler({ ctx, data: user, message: '注册成功' });
  await next();
}

router.post('/login', async ctx => {
  requireCodeService();
  const email = emailFrom(ctx.request.body);
  const verified = await verifyCode('login', email, ctx.request.body?.code);
  const userId = await ownerOf(email);
  if (!userId) throw new CustomError('该邮箱尚未绑定账号', 400, 400);
  const token = await sequelize.transaction(async transaction => {
    const user: any = await User.findByPk(userId, { attributes: ['id', 'status'], transaction, lock: transaction.LOCK.UPDATE });
    if (!user) throw new CustomError('账号不存在', 404, 404);
    if (user.status === 2) throw new CustomError('账号已禁用，请联系平台处理', 403, 403);
    const issued = signJwt({ userInfo: { id: Number(user.id) }, exp: 24 });
    await user.update({ token: issued }, { transaction });
    return issued;
  });
  await deleteCodes(verified.key, verified.attemptsKey);
  successHandler({ ctx, data: token, message: '登录成功' });
});

router.get('/binding', async ctx => {
  const email: any = await boundEmail(await authenticatedId(ctx));
  successHandler({ ctx, data: { email: email?.email || null } });
});

router.post('/bind', async ctx => {
  requireCodeService();
  const userId = await authenticatedId(ctx);
  const email = emailFrom(ctx.request.body);
  const verified = await verifyCode('bind', email, ctx.request.body?.code, userId);
  await sequelize.transaction(async transaction => {
    const current: any = await ThirdUser.findOne({ where: { user_id: userId, third_platform: THIRD_PLATFORM.email }, transaction });
    if (current) throw new CustomError('当前账号已绑定邮箱', 400, 400);
    if (await ownerOf(email)) throw new CustomError('该邮箱已绑定其他账号', 400, 400);
    const record: any = await EmailUser.create({ email }, { transaction });
    await ThirdUser.create({ user_id: userId, third_user_id: record.id, third_platform: THIRD_PLATFORM.email }, { transaction });
  });
  await deleteCodes(verified.key, verified.attemptsKey);
  successHandler({ ctx, message: '邮箱绑定成功' });
});

export default router;
