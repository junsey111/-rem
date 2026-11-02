const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const Koa = require('koa');
require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, exports) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename: id, loaded: true, exports };
}
const calls = [];
let failure;
stub('@/config/secret', { REVENUE_API_URL: 'http://ledger', INTERNAL_SERVICE_KEY: 'fixture' });
stub('@/app/auth/authJwt', { authJwt: async () => ({ code: 200, userInfo: { id: 7 } }) });
stub('@/app/handler/success-handle', { __esModule: true, default: ({ ctx, data }) => { ctx.body = data; } });
stub('@/utils/request', {
  __esModule: true,
  default: {
    get: async (url, options) => {
      calls.push({ method: 'GET', url, options });
      if (failure) throw failure;
      if (String(url).endsWith('/v1/gifts/catalog')) {
        return { gifts: [{ gift_id: 'ROSE', coin_price: 1, effect_type: 'yyeva', effect_url: 'https://cdn.example.com/rose.mp4', effect_video_mode: 'alpha', effect_md5: 'abc', icon_url: '' }] };
      }
      return { live_id: '501', status: 'PREVIEW', free_preview_seconds: 60 };
    },
    post: async (url, data, options) => { calls.push({ method: 'POST', url, data, options }); if (failure) throw failure; return { live_id: '501', status: 'UNLOCKED', unlocked: true }; },
    put: async (url, data, options) => { calls.push({ method: 'PUT', url, data, options }); return {}; },
  },
});
test('paid watch routes keep the Android field contract and forward the Billd JWT', async () => {
  const app = new Koa();
  app.use(async (ctx, next) => { try { await next(); } catch (e) { ctx.status = e.statusCode || 500; ctx.body = { message: e.message, errorCode: e.errorCode }; } });
  app.use(require('koa-bodyparser')());
  app.use(require('../dist/router/revenue.router').default.routes());
  const server = app.listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  const call = async (method, path, body) => {
    const response = await fetch(`http://127.0.0.1:${server.address().port}${path}`, {
      method,
      headers: { authorization: 'Bearer fixture', 'Content-Type': 'application/json' },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    return { status: response.status, body: await response.json() };
  };
  try {
    const access = await call('GET', '/revenue/lives/501/watch-access');
    assert.equal(access.status, 200);
    assert.equal(access.body.live_id, '501');
    assert.equal(access.body.free_preview_seconds, 60);
    assert.equal(access.body.status, 'PREVIEW');
    assert.equal(calls.at(-1).url, 'http://ledger/v1/lives/501/watch-access');
    assert.equal(calls.at(-1).options.headers.Authorization, 'Bearer fixture');

    assert.equal((await call('POST', '/revenue/lives/501/watch-heartbeat', { request_id: 'nope', delta_seconds: 5 })).status, 400);
    const beat = await call('POST', '/revenue/lives/501/watch-heartbeat', { request_id: '11111111-1111-4111-8111-111111111111', delta_seconds: 30 });
    assert.equal(beat.status, 200);
    assert.deepEqual(calls.at(-1).data, { request_id: '11111111-1111-4111-8111-111111111111', delta_seconds: 30 });

    failure = { isAxiosError: true, response: { status: 402, data: { code: 'INSUFFICIENT_BALANCE', message: '余额不足，无法解锁付费观看，本次未扣币' } } };
    const refused = await call('POST', '/revenue/lives/501/watch-unlock', { request_id: '44444444-4444-4444-8444-444444444444' });
    assert.equal(refused.status, 402);
    assert.match(refused.body.message, /未扣币/);
    failure = undefined;
    const unlocked = await call('POST', '/revenue/lives/501/watch-unlock', { request_id: '44444444-4444-4444-8444-444444444444' });
    assert.equal(unlocked.status, 200);
    assert.equal(unlocked.body.unlocked, true);
    assert.equal(unlocked.body.status, 'UNLOCKED');
    const gifts = await call('GET', '/revenue/gifts');
    assert.equal(gifts.status, 200);
    assert.equal(gifts.body.gifts[0].effect_type, 'yyeva');
    assert.equal(gifts.body.gifts[0].effect_url, 'https://cdn.example.com/rose.mp4');
    assert.equal(gifts.body.gifts[0].effect_video_mode, 'alpha');
    assert.equal(gifts.body.gifts[0].effect_md5, 'abc');
    assert.equal(gifts.body.gifts[0].coin_price, 1);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
