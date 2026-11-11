const { test } = require('node:test');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const path = require('node:path');
const mysql = require('mysql2/promise');
const { Sequelize } = require('sequelize');
const Koa = require('koa');
const sharp = require('sharp');
require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, exports) { const id = require.resolve(file); require.cache[id] = { id, filename: id, loaded: true, exports }; }

test('announcement covers and rich documents persist with permissions, publication filtering and optimistic versions', { skip: !process.env.MYSQL_TEST_URL }, async () => {
  const url = new URL(process.env.MYSQL_TEST_URL), name = `live_test_announcements_${Date.now()}`;
  const admin = await mysql.createConnection({ host: url.hostname, port: Number(url.port), user: url.username, password: url.password });
  let db, server, operator = true, authenticated = true;
  try {
    await admin.query(`CREATE DATABASE \`${name}\``);
    db = new Sequelize(name, url.username, url.password, { host: url.hostname, port: Number(url.port), dialect: 'mysql', logging: false });
    stub('@/config/mysql', { __esModule: true, default: db });
    stub('@/app/auth/authJwt', { authJwt: async()=>authenticated?{code:200,userInfo:{id:8}}:{code:401} });
    stub('@/app/auth/operations', { requireOperations: async () => {
      if (!operator) { const error = new Error('Forbidden'); error.statusCode = 403; throw error; }
      return { id: 7 };
    } });
    const router = require('../dist/router/platform-content.router').default;
    await db.sync();
    const app = new Koa();
    app.use(async (ctx, next) => { try { await next(); } catch (error) { ctx.status = error.statusCode || 500; ctx.body = { message: error.message }; } });
    app.use(require('koa-bodyparser')({ jsonLimit: '128kb' })); app.use(router.routes());
    server = app.listen(0, '127.0.0.1'); await new Promise(resolve => server.once('listening', resolve));
    const call = async (route, method = 'GET', body) => {
      const binary = Buffer.isBuffer(body);
      const response = await fetch(`http://127.0.0.1:${server.address().port}${route}`, { method,
        headers: { 'Content-Type': binary ? 'application/octet-stream' : 'application/json' },
        ...(body ? { body: binary ? body : JSON.stringify(body) } : {}) });
      const bytes = Buffer.from(await response.arrayBuffer());
      return { status: response.status, bytes, json: response.headers.get('content-type')?.includes('application/json') ? JSON.parse(bytes) : null };
    };
    const image = await sharp({ create: { width: 640, height: 200, channels: 3, background: '#7951ed' } }).png().toBuffer();
    const follow = '/platform-content/secretary/follow';
    assert.equal((await call(follow)).json.data.following,false);
    assert.equal((await call(follow,'PUT')).json.data.following,true);
    assert.equal((await call(follow,'PUT')).json.data.following,true);
    assert.equal((await call(follow)).json.data.following,true);
    assert.equal((await call(follow,'DELETE')).json.data.following,false);
    authenticated=false;assert.equal((await call(follow,'PUT')).status,401);authenticated=true;
    operator = false;
    assert.equal((await call('/operations/content-images', 'POST', image)).status, 403);
    assert.equal((await call('/operations/content/announcements')).status, 403);
    operator = true;
    assert.equal((await call('/operations/content-images', 'POST', Buffer.from('<svg onload="alert(1)"/>'))).status, 400);
    const uploaded = await call('/operations/content-images', 'POST', image);
    assert.equal(uploaded.status, 200);
    const imagePath = uploaded.json.data.image_path;
    const served = await call(imagePath); assert.equal(served.status, 200);
    assert.equal((await sharp(served.bytes).metadata()).format, 'jpeg');
    const plain = { title: '原有公告', body: '纯文本兼容', published: true, published_at: '2026-11-12' };
    const rich = { title: '图文公告', body: '活动详情', published: true, published_at: '2026-11-12', cover_path: imagePath,
      blocks: [{ type: 'heading', level: 2, align: 'center', runs: [{ text: '活动详情', bold: true }] }, { type: 'image', url: imagePath }] };
    const payload = { request_id: randomUUID(), expected_version: 0, reason: '图文测试', value: { items: [plain, rich, { ...plain, title: '草稿', published: false }] } };
    assert.equal((await call('/operations/content/announcements', 'PUT', payload)).status, 200);
    assert.equal((await call('/operations/content/announcements', 'PUT', payload)).status, 200);
    assert.equal((await call('/operations/content/announcements', 'PUT', { ...payload, request_id: randomUUID() })).status, 409);
    const publicContent = (await call('/platform-content/announcements')).json.data.value.items;
    assert.equal(publicContent.length, 2); assert.deepEqual(publicContent[0], plain); assert.deepEqual(publicContent[1], rich);
    const invalid = { ...payload, request_id: randomUUID(), expected_version: 1, value: { items: [{ ...rich, cover_path: 'https://example.com/remote.jpg' }] } };
    assert.equal((await call('/operations/content/announcements', 'PUT', invalid)).status, 400);
    operator = false; assert.equal((await call('/operations/content/announcements', 'PUT', { ...payload, expected_version: 1 })).status, 403);
  } finally {
    if (server) await new Promise(resolve => server.close(resolve)); if (db) await db.close();
    await admin.query(`DROP DATABASE IF EXISTS \`${name}\``); await admin.end();
  }
});
