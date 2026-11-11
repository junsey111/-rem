const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const mysql = require('mysql2/promise');
const { Sequelize } = require('sequelize');
const Koa = require('koa');
const Router = require('koa-router');
const jwt = require('jsonwebtoken');

require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, exports) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename: id, loaded: true, exports };
}

test('email code is required for registration and can log in to the bound account', {
  skip: !process.env.MYSQL_TEST_URL, timeout: 30000,
}, async () => {
  process.env.EMAIL_CODE_LOCAL_STORE = 'true';
  const url = new URL(process.env.MYSQL_TEST_URL);
  const name = `live_test_email_${Date.now()}`;
  const secret = 'isolated-email-login-key';
  const admin = await mysql.createConnection({ host: url.hostname, port: Number(url.port || 3306),
    user: decodeURIComponent(url.username), password: decodeURIComponent(url.password) });
  let db;
  let server;
  try {
    await admin.query(`CREATE DATABASE \`${name}\``);
    db = new Sequelize(name, decodeURIComponent(url.username), decodeURIComponent(url.password), {
      host: url.hostname, port: Number(url.port || 3306), dialect: 'mysql', logging: false,
    });
    stub('@/config/mysql', { __esModule: true, default: db });
    stub('@/config/secret', { JWT_SECRET: secret, QQ_EMAIL_USER: 'sender@example.com', QQ_EMAIL_PASS: 'test-only' });
    stub('@/config/redis', { redisClient: { isOpen: false } });
    stub('@/utils', { initTable: () => {} });
    const sent = [];
    stub('nodemailer', { createTransport: () => ({ sendMail: async message => { sent.push(message); } }) });
    const User = require('../dist/model/user.model').default;
    const EmailUser = require('../dist/model/emailUser.model').default;
    const ThirdUser = require('../dist/model/thirdUser.model').default;
    await User.sync();
    await EmailUser.sync();
    await ThirdUser.sync();
    stub('@/service/user.service', { __esModule: true, default: {
      isSameName: username => User.findOne({ where: { username } }),
      create: (props, transaction) => User.create({ ...props, public_id: 567891 }, { transaction }),
      find: id => User.findByPk(id, { attributes: { exclude: ['password', 'token'] } }),
      findAndToken: id => User.findByPk(id, { attributes: { exclude: ['password'] } }),
    } });
    const emailRouter = require('../dist/router/email-login.router');
    const { passwordLogin } = require('../dist/app/auth/password-login');
    const loginRouter = new Router();
    loginRouter.post('/user/create', emailRouter.registerWithEmail);
    loginRouter.post('/user/login', passwordLogin);
    const app = new Koa();
    app.on('error', require('../dist/app/handler/error-handle').default);
    app.use(require('../dist/app/app.middleware').catchErrorMiddle);
    app.use(require('koa-bodyparser')());
    app.use(emailRouter.default.routes());
    app.use(loginRouter.routes());
    server = app.listen(0, '127.0.0.1');
    await new Promise(resolve => server.once('listening', resolve));
    const call = async (route, body, token) => {
      const response = await fetch(`http://127.0.0.1:${server.address().port}${route}`, {
        method: body === undefined ? 'GET' : 'POST',
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
      return { status: response.status, body: await response.json() };
    };
    const email = 'new@example.com';
    assert.equal((await call('/user/create', { username: 'new-user', password: 'secret', email })).status, 400);
    assert.equal((await call('/user/email/code', { email, purpose: 'register' })).status, 200);
    const registerCode = sent.at(-1).text.match(/\b[0-9]{6}\b/)[0];
    assert.equal((await call('/user/create', { username: 'new-user', password: 'secret', email, code: '000000' })).status, 400);
    const created = await call('/user/create', { username: 'new-user', password: 'secret', email, code: registerCode });
    assert.equal(created.status, 200);
    assert.equal((await EmailUser.findOne({ where: { email } })).email, email);
    assert.equal((await call('/user/create', { username: 'another-user', password: 'secret', email, code: registerCode })).status, 400);
    const passwordSession = await call('/user/login', { username: 'new-user', password: 'secret' });
    assert.equal(passwordSession.status, 200);
    assert.equal((await call('/user/login', { username: 'NEW-USER', password: 'secret' })).status, 400);
    assert.equal((await call('/user/email/binding', undefined, passwordSession.body.data)).body.data.email, email);
    assert.equal((await call('/user/email/code', { email, purpose: 'login' })).status, 200);
    const loginCode = sent.at(-1).text.match(/\b[0-9]{6}\b/)[0];
    const emailSession = await call('/user/email/login', { email, code: loginCode });
    assert.equal(emailSession.status, 200);
    assert.equal(jwt.verify(emailSession.body.data, secret).userInfo.id, created.body.data.id);
    assert.equal((await call('/user/email/login', { email, code: loginCode })).status, 400);
    const existing = await User.create({ username: 'existing-user', password: 'existing-secret', public_id: 567892, status: 1 });
    const existingLogin = await call('/user/login', { username: 'existing-user', password: 'existing-secret' });
    assert.equal(existingLogin.status, 200);
    const existingToken = existingLogin.body.data;
    const secondEmail = 'existing@example.com';
    assert.equal((await call('/user/email/code', { email: secondEmail, purpose: 'bind' }, existingToken)).status, 200);
    const bindCode = sent.at(-1).text.match(/\b[0-9]{6}\b/)[0];
    assert.equal((await call('/user/email/bind', { email: secondEmail, code: bindCode }, existingToken)).status, 200);
    assert.equal((await call('/user/email/binding', undefined, existingToken)).body.data.email, secondEmail);
    assert.equal((await call('/user/email/code', { email: secondEmail, purpose: 'login' })).status, 200);
    const secondLoginCode = sent.at(-1).text.match(/\b[0-9]{6}\b/)[0];
    const secondSession = await call('/user/email/login', { email: secondEmail, code: secondLoginCode });
    assert.equal(secondSession.status, 200);
    assert.equal(jwt.verify(secondSession.body.data, secret).userInfo.id, existing.id);
  } finally {
    if (server) await new Promise(resolve => server.close(resolve));
    if (db) await db.close();
    await admin.query(`DROP DATABASE IF EXISTS \`${name}\``);
    await admin.end();
  }
});
