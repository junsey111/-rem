import axiosClient from 'axios';
import crypto from 'crypto';
import Router from 'koa-router';

import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';
import { ALLOW_HTTP_CODE } from '@/constant';
import { CustomError } from '@/model/customError.model';
import { publishAcceptedGift } from '@/service/gift-broadcast';
import axios from '@/utils/request';

const revenueRouter = new Router({ prefix: '/revenue' });

// Preserve actionable business refusals instead of presenting them as server errors.
revenueRouter.use(async (_ctx, next) => {
  try {
    await next();
  } catch (error) {
    if (!axiosClient.isAxiosError(error)) throw error;
    const status = error.response?.status;
    const messages = {
      INVALID_AGE: '请填写 1 至 120 之间的真实年龄',
      INVALID_REGION: '请选择有效的国家或地区',
      INVALID_CURSOR: '记录分页已失效，请刷新后重试',
      INVALID_REQUEST_ID: '送礼单号格式有误，请核对后重试',
      LIVE_PROFILE_REQUIRED: '请完善年龄和地区，并确认账号具备开播权限',
    };
    const message = messages[error.response?.data?.code];
    if (status && status >= 400 && status < 500 && message) {
      throw new CustomError(message, status, status);
    }
    throw error;
  }
});

async function requireUser(ctx) {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== ALLOW_HTTP_CODE.ok || !userInfo?.id) {
    throw new CustomError(message || '未登录！', code, code);
  }
  return userInfo;
}

async function postRevenue(path: string, data: any, token?: string, internal = false): Promise<any> {
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
    can_go_live: true,
    live_gifts_enabled: true,
    account_type: 'NORMAL',
  }, undefined, true);
}

revenueRouter.get('/profile', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/users/me/profile`, { headers: { Authorization: ctx.headers.authorization || '' } });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.put('/profile', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.put(`${REVENUE_API_URL}/v1/users/me/profile`, ctx.request.body || {}, { headers: { Authorization: ctx.headers.authorization || '' } });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/gift-orders', async ctx => {
  await requireUser(ctx);
  const cursor = ctx.query.before_id;
  if (cursor !== undefined && (typeof cursor !== 'string' || !/^[1-9][0-9]{0,19}$/.test(cursor))) {
    throw new CustomError('记录分页已失效，请刷新后重试', 400, 400);
  }
  const result = await axios.get(`${REVENUE_API_URL}/v1/users/me/gifts`, {
    params: cursor === undefined ? {} : { before_id: cursor }, headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
});

revenueRouter.get('/gift-orders/:requestId', async ctx => {
  await requireUser(ctx);
  try {
    const result = await axios.get(`${REVENUE_API_URL}/v1/users/me/gifts/${encodeURIComponent(ctx.params.requestId)}`, {
      headers: { Authorization: ctx.headers.authorization || '' },
    });
    successHandler({ ctx, data: result });
  } catch (error) {
    if (axiosClient.isAxiosError(error) && error.response?.status === 404) {
      throw new CustomError('尚未查到已确认送礼记录，请继续核实，勿重复送礼', 404, 404);
    }
    throw error;
  }
});

revenueRouter.get('/gifts', async (ctx, next) => {
  // Pass-through. Each gift includes effect_type (none|yyeva|svga|lottie), effect_url,
  // effect_video_mode, effect_md5 and icon_url. Fullscreen gifts use effect_type "yyeva".
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

revenueRouter.get('/level', async (ctx, next) => {
  const user = await requireUser(ctx);
  await syncRevenueUser(user);
  const result = await axios.get(`${REVENUE_API_URL}/v1/users/me/level`, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/referrals/me', async (ctx, next) => {
  const user = await requireUser(ctx);
  await syncRevenueUser(user);
  const result = await axios.get(`${REVENUE_API_URL}/v1/referrals/me`, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/referrals/rewards', async (ctx, next) => {
  await requireUser(ctx);
  const before = ctx.query.before_id;
  if (before !== undefined && (typeof before !== 'string' || !/^[1-9][0-9]{0,19}$/.test(before)))
    throw new CustomError('分页无效', 400, 400);
  const result = await axios.get(REVENUE_API_URL + '/v1/referrals/rewards' + (before ? '?before_id=' + before : ''), {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.post('/referrals/redeem', async (ctx, next) => {
  const user = await requireUser(ctx);
  const token = ctx.headers.authorization?.split(' ')[1];
  const body = ctx.request.body || {};
  if (typeof body.request_id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.request_id)) {
    throw new CustomError('邀请兑换缺少有效单号，请更新客户端后重试', 400, 400);
  }
  if (typeof body.code !== 'string' || body.code.trim() === '' || body.code.trim().length > 32) {
    throw new CustomError('请填写邀请码', 400, 400);
  }
  await syncRevenueUser(user);
  try {
    const result = await postRevenue('/v1/referrals/redeem', {
      request_id: body.request_id.toLowerCase(),
      code: body.code.trim().toUpperCase(),
    }, token);
    successHandler({ ctx, data: result });
  } catch (error) {
    if (!axiosClient.isAxiosError(error)) throw error;
    const status = error.response?.status;
    const payload = error.response?.data;
    if (status && status >= 400 && status < 500 && typeof payload?.code === 'string' && typeof payload?.message === 'string') {
      ctx.status = status;
      ctx.body = { code: payload.code, message: payload.message };
    } else {
      throw error;
    }
  }
  await next();
});

revenueRouter.get('/earnings', async (ctx, next) => {
  await requireUser(ctx);
  const applications: any = await axios.get(`${REVENUE_API_URL}/v1/users/me/creator-applications`, {
    headers: { Authorization: ctx.headers.authorization || '' },
    timeout: 10000,
  });
  if (!applications?.reviews?.some((application) => application.status === 'APPROVED'))
    throw new CustomError('请先完成主播认证', 403, 403);
  const result = await axios.get(`${REVENUE_API_URL}/v1/creators/me/earnings`, {
    params: ctx.query,
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/dashboard', async (ctx, next) => {
  await requireUser(ctx);
  const applications: any = await axios.get(`${REVENUE_API_URL}/v1/users/me/creator-applications`, {
    headers: { Authorization: ctx.headers.authorization || '' },
    timeout: 10000,
  });
  if (!applications?.reviews?.some((application) => application.status === 'APPROVED'))
    throw new CustomError('请先完成主播认证', 403, 403);
  const result = await axios.get(`${REVENUE_API_URL}/v1/creators/me/dashboard`, {
    headers: { Authorization: ctx.headers.authorization || '' }, timeout: 10000,
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/sessions', async ctx => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/creators/me/sessions`, {
    headers: { Authorization: ctx.headers.authorization || '' }, timeout: 10000,
  });
  successHandler({ ctx, data: result });
});

revenueRouter.get('/income', async ctx => {
  await requireUser(ctx);
  const applications: any = await axios.get(`${REVENUE_API_URL}/v1/users/me/creator-applications`, {
    headers: { Authorization: ctx.headers.authorization || '' }, timeout: 10000,
  });
  if (!applications?.reviews?.some(row => row.status === 'APPROVED')) throw new CustomError('请先完成主播认证', 403, 403);
  const query = new URLSearchParams();
  if (ctx.query.source) query.set('source', String(ctx.query.source));
  if (ctx.query.before) query.set('before', String(ctx.query.before));
  const result = await axios.get(`${REVENUE_API_URL}/v1/creators/me/income?${query}`, {
    headers: { Authorization: ctx.headers.authorization || '' }, timeout: 10000,
  });
  successHandler({ ctx, data: result });
});

revenueRouter.get('/supporters', async ctx => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/creators/me/supporters`, {
    headers: { Authorization: ctx.headers.authorization || '' }, timeout: 10000,
  });
  successHandler({ ctx, data: result });
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
  const body = ctx.request.body || {};
  // A request belongs to one user intent. Never manufacture a new key on retries.
  if (typeof body.request_id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.request_id)) {
    throw new CustomError('送礼请求缺少有效单号，请更新客户端后重试', 400, 400);
  }
  await syncRevenueUser(user);
  try {
    const result = await postRevenue('/v1/gifts', {
      request_id: body.request_id.toLowerCase(),
      viewer_id: Number(user.id),
      creator_id: Number(body.creator_id),
      live_session_id: Number(body.live_session_id),
      match_id: body.match_id ? Number(body.match_id) : null,
      gift_id: String(body.gift_id || ''),
      quantity: Number(body.quantity || 1),
      // Ledger hashes the entire payload; gateway receipt time must not change it.
      sent_at_ms: 0,
    }, token);
    successHandler({ ctx, data: result });
    const acceptedBody = result?.accepted === true ? result : result?.data;
    if (acceptedBody?.accepted === true) {
      try {
        publishAcceptedGift({
          roomId: body.live_session_id,
          requestId: body.request_id,
          giftId: body.gift_id,
          viewerId: user.id,
          quantity: body.quantity || 1,
          senderName: user.username,
        });
      } catch {
        // The debit is already committed. A missed animation must not turn it into an error.
      }
    }
  } catch (error) {
    const refusals = {
      CREATOR_NOT_ELIGIBLE: '主播当前无法接收礼物，本次未扣币',
      CREATOR_NOT_LIVE_HOST: '直播间主播信息不匹配，本次未扣币',
      LIVE_SESSION_NOT_FOUND: '直播间不存在，本次未扣币',
      LIVE_SESSION_CLOSED: '直播已结束，本次未扣币',
      GIFT_NOT_FOUND: '礼物已下架，本次未扣币',
      MATCH_CLOSED: 'PK 已结束，本次未扣币',
      INSUFFICIENT_BALANCE: '余额不足，本次未扣币',
    };
    if (!axiosClient.isAxiosError(error)) throw error;
    const status = error.response?.status;
    const code = error.response?.data?.code;
    if (!status || status < 400 || status >= 500 || !refusals[code]) throw error;
    // These codes are returned only after Revenue commits a durable rejection.
    ctx.status = status;
    ctx.body = { code: status, errorCode: code, message: refusals[code], gift_outcome: 'REJECTED' };
  }
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
  }, undefined, true);
  successHandler({ ctx, data: { ...result, live_session_id: liveSessionId } });
  await next();
});

revenueRouter.post('/matches/start', async (ctx) => {
  await requireUser(ctx);
  throw new CustomError('请通过邀请并由对方接受后开始 PK', 410, 410);
});

revenueRouter.get('/lives/:id/match', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/lives/${encodeURIComponent(ctx.params.id)}/match`, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.post('/matches/:id/close', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.post(`${REVENUE_API_URL}/v1/matches/${encodeURIComponent(ctx.params.id)}/close`, {}, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.get('/match-invitations', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/match-invitations`, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.post('/match-invitations', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.post(`${REVENUE_API_URL}/v1/match-invitations`, ctx.request.body || {}, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
  await next();
});

revenueRouter.post('/match-invitations/:id/respond', async (ctx, next) => {
  await requireUser(ctx);
  const result = await axios.post(`${REVENUE_API_URL}/v1/match-invitations/${encodeURIComponent(ctx.params.id)}/respond`, ctx.request.body || {}, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: result });
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

revenueRouter.get('/lives/:id/stats', async ctx => {
  requireLiveId(ctx.params.id);
  await forwardWatch(ctx, 'GET', `/v1/lives/${ctx.params.id}/stats`);
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

const uuidV4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function requireLiveId(id: string) {
  if (!/^[1-9][0-9]{0,19}$/.test(id || '')) {
    throw new CustomError('直播间编号无效', 400, 400);
  }
}

async function forwardWatch(ctx, method: 'GET' | 'POST', path: string, data?: any) {
  await requireUser(ctx);
  try {
    const headers = { Authorization: ctx.headers.authorization || '' };
    const result = method === 'GET'
      ? await axios.get(`${REVENUE_API_URL}${path}`, { headers })
      : await axios.post(`${REVENUE_API_URL}${path}`, data ?? {}, { headers });
    successHandler({ ctx, data: result });
  } catch (error) {
    if (!axiosClient.isAxiosError(error)) throw error;
    const status = error.response?.status;
    const message = error.response?.data?.message;
    if (status && status >= 400 && status < 500 && typeof message === 'string' && message) {
      throw new CustomError(message, status, status);
    }
    throw error;
  }
}

revenueRouter.get('/lives/:liveId/watch-access', async (ctx) => {
  requireLiveId(ctx.params.liveId);
  await forwardWatch(ctx, 'GET', `/v1/lives/${ctx.params.liveId}/watch-access`);
});

revenueRouter.post('/lives/:liveId/watch-heartbeat', async (ctx) => {
  requireLiveId(ctx.params.liveId);
  const body = ctx.request.body || {};
  if (typeof body.request_id !== 'string' || !uuidV4.test(body.request_id)) {
    throw new CustomError('观看心跳单号无效，请使用 UUID v4', 400, 400);
  }
  if (typeof body.delta_seconds !== 'number' || !Number.isInteger(body.delta_seconds)) {
    throw new CustomError('请提供整数观看心跳秒数', 400, 400);
  }
  await forwardWatch(ctx, 'POST', `/v1/lives/${ctx.params.liveId}/watch-heartbeat`, {
    request_id: body.request_id.toLowerCase(),
    delta_seconds: body.delta_seconds,
  });
});

revenueRouter.post('/lives/:liveId/watch-unlock', async (ctx) => {
  requireLiveId(ctx.params.liveId);
  const body = ctx.request.body || {};
  if (typeof body.request_id !== 'string' || !uuidV4.test(body.request_id)) {
    throw new CustomError('解锁单号无效，请使用 UUID v4', 400, 400);
  }
  await forwardWatch(ctx, 'POST', `/v1/lives/${ctx.params.liveId}/watch-unlock`, {
    request_id: body.request_id.toLowerCase(),
  });
});

revenueRouter.put('/lives/:liveId/watch-price', async (ctx) => {
  requireLiveId(ctx.params.liveId);
  const price = ctx.request.body?.unlock_price_coins;
  if (typeof price !== 'number' || !Number.isInteger(price) || price < 0) {
    throw new CustomError('请填写 0 及以上的整数观看价格，0 表示免费', 400, 400);
  }
  await requireUser(ctx);
  try {
    const result = await axios.put(`${REVENUE_API_URL}/v1/lives/${ctx.params.liveId}/watch-price`, { unlock_price_coins: price }, {
      headers: { Authorization: ctx.headers.authorization || '' },
    });
    successHandler({ ctx, data: result });
  } catch (error) {
    if (!axiosClient.isAxiosError(error)) throw error;
    const status = error.response?.status;
    const message = error.response?.data?.message;
    if (status && status >= 400 && status < 500 && typeof message === 'string' && message) {
      throw new CustomError(message, status, status);
    }
    throw error;
  }
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
