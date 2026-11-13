const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const mysql = require('mysql2/promise');
const { Sequelize } = require('sequelize');
const Koa = require('koa');
require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, exports) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename: id, loaded: true, exports };
}

test('real MySQL denies pending/rejected/hidden/blocked moment interactions and publishes into review', {
  skip: !process.env.MYSQL_TEST_URL, timeout: 30000,
}, async () => {
  const url = new URL(process.env.MYSQL_TEST_URL), name = 'live_test_visibility_' + Date.now();
  const admin = await mysql.createConnection({ host: url.hostname, port: Number(url.port || 3306),
    user: decodeURIComponent(url.username), password: decodeURIComponent(url.password) });
  let db, server;
  try {
    await admin.query(`CREATE DATABASE \`${name}\``);
    db = new Sequelize(name, decodeURIComponent(url.username), decodeURIComponent(url.password), {
      host: url.hostname, port: Number(url.port || 3306), dialect: 'mysql', logging: false });
    stub('@/config/mysql', { __esModule: true, default: db });
    stub('@/utils', { initTable() {} });
    // Authentication is isolated here; the query and HTTP mutation paths use real models/MySQL.
    stub('@/app/auth/authJwt', { authJwt: async ctx => {
      const id = Number(ctx.get('authorization').slice(7));
      return id ? { code: 200, userInfo: { id } } : { code: 401, message: 'Login required' };
    } });
    // HTTP moderator identity fixture; real account/role isolation is covered by smoke-admin-local.cjs.
    stub('@/app/auth/operations', { requireOperations: async ctx => {
      if (ctx.get('authorization') !== 'Bearer 3') throw Object.assign(new Error('Forbidden'), { statusCode: 403 });
      return { id: 3 };
    }, canManageOperations: async id => id === 3 });
    const { Moment, MomentComment, MomentLike } = require('../dist/model/moment.model');
    const User = require('../dist/model/user.model').default;
    const Block = require('../dist/model/userBlock.model').default;
    const router = require('../dist/router/moments.router').default;
    const operations = require('../dist/router/operations.router').default;
    await db.sync();
    await User.bulkCreate([{ id: 1, username: 'Reader', password: 'test-password' },
      { id: 2, username: 'Author', password: 'test-password' },
      { id: 3, username: 'Moderator', password: 'test-password' }]);
    await Moment.bulkCreate([
      { id: 1, user_id: 2, content: 'Approved', review_status: 'APPROVED' },
      { id: 2, user_id: 2, content: 'Pending', review_status: 'PENDING' },
      { id: 3, user_id: 2, content: 'Rejected', review_status: 'REJECTED' },
      { id: 4, user_id: 2, content: 'Hidden', review_status: 'APPROVED', visible: false },
    ]);
    const app = new Koa();
    app.use(async (ctx, next) => { try { await next(); } catch (error) {
      ctx.status = error.statusCode || 500; ctx.body = { message: error.message };
    } });
    app.use(require('koa-bodyparser')()); app.use(router.routes()); app.use(operations.routes());
    server = app.listen(0, '127.0.0.1');
    await new Promise(resolve => server.once('listening', resolve));
    const call = async (id, suffix, method = 'GET', body, viewer = 1) => {
      const response = await fetch(`http://127.0.0.1:${server.address().port}/moments${id ? '/' + id : ''}${suffix}`, {
        method, headers: { authorization: `Bearer ${viewer}`, 'content-type': 'application/json' },
        ...(body ? { body: JSON.stringify(body) } : {}),
      });
      return { status: response.status, body: await response.json() };
    };
    for (const id of [2, 3, 4]) {
      assert.equal((await call(id, '/comments')).status, 404);
      assert.equal((await call(id, '/comments', 'POST', { content: 'Not allowed' })).status, 404);
      assert.equal((await call(id, '/like', 'PUT')).status, 404);
    }
    assert.equal(await MomentComment.count(), 0);
    assert.equal(await MomentLike.count(), 0);
    assert.equal((await call(2, '/comments', 'GET', undefined, 2)).status, 200);
    assert.equal((await call(3, '/comments', 'GET', undefined, 2)).status, 404);
    assert.equal((await call(1, '/like', 'PUT', undefined, 0)).status, 401);
    assert.equal((await call(1, '/like', 'PUT')).status, 200);
    assert.equal((await call(1, '/comments', 'POST', { content: 'Allowed' })).status, 200);
    assert.equal(await MomentComment.count(), 1);
    const pendingComment = await MomentComment.findOne();
    assert.equal(pendingComment.review_status, 'PENDING');
    assert.equal((await call(1, '/comments')).body.data.comments[0].review_status, 'PENDING');
    assert.equal((await call(1, '/comments', 'GET', undefined, 2)).body.data.comments.length, 0);
    const moderate = async (route, method = 'GET', body, viewer = 3) => {
      const response = await fetch(`http://127.0.0.1:${server.address().port}/operations/comments${route}`, {
        method, headers: { authorization: `Bearer ${viewer}`, 'content-type': 'application/json' },
        ...(body ? { body: JSON.stringify(body) } : {}),
      });
      return { status: response.status, body: await response.json() };
    };
    assert.equal((await moderate('', 'GET', undefined, 1)).status, 403);
    assert.equal((await moderate('')).body.data.comments[0].id, String(pendingComment.id));
    assert.equal((await moderate('?before_id=invalid')).status, 400);
    assert.equal((await moderate('?status=invalid')).status, 400);
    assert.equal((await moderate('/' + pendingComment.id, 'POST', { action: 'REJECTED' })).status, 400);
    assert.equal((await moderate('/' + pendingComment.id, 'POST', { action: 'APPROVED' })).status, 200);
    assert.equal((await call(1, '/comments', 'GET', undefined, 2)).body.data.comments.length, 1);
    assert.equal((await moderate('/' + pendingComment.id, 'POST', { action: 'HIDE', reason: 'Review test' })).status, 200);
    assert.equal((await call(1, '/comments', 'GET', undefined, 2)).body.data.comments.length, 0);
    const rejectedComment = (await call(1, '/comments')).body.data.comments[0];
    assert.equal(rejectedComment.review_status, 'REJECTED');
    assert.equal(rejectedComment.moderation_reason, 'Review test');
    assert.equal((await moderate('/' + pendingComment.id, 'POST', { action: 'RESTORE' })).status, 200);
    await pendingComment.reload();
    assert.equal(Number(pendingComment.reviewed_by), 3);
    const deletedComment = await MomentComment.create({ moment_id: 1, user_id: 1, content: 'To delete', review_status: 'PENDING' });
    assert.equal((await call(1, '/comments/' + deletedComment.id, 'DELETE')).status, 200);
    assert.equal((await moderate('/' + deletedComment.id, 'POST', { action: 'RESTORE' })).status, 409);
    assert.equal((await moderate('?status=ALL')).body.data.comments.some(row => row.id === String(deletedComment.id)), false);
    assert.equal((await moderate('?status=ALL&before_id=' + pendingComment.id)).body.data.comments.length, 0);
    const feed = await call(null, '');
    assert.equal(feed.body.data.moments.find(row => row.id === '1').comment_count, 1);
    await deletedComment.destroy();
    assert.equal(await MomentLike.count(), 1);
    await Block.create({ blocker_id: 2, blocked_id: 1, created_at: new Date() });
    assert.equal((await call(1, '/comments')).status, 404);
    assert.equal((await call(1, '/comments', 'POST', { content: 'Blocked' })).status, 404);
    assert.equal((await call(1, '/like', 'PUT')).status, 404);
    assert.equal(await MomentComment.count(), 1);
    const published = await call(null, '', 'POST', { content: 'New text' });
    assert.equal(published.status, 200);
    assert.equal(published.body.data.review_status, 'PENDING');
    assert.equal((await Moment.findByPk(published.body.data.id)).review_status, 'PENDING');
    const Follow = require('../dist/model/followRelation.model').default;
    const SocialProfile = require('../dist/model/userSocialProfile.model').default;
    const TaskConfig = require('../dist/model/taskConfig.model').default;
    const taskKeys = ['publish_moment', 'like_moment', 'comment_moment', 'follow_user', 'first_follow', 'set_city'];
    await TaskConfig.bulkCreate(taskKeys.map(task_key => ({ task_key, category: 'social', title: task_key,
      target_count: 1, reward_coins: 1, enabled: true })));
    await Follow.create({ follower_id: 1, followed_id: 2 });
    await SocialProfile.create({ user_id: 1, city: 'Beijing' });
    const progress = await call(null, '/tasks/today');
    assert.equal(progress.status, 200);
    assert.equal(progress.body.data.published, 1);
    assert.equal(progress.body.data.liked, 1);
    assert.equal(progress.body.data.commented, 1);
    assert.equal(progress.body.data.tasks.length, taskKeys.length);
    assert.ok(progress.body.data.tasks.every(task => task.progress === 1));
  } finally {
    if (server) await new Promise(resolve => { server.close(resolve); server.closeAllConnections?.(); });
    if (db) await db.close();
    await admin.query(`DROP DATABASE IF EXISTS \`${name}\``); await admin.end();
  }
});
