const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const Koa = require('koa');
require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, value) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename: id, loaded: true, exports: value };
}
let admin = false,
  lastCall,
  failure;
stub('@/app/auth/operations', {
  requireOperations: async () => {
    if (!admin) {
      const e = Error('Forbidden');
      e.statusCode = 403;
      throw e;
    }
    return { id: 7 };
  },
});
stub('@/config/secret', {
  INTERNAL_SERVICE_KEY: 'internal-test',
  REVENUE_API_URL: 'http://ledger',
});
stub('@/utils/request', {
  __esModule: true,
  default: {
    request: async (options) => {
      lastCall = options;
      if (failure) throw failure;
      return { version: 1 };
    },
  },
});
test('business gateway authenticates operators, rejects impersonation, and preserves version conflicts', async () => {
  const app = new Koa();
  app.use(async (ctx, next) => {
    try {
      await next();
    } catch (e) {
      ctx.status = e.statusCode || 500;
      ctx.body = { message: e.message };
    }
  });
  app.use(require('koa-bodyparser')());
  app.use(require('../dist/router/business-settings.router').default.routes());
  const server = app.listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  const call = async (route, method = 'GET', body) => {
    const res = await fetch(
      `http://127.0.0.1:${server.address().port}${route}`,
      {
        method,
        headers: { 'Content-Type': 'application/json' },
        ...(body ? { body: JSON.stringify(body) } : {}),
      }
    );
    return { status: res.status, body: await res.json() };
  };
  try {
    for (const route of [
      '/operations/referrals/settings',
      '/operations/lives/11/watch-price',
      '/operations/business-actions',
    ])
      assert.equal((await call(route)).status, 403);
    assert.equal(lastCall, undefined);
    admin = true;
    assert.equal(
      (await call('/operations/referrals/settings', 'PUT', { operator_id: 99 }))
        .status,
      400
    );
    assert.equal(lastCall, undefined);
    assert.equal(
      (
        await call('/operations/referrals/settings', 'PUT', {
          request_id: 'same-id',
          expected_version: 0,
        })
      ).status,
      200
    );
    assert.equal(lastCall.data.operator_id, 7);
    assert.equal(lastCall.data.request_id, 'same-id');
    assert.equal(lastCall.headers['X-Internal-Key'], 'internal-test');
    assert.equal((await call('/operations/lives/011/watch-price')).status, 400);
    assert.equal(
      (await call('/operations/business-actions?before_id=garbage')).status,
      400
    );
    failure = {
      isAxiosError: true,
      response: {
        status: 409,
        data: { message: '设置已被修改，请刷新后重新确认' },
      },
    };
    const conflict = await call('/operations/lives/11/watch-price', 'PUT', {
      expected_version: 0,
    });
    assert.equal(conflict.status, 409);
    assert.match(conflict.body.message, /刷新/);
    failure = { isAxiosError: true };
    assert.equal((await call('/operations/referrals/settings')).status, 503);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
