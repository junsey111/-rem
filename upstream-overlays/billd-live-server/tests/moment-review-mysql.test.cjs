const { test } = require('node:test');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const path = require('node:path');
const mysql = require('mysql2/promise');
const { Sequelize, DataTypes } = require('sequelize');
const Koa = require('koa');
const sharp = require('sharp');
require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, exports) { const id = require.resolve(file); require.cache[id] = { id, filename: id, loaded: true, exports }; }

test('prepublication review protects all feeds and original media, with versioned and audited decisions', { skip: !process.env.MYSQL_TEST_URL }, async () => {
  const url = new URL(process.env.MYSQL_TEST_URL), name = `live_test_moment_review_${Date.now()}`;
  const admin = await mysql.createConnection({ host: url.hostname, port: Number(url.port), user: url.username, password: url.password });
  let db, server;
  try {
    await admin.query(`CREATE DATABASE \`${name}\``);
    db = new Sequelize(name, url.username, url.password, { host: url.hostname, port: Number(url.port), dialect: 'mysql', logging: false });
    stub('@/config/mysql', { __esModule: true, default: db });
    stub('@/config/secret', { REVENUE_API_URL: 'http://revenue.test', INTERNAL_SERVICE_KEY: 'test' });
    stub('@/utils/request', { __esModule: true, default: { get: async () => ({ reviews: [{ status: 'APPROVED' }] }) } });
    stub('@/app/auth/authJwt', { authJwt: async ctx => {
      const id = Number(ctx.get('authorization').slice(7));
      return id > 0 ? { code: 200, userInfo: { id } } : { code: 401 };
    } });
    stub('@/app/auth/operations', { requireOperations: async ctx => {
      if (ctx.get('authorization') !== 'Bearer 9') { const error = new Error('Forbidden'); error.statusCode = 403; throw error; }
      return { id: 9 };
    }, canManageOperations: async () => false });
    const User = db.define('user', { id: { type: DataTypes.INTEGER, primaryKey: true }, username: DataTypes.STRING,
      avatar: DataTypes.STRING, status: DataTypes.INTEGER }, { freezeTableName: true, timestamps: false });
    stub('@/model/user.model', { __esModule: true, default: User });
    const momentsRouter = require('../dist/router/moments.router').default;
    const reviewRouter = require('../dist/router/moment-review.router').default;
    const operationsRouter = require('../dist/router/operations.router').default;
    const { Moment, MomentMedia, MomentFavorite, MomentLike } = require('../dist/model/moment.model');
    const { default: Review, MomentReviewAction } = require('../dist/model/momentReview.model');
    await db.sync();
    await db.query('CREATE TABLE user_block_relation (blocker_id INT NOT NULL, blocked_id INT NOT NULL)');
    await User.bulkCreate([{ id: 1, username: '作者', status: 1 }, { id: 2, username: '观众', status: 1 }]);
    const legacy = await Moment.create({ user_id: 1, content: '已发布的旧内容' });
    const app = new Koa();
    app.use(async (ctx, next) => { try { await next(); } catch (error) { ctx.status = error.statusCode || 500; ctx.body = { message: error.message }; } });
    app.use(require('koa-bodyparser')());
    for (const router of [momentsRouter, reviewRouter, operationsRouter]) app.use(router.routes());
    server = app.listen(0, '127.0.0.1'); await new Promise(resolve => server.once('listening', resolve));
    const call = async (route, method = 'GET', body, user = 2, extra = {}) => {
      const binary = Buffer.isBuffer(body);
      const response = await fetch(`http://127.0.0.1:${server.address().port}${route}`, { method,
        headers: { authorization: `Bearer ${user}`, 'content-type': binary ? 'application/octet-stream' : 'application/json', ...extra },
        ...(body ? { body: binary ? body : JSON.stringify(body) } : {}) });
      const bytes = Buffer.from(await response.arrayBuffer());
      return { status: response.status, bytes, json: response.headers.get('content-type')?.includes('application/json') ? JSON.parse(bytes) : null };
    };
    const feed = async (scope = 'all', user = 2) => (await call(`/moments?scope=${scope}`, 'GET', null, user)).json.data.moments;
    assert.equal((await feed()).length, 1);
    const text = (await call('/moments', 'POST', { content: '等待审核的文字' }, 1)).json.data;
    assert.equal(text.review_status, 'PENDING');
    const png = await sharp({ create: { width: 10, height: 10, channels: 3, background: '#7945dd' } }).png().toBuffer();
    const upload = async (kind, bytes) => call('/moments', 'POST', bytes, 1, {
      'x-moment-meta': Buffer.from(JSON.stringify({ kind, content: `待审${kind}` })).toString('base64'),
    });
    const image = (await upload('image', png)).json.data;
    const video = (await upload('video', Buffer.concat([Buffer.from([0, 0, 0, 16]), Buffer.from('ftypisom'), Buffer.alloc(8)]))).json.data;
    await MomentLike.create({ moment_id: video.id, user_id: 2 });
    await MomentFavorite.create({ moment_id: video.id, user_id: 2 });
    for (const scope of ['all', 'following', 'video', 'liked_post', 'liked_video', 'favorite_video', 'user&user_id=1']) {
      const rows = await feed(scope);
      assert.ok(rows.every(row => String(row.id) === String(legacy.id)), `pending content leaked through ${scope}`);
    }
    const mine = await feed('mine', 1);
    assert.equal(mine.filter(row => row.review_status === 'PENDING').length, 3);
    assert.equal((await call(image.media_path)).status, 404);
    assert.equal((await call(`/moments/${text.id}/comments`)).status, 404);
    assert.equal((await call(`/moments/${text.id}/like`, 'PUT')).status, 404);
    assert.equal((await call(image.media_path, 'GET', null, 1)).status, 200);
    assert.equal((await call(`/operations/moments/${image.id}/media`)).status, 403);
    assert.equal((await call(`/operations/moments/${image.id}/media`, 'GET', null, 0)).status, 403);
    const preview = await call(`/operations/moments/${image.id}/media`, 'GET', null, 9);
    assert.equal(preview.status, 200); assert.equal((await sharp(preview.bytes).metadata()).format, 'jpeg');
    const pending = (await call('/operations/moments?review_status=PENDING', 'GET', null, 9)).json.data.moments;
    assert.equal(pending.length, 3);
    const review = (id, body, user = 9) => call(`/operations/moments/${id}/review`, 'POST', body, user);
    const decision = { decision: 'APPROVED', expected_version: 1, request_id: randomUUID(), operator_id: 888 };
    assert.equal((await review(video.id, decision, 2)).status, 403);
    assert.equal((await review(video.id, { ...decision, decision: 'REJECTED' })).status, 400);
    assert.equal((await review(video.id, decision)).status, 200);
    assert.equal((await review(video.id, decision)).status, 200);
    assert.equal(await MomentReviewAction.count(), 1);
    assert.equal((await feed('video'))[0].id, video.id);
    assert.equal((await call(video.media_path)).status, 200);
    assert.equal((await review(video.id, { ...decision, request_id: randomUUID() })).status, 409);
    assert.equal((await review(video.id, { ...decision, expected_version: 2 })).status, 409);
    const reject = { decision: 'REJECTED', expected_version: 2, request_id: randomUUID(), reason: '请更换视频内容' };
    assert.equal((await review(video.id, reject)).status, 200);
    assert.equal((await feed('video')).length, 0);
    assert.equal((await call(video.media_path)).status, 404);
    assert.equal((await feed('mine_video', 1))[0].review_reason, '请更换视频内容');
    const race = await Promise.all([
      review(video.id, { ...decision, expected_version: 3, request_id: randomUUID() }),
      review(video.id, { ...reject, expected_version: 3, request_id: randomUUID() }),
    ]);
    assert.deepEqual(race.map(row => row.status).sort(), [200, 409]);
    const history = (await call(`/operations/moments/${video.id}/reviews`, 'GET', null, 9)).json.data.actions;
    assert.equal(history.length, 3); assert.ok(history.every(row => row.operator_id === 9));
    assert.equal((await Review.findByPk(video.id)).version, 4);
    await MomentMedia.destroy({ where: { moment_id: image.id } });
    assert.equal((await call(`/operations/moments/${image.id}/media`, 'GET', null, 9)).status, 404);
    await call(`/moments/${text.id}`, 'DELETE', null, 1);
    assert.equal((await review(text.id, { ...decision, request_id: randomUUID() })).status, 409);
  } finally {
    if (server) await new Promise(resolve => server.close(resolve)); if (db) await db.close();
    await admin.query(`DROP DATABASE IF EXISTS \`${name}\``); await admin.end();
  }
});
