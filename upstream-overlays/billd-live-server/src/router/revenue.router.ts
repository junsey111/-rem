import crypto from 'crypto';
import Router from 'koa-router';

import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';
import { ALLOW_HTTP_CODE } from '@/constant';
import { CustomError } from '@/model/customError.model';
import axios from '@/utils/request';

const revenueRouter = new Router({ prefix: '/revenue' });

async function requireUser(ctx) {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== ALLOW_HTTP_CODE.ok || !userInfo?.id) {
    throw new CustomError(message || '未登录！', code, code);
  }
  return userInfo;
}

async function postRevenue(path: string, data: any, token?: string, internal = false) {
  return axios.post(`${REVENUE_API_URL}${path}`, data, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(internal ? { 'X-Internal-Key': INTERNAL_SERVICE_KEY } : {}),
    },
    timeout: 10000,
  });
}

async function syncRevenueUser(userInfo: any) {
  return postRevenue('/v1/internal/users', {
    user_id: Number(userInfo.id),
    age_years: 18,
    region_code: 'US',
    can_go_live: true,
    live_gifts_enabled: true,
    account_type: 'NORMAL',
  }, undefined, true);
}

revenueRouter.get('/gifts', async (ctx, next) => {
  const result = await axios.get(`${REVENUE_API_URL}/v1/gifts/catalog`);
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/wallet', async (ctx, next) => {
  const user = await requireUser(ctx);
  const token = ctx.headers.authorization?.split(' ')[1];
  await syncRevenueUser(user);
  const result = await axios.get(`${REVENUE_API_URL}/v1/wallets/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/earnings', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/creators/me/earnings`, {
    params: ctx.query,
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/rankings/creators', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/rankings/creators`, {
    params: ctx.query,
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/rankings/contributors', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/rankings/contributors`, {
    params: ctx.query,
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.post('/gifts/send', async (ctx, next) => {
  const user = await requireUser(ctx);
  const token = ctx.headers.authorization?.split(' ')[1];
  await syncRevenueUser(user);
  const body = ctx.request.body || {};
  const result = await postRevenue('/v1/gifts', {
    request_id: crypto.randomUUID(),
    viewer_id: Number(user.id),
    creator_id: Number(body.creator_id),
    live_session_id: Number(body.live_session_id),
    match_id: body.match_id ? Number(body.match_id) : null,
    gift_id: String(body.gift_id || ''),
    quantity: Number(body.quantity || 1),
    sent_at_ms: Date.now(),
  }, token);
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.post('/lives/start', async (ctx, next) => {
  const user = await requireUser(ctx);
  const liveSessionId = Date.now() * 1000 + crypto.randomInt(1000);
  await syncRevenueUser(user);
  const result = await postRevenue('/v1/internal/lives', {
    live_session_id: liveSessionId,
    creator_id: Number(user.id),
    campaign_id: ctx.request.body?.campaign_id ? Number(ctx.request.body.campaign_id) : null,
    match_id: ctx.request.body?.match_id ? Number(ctx.request.body.match_id) : null,
    mode: ctx.request.body?.mode || 'ALL_GIFTS',
    specific_gift_id: ctx.request.body?.specific_gift_id || null,
  }, undefined, true);
  successHandler({ ctx, data: { ...result, live_session_id: liveSessionId } });
  await next();
});

revenueRouter.get('/lives/:id/contributors', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/lives/${encodeURIComponent(ctx.params.id)}/contributors`, {
    params: ctx.query,
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/lives/:id', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/lives/${encodeURIComponent(ctx.params.id)}`, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/campaigns/:id/leaderboard', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/campaigns/${encodeURIComponent(ctx.params.id)}/leaderboard`, {
    params: ctx.query,
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.post('/lives/:id/close', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.post(`${REVENUE_API_URL}/v1/lives/${encodeURIComponent(ctx.params.id)}/close`, {}, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/matches/:id/score', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/matches/${encodeURIComponent(ctx.params.id)}/score`, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/lives/:id/settlement', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/settlements/${encodeURIComponent(ctx.params.id)}`, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

export default revenueRouter;
