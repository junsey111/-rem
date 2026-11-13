const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const Koa = require('koa');

require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, exports) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename: id, loaded: true, exports };
}

class CustomError extends Error {
  constructor(message, statusCode) { super(message); this.statusCode = statusCode; }
}

const rows = [{ id: 5, user_id: 2, content: '真实动态', created_at: new Date('2026-11-05T10:00:00Z') }];
const comments = [{ id: 50, moment_id: 5, user_id: 2, content: '真实评论', created_at: new Date('2026-11-05T10:01:00Z') }];
const likes = new Set();
const checkinDates = [];
let feedWhere;
const taskWhere = [];
stub('@/app/auth/authJwt', { authJwt: async ctx => ({ code: 200, userInfo: { id: Number(ctx.get('authorization').slice(7)) } }) });
stub('@/model/customError.model', { CustomError });
stub('@/app/handler/success-handle', { __esModule: true, default: ({ ctx, data }) => { ctx.status = 200; ctx.body = { data }; } });
stub('@/config/mysql', { __esModule: true, default: { transaction: async callback => callback({}) } });
stub('@/model/user.model', { __esModule: true, default: { findAll: async () => [{ id: 2, username: '作者', avatar: null }] } });
stub('@/model/followRelation.model', { __esModule: true, default: {} });
stub('@/model/userSocialProfile.model', { __esModule: true, default: { findOne: async () => null } });
stub('@/model/dailyCheckin.model', { __esModule: true, default: {
  findAll: async () => [...checkinDates].sort().reverse().map(checkin_date => ({ checkin_date })),
  count: async () => 0,
  findOrCreate: async ({ where }) => {
    const created = !checkinDates.includes(where.checkin_date);
    if (created) checkinDates.push(where.checkin_date);
    return [{ ...where }, created];
  },
} });
stub('@/model/taskConfig.model', { __esModule: true, default: {
  findOne: async () => null,
  findAll: async () => [],
} });
stub('@/model/taskClaim.model', { __esModule: true, default: {
  findAll: async () => [],
  findOne: async () => null,
  findOrCreate: async ({ defaults }) => [{ get: () => defaults }, true],
} });
stub('@/model/reportEvidence.model', { __esModule: true, default: {} });
stub('@/model/moment.model', { Moment: {
  findAll: async ({ where }) => { feedWhere = where; return rows; },
  count: async ({ where }) => { taskWhere.push(where); return 2; },
  create: async value => ({ id: 6, user_id: 1, content: value.content, created_at: new Date() }),
  update: async () => [1],
  findOne: async () => ({ id: 5 }),
}, MomentComment: {
  count: async ({ where }) => { taskWhere.push(where); return 4; },
  findAll: async options => options.group ? [{ moment_id: 5, count: String(comments.filter(row => row.visible !== false).length) }] : comments.filter(row => row.visible !== false),
  create: async value => {
    const row = { id: 51, ...value, created_at: new Date() };
    comments.push(row);
    return row;
  },
  update: async (_values, { where }) => [comments.some(row => row.id === Number(where.id) && row.user_id === where.user_id && row.visible !== false) ? 1 : 0],
}, MomentLike: {
  count: async ({ where }) => { taskWhere.push(where); return 3; },
  findAll: async options => options.group ? [{ moment_id: 5, count: '2' }] : [...likes].map(moment_id => ({ moment_id })),
  findOrCreate: async ({ where }) => { likes.add(String(where.moment_id)); return [null, true]; },
  destroy: async ({ where }) => likes.delete(String(where.moment_id)),
}, MomentMedia: {
  findAll: async () => [],
} });

test('authenticated moments support feed, text publishing, comments, likes, and owned deletion', async () => {
  const router = require('../dist/router/moments.router').default;
  const app = new Koa();
  app.use(async (ctx, next) => {
    try { await next(); } catch (error) { ctx.status = error.statusCode || 500; ctx.body = { error: error.message }; }
  });
  app.use(require('koa-bodyparser')());
  app.use(router.routes());
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  const call = async (url, method = 'GET', body) => {
    const response = await fetch(`http://127.0.0.1:${server.address().port}${url}`, {
      method, headers: { authorization: 'Bearer 1', ...(body ? { 'content-type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    return { status: response.status, json: await response.json() };
  };
  try {
    const feed = await call('/moments?scope=all');
    assert.equal(feed.status, 200);
    assert.equal(feed.json.data.moments[0].author.username, '作者');
    assert.equal(feed.json.data.moments[0].like_count, 2);
    assert.equal(feed.json.data.moments[0].comment_count, 1);
    const following = await call('/moments?scope=following');
    assert.equal(following.status, 200);
    const userFeed = await call('/moments?scope=user&user_id=2');
    assert.equal(userFeed.status, 200);
    assert.equal(userFeed.json.data.moments[0].user_id, 2);
    const [userFeedAndKey] = Object.getOwnPropertySymbols(feedWhere);
    assert.equal(feedWhere[userFeedAndKey][2].user_id, 2);
    const blockedCondition = feedWhere[userFeedAndKey][1].user_id;
    const [notInKey] = Object.getOwnPropertySymbols(blockedCondition);
    assert.match(blockedCondition[notInKey].val, /user_block_relation/);
    assert.equal((await call('/moments?scope=user')).status, 400);
    assert.equal((await call('/moments?scope=user&user_id=0')).status, 400);
    assert.equal((await call('/moments?scope=all&user_id=2')).status, 400);
    await call('/moments?scope=following');
    const [andKey] = Object.getOwnPropertySymbols(feedWhere);
    assert.equal(feedWhere[andKey].length, 4);
    const blockedUsers = feedWhere[andKey][1].user_id[Object.getOwnPropertySymbols(feedWhere[andKey][1].user_id)[0]];
    const followedUsers = feedWhere[andKey][2].user_id[Object.getOwnPropertySymbols(feedWhere[andKey][2].user_id)[0]];
    assert.match(blockedUsers.val, /user_block_relation/);
    assert.match(followedUsers.val, /user_follow_relation/);
    const dailyProgress = await call('/moments/tasks/today');
    assert.equal(dailyProgress.status, 200);
    assert.deepEqual(dailyProgress.json.data, { published: 2, liked: 3, commented: 4, tasks: [] });
    assert.equal(taskWhere.length, 3);
    assert.ok(taskWhere.every(where => where.user_id === 1 && where.created_at));
    assert.equal(taskWhere[0].visible, true);
    const beforeCheckin = await call('/moments/tasks/checkin');
    assert.equal(beforeCheckin.json.data.checked_in, false);
    assert.equal(beforeCheckin.json.data.streak_days, 0);
    const checkin = await call('/moments/tasks/checkin', 'POST');
    assert.equal(checkin.json.data.checked_in, true);
    assert.equal(checkin.json.data.streak_days, 1);
    assert.equal(checkin.json.data.reward_configured, false);
    await call('/moments/tasks/checkin', 'POST');
    assert.equal(checkinDates.length, 1);
    const listedComments = await call('/moments/5/comments');
    assert.equal(listedComments.status, 200);
    assert.equal(listedComments.json.data.comments[0].content, '真实评论');
    assert.equal(listedComments.json.data.comments[0].is_mine, false);
    assert.equal((await call('/moments/5/comments?before_id=bad')).status, 400);
    const createdComment = await call('/moments/5/comments', 'POST', { content: '  我的评论  ' });
    assert.equal(createdComment.status, 200);
    assert.equal(createdComment.json.data.comment.content, '我的评论');
    assert.equal((await call('/moments/5/comments', 'POST', { content: ' ' })).status, 400);
    assert.equal((await call('/moments/5/comments/50', 'DELETE')).status, 404);
    assert.equal((await call('/moments/5/comments/51', 'DELETE')).status, 200);
    assert.equal((await call('/moments', 'POST', { content: '  新动态  ' })).json.data.content, '新动态');
    assert.equal((await call('/moments', 'POST', { content: ' ' })).status, 400);
    assert.equal((await call('/moments/5/like', 'PUT')).status, 200);
    assert.equal(likes.has('5'), true);
    assert.equal((await call('/moments/5/like', 'DELETE')).status, 200);
    assert.equal(likes.has('5'), false);
    assert.equal((await call('/moments/6', 'DELETE')).status, 200);
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});
