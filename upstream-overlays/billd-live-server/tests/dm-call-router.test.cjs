// Router contract for 1v1 calls. Service boundaries are stubbed; token signing is real.
const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const jwt = require('jsonwebtoken');
const Koa = require('koa');

require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));

function stub(file, exports) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename: id, loaded: true, exports };
}

class CustomError extends Error {
  constructor(message, statusCode, errorCode) {
    super(message);
    this.statusCode = statusCode;
    this.errorCode = errorCode;
  }
}

const peers = {
  2: { id: 2, status: 1 },
  3: { id: 3, status: 2 },
};

stub('@/model/customError.model', { CustomError });
stub('@/constant', { ALLOW_HTTP_CODE: { ok: 200, paramsError: 400, forbidden: 403 } });
stub('@/config/secret', { LIVEKIT_API_KEY: 'fixture-key', LIVEKIT_API_SECRET: 'fixture-secret', REVENUE_API_URL: 'http://fixture', INTERNAL_SERVICE_KEY: 'fixture-internal' });
stub('@/app/auth/authJwt', { authJwt: async (ctx) => ({ code: 200, userInfo: { id: Number(ctx.headers.authorization.slice(7)) } }) });
stub('@/app/handler/success-handle', { __esModule: true, default: ({ ctx, data }) => { ctx.body = data; } });
stub('@/service/media-authority', { mediaAuthority: async () => { throw new Error('live authority must not run for a dm call'); }, mediaSessionHash: (token) => `hash-${token}` });
stub('@/utils/request', { __esModule: true, default: {
  get: async (url) => {
    if (url.endsWith('/v1/dm/call-prices/2')) return { price_coins: 0 };
    throw new Error(`unexpected GET ${url}`);
  },
  post: async (url) => {
    if (url.endsWith('/v1/internal/users')) return {};
    throw new Error(`unexpected POST ${url}`);
  },
} });
stub('@/model/user.model', { __esModule: true, default: { findByPk: async (id) => peers[String(id)] || null } });
const callHistory = new Map();
stub('@/model/dmCallHistory.model', { __esModule: true, default: {
  upsert: async row => { callHistory.set(row.call_id, row); }, findByPk: async id => callHistory.get(id),
} });

test('authenticated pair receives publish tokens; strangers, self calls and live token route stay separated', async () => {
  const { resetDmCalls } = require('../dist/service/dm-call');
  resetDmCalls();
  const loaded = require('../dist/router/livekit.router');
  const router = loaded.default || loaded;
  const app = new Koa();
  app.use(async (ctx, next) => {
    try { await next(); } catch (error) {
      ctx.status = error.statusCode || 500;
      ctx.body = { error: error.message };
    }
  });
  app.use(require('koa-bodyparser')());
  app.use(router.routes());
  const server = app.listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  const call = async (user, path, body, expected = 200) => {
    const response = await fetch(`http://127.0.0.1:${server.address().port}${path}`, {
      method: 'POST',
      headers: { authorization: `Bearer ${user}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await response.json();
    assert.equal(response.status, expected, JSON.stringify(data));
    return data;
  };
  try {
    process.env.LIVEKIT_URL = 'ws://192.168.254.132:17880';
    const created = await call(1, '/livekit/calls', { peer_id: 2, mode: 'video', accepted_price_coins: 0 });
    assert.equal(created.url, 'ws://192.168.254.132:17880');
    assert.equal(created.room.startsWith('dm-'), true);
    const caller = jwt.verify(created.token, 'fixture-secret', { issuer: 'fixture-key', algorithms: ['HS256'] });
    assert.equal(caller.sub, '1');
    assert.equal(caller.video.canPublish, true);
    assert.deepEqual(caller.video.canPublishSources, ['microphone', 'camera']);
    const joined = await call(2, '/livekit/calls/join', { call_id: created.call_id });
    const callee = jwt.verify(joined.token, 'fixture-secret', { issuer: 'fixture-key', algorithms: ['HS256'] });
    assert.equal(callee.sub, '2');
    assert.equal(callee.video.canPublish, true);
    assert.equal(callee.video.room, created.room);
    await call(9, '/livekit/calls/join', { call_id: created.call_id }, 403);
    await call(1, '/livekit/calls', { peer_id: 1, mode: 'voice', accepted_price_coins: 0 }, 400);
    await call(1, '/livekit/calls', { peer_id: 99, mode: 'voice', accepted_price_coins: 0 }, 404);
    await call(1, '/livekit/calls', { peer_id: 3, mode: 'voice', accepted_price_coins: 0 }, 403);
    await call(1, '/livekit/calls', { peer_id: 'nope', mode: 'video' }, 400);
    const ended = await call(1, '/livekit/calls/end', { call_id: created.call_id });
    assert.equal(ended.status, 'ended');
    assert.equal(ended.token, undefined);
    const read = async (user,expected=200)=>{
      const response=await fetch(`http://127.0.0.1:${server.address().port}/livekit/calls/${created.call_id}`,{headers:{authorization:`Bearer ${user}`}});
      assert.equal(response.status,expected);return response.json();
    };
    assert.equal((await read(1)).status,'cancelled');
    await read(9,403);
    await call(2, '/livekit/calls/join', { call_id: created.call_id }, 404);
    const live = await call(1, '/livekit/token', {}, 400);
    assert.equal(live.token, undefined);
    resetDmCalls();
    assert.equal((await read(2)).status,'cancelled');
    await read(9,403);
  } finally {
    delete process.env.LIVEKIT_URL;
    await new Promise((resolve) => server.close(resolve));
  }
});
