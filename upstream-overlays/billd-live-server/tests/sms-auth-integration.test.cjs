const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const crypto = require('node:crypto');
const mysql = require('mysql2/promise');
const { Sequelize } = require('sequelize');
const { createClient } = require('redis');
require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));

function stub(file, exports) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename: id, loaded: true, exports };
}

test('SMS authentication uses atomic real Redis limits and transactional MySQL ordinary accounts', {
  skip: !process.env.MYSQL_TEST_URL || !process.env.REDIS_TEST_URL, timeout: 45000,
}, async () => {
  const url = new URL(process.env.MYSQL_TEST_URL);
  const name = 'live_test_sms_' + Date.now();
  const admin = await mysql.createConnection({ host: url.hostname, port: Number(url.port || 3306),
    user: decodeURIComponent(url.username), password: decodeURIComponent(url.password) });
  const redis = createClient({ url: process.env.REDIS_TEST_URL, socket: { reconnectStrategy: false } });
  redis.on('error', () => {});
  const hashSecret = crypto.randomBytes(32).toString('hex');
  const settings = { SMS_ENABLED: true, SMS_PROVIDER_URL: 'https://sms.example.test/send',
    SMS_PROVIDER_SECRET: crypto.randomBytes(32).toString('hex'), SMS_PROVIDER_TEMPLATE_ID: 'login',
    PHONE_HASH_SECRET: hashSecret, JWT_SECRET: crypto.randomBytes(32).toString('hex') };
  const ip = '192.0.2.' + crypto.randomInt(1, 255);
  const ctx = { req: { socket: { remoteAddress: ip } } };
  const phone = '139' + String(crypto.randomInt(0, 100000000)).padStart(8, '0');
  const hash = crypto.createHmac('sha256', hashSecret).update(phone).digest('hex');
  const ipHash = crypto.createHmac('sha256', hashSecret).update('ip:' + ip).digest('hex');
  const keys = { code: `auth:sms:code:${hash}`, cooldown: `auth:sms:cooldown:${hash}`,
    phone: `auth:sms:phone-day:${hash}`, ip: `auth:sms:ip-day:${ipHash}` };
  let db, sent, sends = 0, failProvider = false;
  const axios = require('axios'), originalPost = axios.post;
  try {
    await admin.query(`CREATE DATABASE \`${name}\``);
    db = new Sequelize(name, decodeURIComponent(url.username), decodeURIComponent(url.password), {
      host: url.hostname, port: Number(url.port || 3306), dialect: 'mysql', logging: false });
    await redis.connect();
    stub('@/config/mysql', { __esModule: true, default: db });
    stub('@/config/secret', settings);
    const redisModule = { redisClient: redis };
    stub('@/config/redis', redisModule);
    stub('@/utils', { initTable() {} });
    const User = require('../dist/model/user.model').default;
    const Role = require('../dist/model/role.model').default;
    const UserRole = require('../dist/model/userRole.model').default;
    const Identity = require('../dist/model/userPhoneIdentity.model').default;
    require('../dist/model/relation');
    const sms = require('../dist/service/sms-auth');
    const userService = require('../dist/service/user.service').default;
    await db.sync();
    const adminRole = await Role.create({ role_value: 'SUPER_ADMIN', role_name: 'Administrator', type: 2 });
    axios.post = async (endpoint, payload, options) => {
      sends++;
      assert.equal(endpoint, settings.SMS_PROVIDER_URL);
      assert.equal(options.maxRedirects, 0);
      assert.equal(options.timeout, 8000);
      const expected = crypto.createHmac('sha256', settings.SMS_PROVIDER_SECRET)
        .update(options.headers['X-Request-Timestamp'] + '.' + payload).digest('hex');
      assert.equal(options.headers['X-Request-Signature'], 'sha256=' + expected);
      if (failProvider) throw Error('isolated provider failure');
      sent = JSON.parse(payload);
      assert.equal(sent.to, phone);
      assert.match(sent.parameters.code, /^\d{6}$/);
      assert.equal(sent.parameters.expires_minutes, 5);
      return { status: 202 };
    };
    const rejectsStatus = (promise, status) => assert.rejects(promise, error => error.statusCode === status);
    assert.equal(sms.isSmsLoginConfigured(), true);
    settings.SMS_ENABLED = false;
    assert.equal(sms.isSmsLoginConfigured(), false);
    await rejectsStatus(sms.sendSmsLoginCode(phone, ctx), 503);
    await rejectsStatus(sms.loginWithSms(phone, '123456'), 503);
    assert.equal(sends, 0);
    settings.SMS_ENABLED = true;
    for (const badUrl of ['http://sms.example.test', 'https://user:pass@sms.example.test', 'invalid']) {
      settings.SMS_PROVIDER_URL = badUrl;
      assert.equal(sms.isSmsLoginConfigured(), false);
    }
    settings.SMS_PROVIDER_URL = 'https://sms.example.test/send';
    await rejectsStatus(sms.sendSmsLoginCode('123', ctx), 400);
    await rejectsStatus(sms.loginWithSms(phone, {}), 400);
    redisModule.redisClient = { isReady: false };
    await rejectsStatus(sms.sendSmsLoginCode(phone, ctx), 503);
    await rejectsStatus(sms.loginWithSms(phone, '123456'), 503);
    redisModule.redisClient = redis;

    await sms.sendSmsLoginCode(phone, ctx);
    assert.equal(sends, 1);
    assert.ok(!(await redis.get(keys.code)).includes(sent.parameters.code));
    assert.ok(await redis.ttl(keys.code) > 290);
    await rejectsStatus(sms.sendSmsLoginCode(phone, ctx), 429);
    assert.equal(sends, 1);
    await redis.pExpire(keys.code, 2000);
    const wrongCode = sent.parameters.code === '000000' ? '000001' : '000000';
    for (let i = 0; i < 4; i++) await rejectsStatus(sms.loginWithSms(phone, wrongCode), 400);
    assert.ok(await redis.pTTL(keys.code) <= 2000);
    await rejectsStatus(sms.loginWithSms(phone, wrongCode), 429);
    assert.equal(await redis.get(keys.code), null);
    await rejectsStatus(sms.loginWithSms(phone, sent.parameters.code), 400);

    await redis.del(keys.cooldown);
    await redis.set(keys.phone, '10');
    await rejectsStatus(sms.sendSmsLoginCode(phone, ctx), 429);
    await redis.del([keys.cooldown, keys.phone]);
    await redis.set(keys.ip, '50');
    await rejectsStatus(sms.sendSmsLoginCode(phone, ctx), 429);
    await redis.del([keys.cooldown, keys.phone, keys.ip]);
    failProvider = true;
    await rejectsStatus(sms.sendSmsLoginCode(phone, ctx), 503);
    assert.equal(await redis.get(keys.code), null);
    failProvider = false;
    await redis.del(keys.cooldown);
    await sms.sendSmsLoginCode(phone, ctx);
    const results = await Promise.allSettled(Array.from({ length: 4 }, () => sms.loginWithSms(phone, sent.parameters.code)));
    assert.equal(results.filter(result => result.status === 'fulfilled').length, 1);
    assert.equal(await Identity.count(), 1);
    assert.equal(await User.count(), 1);
    const identity = await Identity.findOne();
    assert.equal(identity.phone_hash, hash);
    const user = await User.findByPk(identity.user_id);
    const roles = await user.getRoles();
    assert.deepEqual(roles.map(role => role.role_value), ['USER']);
    assert.ok(roles.every(role => role.id !== adminRole.id));
    const token = results.find(result => result.status === 'fulfilled').value;
    assert.equal(user.token, token);
    const claims = require('jsonwebtoken').verify(token, settings.JWT_SECRET);
    assert.deepEqual(claims.userInfo, { id: user.id });
    assert.equal(claims.exp - claims.iat, 86400);
    assert.ok(require('../dist/app/auth/password').isPasswordHash(user.password));

    await redis.del(keys.cooldown);
    await sms.sendSmsLoginCode(phone, ctx);
    const nextToken = await sms.loginWithSms(phone, sent.parameters.code);
    assert.notEqual(nextToken, token);
    assert.equal(await User.count(), 1);
    await user.update({ status: 2 });
    await redis.del(keys.cooldown);
    await sms.sendSmsLoginCode(phone, ctx);
    await rejectsStatus(sms.loginWithSms(phone, sent.parameters.code), 403);
    assert.equal((await user.reload()).token, nextToken);

    // User creation and role assignment must commit together, including password registration.
    const before = await User.count();
    await admin.query(`CREATE TRIGGER \`${name}\`.reject_role BEFORE INSERT ON \`${name}\`.user_role FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated role failure'`);
    await assert.rejects(userService.create({ username: 'rollback-account', password: 'test-password' }));
    assert.equal(await User.count(), before);
    assert.equal(await UserRole.count(), 1);
  } finally {
    axios.post = originalPost;
    if (redis.isOpen) { await redis.del(Object.values(keys)); await redis.quit(); }
    if (db) await db.close();
    await admin.query(`DROP DATABASE IF EXISTS \`${name}\``);
    await admin.end();
  }
});
