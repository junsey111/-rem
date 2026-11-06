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

const users = [
  { id: 1, username: 'A', avatar: null, desc: null, status: 1 },
  { id: 2, username: 'B', avatar: null, desc: null, status: 1 },
];
const relations = [];
let blockedPair = false;
stub('@/app/auth/authJwt', { authJwt: async ctx => ({ code: 200, userInfo: { id: Number(ctx.get('authorization').slice(7)) } }) });
stub('@/app/request-rate', { RequestRateLimiter: class { consume() { return false; } } });
stub('@/model/customError.model', { CustomError });
stub('@/app/handler/success-handle', { __esModule: true, default: ({ ctx, data }) => { ctx.status = 200; ctx.body = { data }; } });
stub('@/model/closeFriendRelation.model', { __esModule: true, default: {
  findAll: async () => [...relations],
  findOrCreate: async ({ where, defaults }) => {
    const row = relations.find(item => item.user_low_id === where.user_low_id && item.user_high_id === where.user_high_id);
    if (row) return [row, false];
    const created = { id: relations.length + 1, ...defaults, created_at: 'now', updated_at: 'now' };
    relations.push(created);
    return [created, true];
  },
  update: async (values, { where }) => {
    const row = relations.find(item => item.user_low_id === where.user_low_id && item.user_high_id === where.user_high_id && item.requested_by === where.requested_by && item.status === where.status);
    if (row) Object.assign(row, values);
    return [row ? 1 : 0];
  },
  destroy: async ({ where }) => {
    const index = relations.findIndex(item => item.user_low_id === where.user_low_id && item.user_high_id === where.user_high_id);
    if (index < 0) return 0;
    relations.splice(index, 1);
    return 1;
  },
} });
stub('@/model/user.model', { __esModule: true, default: {
  findOne: async ({ where }) => users.find(user => user.id === where.id && user.status === where.status) || null,
  findAll: async ({ where }) => users.filter(user => where.id[Object.getOwnPropertySymbols(where.id)[0]].includes(user.id)),
} });
stub('@/model/userBlock.model', { __esModule: true, default: { findAll: async () => [], findOne: async () => blockedPair ? { id: 1 } : null } });

test('close friend invitation requires acceptance and either side can remove the relation', async () => {
  relations.splice(0);
  blockedPair = false;
  const router = require('../dist/router/close-friends.router').default;
  const app = new Koa();
  app.use(async (ctx, next) => {
    try { await next(); } catch (error) { ctx.status = error.statusCode || 500; ctx.body = { error: error.message }; }
  });
  app.use(require('koa-bodyparser')());
  app.use(router.routes());
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  const call = async (userId, url, method = 'GET', body) => {
    const response = await fetch(`http://127.0.0.1:${server.address().port}${url}`, {
      method, headers: { authorization: `Bearer ${userId}`, ...(body ? { 'content-type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    return { status: response.status, json: await response.json() };
  };
  try {
    assert.equal((await call(1, '/close-friends')).json.data.friends.length, 0);
    assert.equal((await call(1, '/close-friends', 'POST', { user_id: 1 })).status, 400);
    assert.equal((await call(1, '/close-friends', 'POST', { user_id: 99 })).status, 404);
    assert.equal((await call(1, '/close-friends', 'POST', { user_id: 2 })).status, 200);
    assert.equal((await call(1, '/close-friends', 'POST', { user_id: 2 })).status, 200);
    assert.equal((await call(2, '/close-friends')).json.data.incoming[0].user.id, 1);
    assert.equal((await call(1, '/close-friends')).json.data.outgoing[0].user.id, 2);
    assert.equal((await call(1, '/close-friends/1/accept', 'POST', {})).status, 404);
    blockedPair = true;
    assert.equal((await call(2, '/close-friends/1/accept', 'POST', {})).status, 409);
    blockedPair = false;
    assert.equal((await call(2, '/close-friends/1/accept', 'POST', {})).status, 200);
    assert.equal((await call(1, '/close-friends')).json.data.friends[0].user.id, 2);
    assert.equal((await call(2, '/close-friends/1', 'DELETE')).status, 200);
    assert.equal((await call(1, '/close-friends')).json.data.friends.length, 0);
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});
