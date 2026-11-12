import jwt from 'jsonwebtoken';
import axiosClient from 'axios';
import Router from 'koa-router';

import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { RequestRateLimiter } from '@/app/request-rate';
import { INTERNAL_SERVICE_KEY, LIVEKIT_API_KEY, LIVEKIT_API_SECRET, REVENUE_API_URL } from '@/config/secret';
import { ALLOW_HTTP_CODE } from '@/constant';
import { CustomError } from '@/model/customError.model';
import axios from '@/utils/request';
import { mediaAuthority, mediaSessionHash } from '@/service/media-authority';
import { requireMediaAccess } from '@/service/media-access';
import { confirmDmCall, DmCallError, endDmCall, joinDmCall, openDmCall, readDmCall, incomingDmCall } from '@/service/dm-call';
import User from '@/model/user.model';
import { snapshotDmCall, storedDmCall } from '@/service/dm-call-history';

const liveKitRouter = new Router({ prefix: '/livekit' });
const callRate = new RequestRateLimiter();

liveKitRouter.use(async (_ctx, next) => {
  try { await next(); }
  catch (error) {
    if (!axiosClient.isAxiosError(error)) throw error;
    if (error.response?.status === 404) throw new CustomError('直播间或 PK 已不存在，请返回刷新', 404, 404);
    throw new CustomError('直播服务暂时不可用，请稍后重试', 503, 503);
  }
});

liveKitRouter.post('/token', async (ctx, next) => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== ALLOW_HTTP_CODE.ok || !userInfo?.id) {
    throw new CustomError(message || '未登录！', code, code);
  }
  const liveSessionId = String(ctx.request.body?.live_session_id || '');
  let matchId = String(ctx.request.body?.match_id || '');
  if (!/^\d+$/.test(liveSessionId) || Number(liveSessionId) <= 0) {
    throw new CustomError('live_session_id 无效', ALLOW_HTTP_CODE.paramsError, ALLOW_HTTP_CODE.paramsError);
  }
  const live = await axios.get(`${REVENUE_API_URL}/v1/lives/${liveSessionId}`, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  const session: any = (live as any)?.data?.live_session_id ? (live as any).data : live as any;
  if (session?.status !== 'OPEN') {
    throw new CustomError('直播间已关闭', ALLOW_HTTP_CODE.paramsError, ALLOW_HTTP_CODE.paramsError);
  }
  const creatorId = Number(session.creator_id);
  if (!matchId) {
    const active: any = await axios.get(`${REVENUE_API_URL}/v1/lives/${liveSessionId}/match`, {
      headers: { Authorization: ctx.headers.authorization || '' },
    });
    const activeMatch = active?.data || active;
    if (activeMatch?.match_id) matchId = String(activeMatch.match_id);
  }
  let room = liveSessionId;
  let canPublish = creatorId === Number(userInfo.id);
  if (matchId) {
    if (!/^\d+$/.test(matchId) || Number(matchId) <= 0) {
      throw new CustomError('match_id 无效', ALLOW_HTTP_CODE.paramsError, ALLOW_HTTP_CODE.paramsError);
    }
    const match = await axios.get(`${REVENUE_API_URL}/v1/matches/${matchId}`, {
      headers: { Authorization: ctx.headers.authorization || '' },
    });
    const details: any = (match as any)?.data?.match_id ? (match as any).data : match as any;
    const belongsToMatch = Number(details?.live_session_id) === Number(liveSessionId)
      || Number(details?.opponent_live_session_id) === Number(liveSessionId);
    const isParticipant = Number(details?.creator_id) === Number(userInfo.id)
      || Number(details?.opponent_creator_id) === Number(userInfo.id);
    if (details?.status !== 'OPEN' || !belongsToMatch) {
      throw new CustomError('此直播间当前不在该 PK 中', ALLOW_HTTP_CODE.forbidden, ALLOW_HTTP_CODE.forbidden);
    }
    room = `pk-${matchId}`;
    canPublish = isParticipant;
  }
  const authority = await mediaAuthority(room);
  if (!authority.open) throw new CustomError('直播已结束或主播暂无开播权限', 403, 403);
  canPublish = authority.publishers.includes(String(userInfo.id));
  const mediaLease = await requireMediaAccess(String(userInfo.id), liveSessionId, room);
  const publishSources = authority.publish_sources?.[String(userInfo.id)];
  const token = jwt.sign({
    metadata: JSON.stringify({ session_hash: mediaSessionHash(ctx.headers.authorization?.split(' ')[1] || ''), live_id: liveSessionId }),
    video: {
      room,
      roomJoin: true,
      canPublish,
      ...(publishSources ? { canPublishSources: publishSources } : {}),
      canSubscribe: true,
      canPublishData: false,
      canUpdateOwnMetadata: false,
    },
  }, LIVEKIT_API_SECRET, {
    algorithm: 'HS256',
    issuer: LIVEKIT_API_KEY,
    subject: String(userInfo.id),
    expiresIn: mediaLease,
  });
  successHandler({ ctx, data: { url: process.env.LIVEKIT_URL || 'ws://10.0.2.2:7880', token } });
  await next();
});

function liveKitPublicUrl(): string {
  return process.env.LIVEKIT_URL || 'ws://10.0.2.2:7880';
}

function bearerToken(ctx): string {
  const header = ctx.headers.authorization || '';
  return header.startsWith('Bearer ') ? header.slice(7) : '';
}

async function authedCaller(ctx) {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== ALLOW_HTTP_CODE.ok || !userInfo?.id) {
    throw new CustomError(message || '未登录！', code, code);
  }
  const retry = callRate.consume(String(userInfo.id), 30);
  if (retry) {
    ctx.set('Retry-After', String(retry));
    throw new CustomError('通话请求较频繁，请稍后重试', 429, 429);
  }
  return userInfo;
}

function rethrowCall(error: unknown): never {
  if (error instanceof DmCallError) throw new CustomError(error.message, error.status, error.status);
  throw error;
}

async function loadCallPeer(id: string): Promise<{ id: string; disabled: boolean } | null> {
  try {
    // Lazy require so live-token tests do not open MySQL just by loading this router.
    // eslint-disable-next-line global-require
    const imported = require('@/model/user.model');
    const userModel = typeof imported?.default?.findByPk === 'function' ? imported.default : imported;
    const user = await userModel.findByPk(id, { attributes: ['id', 'status'] });
    if (!user) return null;
    return { id: String(user.id), disabled: Number(user.status) === 2 };
  } catch (error) {
    if (error instanceof CustomError || error instanceof DmCallError) throw error;
    throw new CustomError('账号服务暂时不可用，请稍后重试', 503, 503);
  }
}

function callPriceFrom(result: any): number {
  const body = result?.data?.price_coins === undefined ? result : result.data;
  const price = body?.price_coins;
  if (!Number.isInteger(price) || price < 0 || price > 1_000_000) throw new DmCallError('通话价格暂时无法确认', 503);
  return price;
}

function liveKitAdminToken(room: string): string {
  return jwt.sign({ video: { room, roomAdmin: true } }, LIVEKIT_API_SECRET, {
    algorithm: 'HS256', issuer: LIVEKIT_API_KEY, subject: 'dm-call-settlement', expiresIn: 30,
  });
}

function liveKitApiUrl(): string {
  return process.env.LIVEKIT_API_URL || 'http://127.0.0.1:7880';
}

async function settleConnectedCall(callId: string, payerId: string, ownerId: string, priceCoins: number): Promise<void> {
  try {
    await axios.post(`${REVENUE_API_URL}/v1/internal/dm-call-settle`, {
      call_id: callId, payer_id: Number(payerId), owner_id: Number(ownerId), price_coins: priceCoins,
    }, { headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY } });
  } catch (error) {
    if (axiosClient.isAxiosError(error)) {
      const status = error.response?.status || 503;
      throw new DmCallError(error.response?.data?.message || '通话结算暂时不可用', status);
    }
    throw error;
  }
}

async function liveKitParticipants(room: string): Promise<string[]> {
  try {
    const result = await axiosClient.post(`${liveKitApiUrl()}/twirp/livekit.RoomService/ListParticipants`, { room }, {
      headers: { Authorization: `Bearer ${liveKitAdminToken(room)}`, 'Content-Type': 'application/json' }, timeout: 3000,
    });
    return Array.isArray(result.data?.participants) ? result.data.participants.map((p: any) => String(p.identity)) : [];
  } catch {
    throw new DmCallError('通话连接暂时无法确认', 503);
  }
}

const monitoredPaidCalls = new Set<string>();

async function monitorPaidCall(callId: string, callerId: string, room: string): Promise<void> {
  if (monitoredPaidCalls.has(callId)) return;
  monitoredPaidCalls.add(callId);
  try {
    for (let attempt = 0; attempt < 600; attempt++) {
      await new Promise(resolve => setTimeout(resolve, 1000));
      try {
        await confirmDmCall({ userId: callerId, callId, participants: liveKitParticipants, settle: settleConnectedCall });
        return;
      } catch (error) {
        if (error instanceof DmCallError && error.status === 404) return;
        if (error instanceof DmCallError && error.message === '双方尚未接通') continue;
        if (error instanceof DmCallError && error.status === 503) continue;
        await endDmCall({ userId: callerId, callId });
        await axiosClient.post(`${liveKitApiUrl()}/twirp/livekit.RoomService/DeleteRoom`, { room }, {
          headers: { Authorization: `Bearer ${liveKitAdminToken(room)}`, 'Content-Type': 'application/json' }, timeout: 3000,
        }).catch(() => undefined);
        return;
      }
    }
  } finally {
    monitoredPaidCalls.delete(callId);
  }
}

liveKitRouter.get('/call-prices/:userId', async (ctx) => {
  await authedCaller(ctx);
  const id = String(ctx.params.userId || '');
  if (!/^[1-9][0-9]{0,9}$/.test(id)) throw new CustomError('用户 ID 无效', 400, 400);
  const result = await axios.get(`${REVENUE_API_URL}/v1/dm/call-prices/${id}`, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: { price_coins: callPriceFrom(result) } });
});

liveKitRouter.get('/call-price', async (ctx) => {
  await authedCaller(ctx);
  const result = await axios.get(`${REVENUE_API_URL}/v1/dm/call-price`, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: { price_coins: callPriceFrom(result) } });
});

liveKitRouter.put('/call-price', async (ctx) => {
  const user = await authedCaller(ctx);
  const price = ctx.request.body?.price_coins;
  if (!Number.isInteger(price) || price < 0 || price > 1_000_000) throw new CustomError('视频通话价格需为 0 至 1000000 金币', 400, 400);
  if (price > 0) {
    const applications: any = await axios.get(`${REVENUE_API_URL}/v1/users/me/creator-applications`, {
      headers: { Authorization: ctx.headers.authorization || '' }, timeout: 10000,
    });
    if (!applications?.reviews?.some(row => row.status === 'APPROVED'))
      throw new CustomError('只有认证通过的主播才能设置付费视频通话', 403, 403);
  }
  await axios.post(`${REVENUE_API_URL}/v1/internal/users`, {
    user_id: Number(user.id), can_go_live: true, live_gifts_enabled: true, account_type: 'NORMAL',
  }, { headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY } });
  await axios.put(`${REVENUE_API_URL}/v1/dm/call-price`, { price_coins: price }, {
    headers: { Authorization: ctx.headers.authorization || '' },
  });
  successHandler({ ctx, data: { price_coins: price } });
});

// 1v1 私信通话。房间名 dm-<hex> 不进入直播/PK 媒体扫描。双方都可发布，密钥只留在服务端。
liveKitRouter.post('/calls', async (ctx, next) => {
  const userInfo = await authedCaller(ctx);
  const peerId = String(ctx.request.body?.peer_id ?? '').trim();
  const mode = String(ctx.request.body?.mode ?? '').trim();
  if (!/^[1-9][0-9]{0,9}$/.test(peerId)) {
    throw new CustomError('peer_id 无效', ALLOW_HTTP_CODE.paramsError, ALLOW_HTTP_CODE.paramsError);
  }
  const peer = peerId === String(userInfo.id)
    ? { id: peerId, disabled: false }
    : await loadCallPeer(peerId);
  let priceCoins = 0;
  let priceOwnerId = peerId;
  if (mode === 'video' && peer) {
    const result = await axios.get(`${REVENUE_API_URL}/v1/dm/call-prices/${peerId}`, {
      headers: { Authorization: ctx.headers.authorization || '' },
    });
    priceCoins = callPriceFrom(result);
  }
  const offeredPrice = ctx.request.body?.offered_price_coins;
  if (offeredPrice !== undefined) {
    if (mode !== 'video' || !Number.isInteger(offeredPrice) || offeredPrice < 1 || offeredPrice > 1_000_000)
      throw new CustomError('本次视频通话价格需为 1 至 1000000 金币', 400, 400);
    const applications: any = await axios.get(`${REVENUE_API_URL}/v1/users/me/creator-applications`, {
      headers: { Authorization: ctx.headers.authorization || '' }, timeout: 10000,
    });
    if (!applications?.reviews?.some(row => row.status === 'APPROVED'))
      throw new CustomError('只有认证通过的主播才能发起付费视频通话', 403, 403);
    priceCoins = offeredPrice;
    priceOwnerId = String(userInfo.id);
  }
  const acceptedPriceCoins = ctx.request.body?.accepted_price_coins;
  if (!Number.isInteger(acceptedPriceCoins) || acceptedPriceCoins < 0) {
    throw new CustomError('请先确认视频通话价格', 400, 400);
  }
  await axios.post(`${REVENUE_API_URL}/v1/internal/users`, {
    user_id: Number(userInfo.id), can_go_live: true, live_gifts_enabled: true, account_type: 'NORMAL',
  }, { headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY } });
  try {
    const created = await openDmCall({
        callerId: String(userInfo.id),
        peer,
        mode,
        priceCoins,
        priceOwnerId,
        acceptedPriceCoins,
        loginToken: bearerToken(ctx),
        url: liveKitPublicUrl(),
        apiKey: LIVEKIT_API_KEY,
        apiSecret: LIVEKIT_API_SECRET,
      });
    await snapshotDmCall(String(userInfo.id), created.call_id);
    successHandler({ ctx, data: created });
  } catch (error) {
    rethrowCall(error);
  }
  await next();
});

liveKitRouter.post('/calls/join', async (ctx, next) => {
  const userInfo = await authedCaller(ctx);
  try {
    const joined = await joinDmCall({
      userId: String(userInfo.id),
      callId: String(ctx.request.body?.call_id ?? '').trim(),
      acceptedPriceCoins: ctx.request.body?.accepted_price_coins,
      loginToken: bearerToken(ctx),
      url: liveKitPublicUrl(),
      apiKey: LIVEKIT_API_KEY,
      apiSecret: LIVEKIT_API_SECRET,
    });
    successHandler({
      ctx,
      data: joined,
    });
    if (joined.mode === 'video' && joined.price_coins > 0 && joined.peer_id === String(userInfo.id)) {
      void monitorPaidCall(joined.call_id, joined.caller_id, joined.room).catch(() => undefined);
    }
  } catch (error) {
    rethrowCall(error);
  }
  await next();
});

liveKitRouter.post('/calls/connected', async (ctx, next) => {
  const userInfo = await authedCaller(ctx);
  try {
    const confirmed = await confirmDmCall({
      userId: String(userInfo.id),
      callId: String(ctx.request.body?.call_id ?? '').trim(),
      participants: liveKitParticipants,
      settle: settleConnectedCall,
    });
    await snapshotDmCall(String(userInfo.id), confirmed.call_id);
    successHandler({ ctx, data: confirmed });
  } catch (error) {
    rethrowCall(error);
  }
  await next();
});

liveKitRouter.get('/calls/incoming', async ctx => {
  const user = await authedCaller(ctx);
  const call = incomingDmCall(String(user.id));
  const caller: any = call ? await User.findByPk(call.caller_id) : null;
  successHandler({ ctx, data: { call: call && caller?.status === 1 ? { ...call, caller_name: caller.username, caller_avatar: caller.avatar || '' } : null } });
});

liveKitRouter.get('/calls/:callId', async ctx => {
  const user = await authedCaller(ctx);
  try { successHandler({ ctx, data: await storedDmCall(String(user.id), ctx.params.callId) }); }
  catch (error) { rethrowCall(error); }
});

liveKitRouter.post('/calls/end', async (ctx, next) => {
  const userInfo = await authedCaller(ctx);
  try {
    const ended = await endDmCall({
        userId: String(userInfo.id),
        callId: String(ctx.request.body?.call_id ?? '').trim(),
        reason: ctx.request.body?.reason === 'rejected' ? 'rejected' : 'ended',
      });
    try { await snapshotDmCall(String(userInfo.id), ended.call_id); }
    catch (error) { if (!(error instanceof DmCallError) || error.status !== 404) throw error; }
    successHandler({ ctx, data: ended });
  } catch (error) {
    rethrowCall(error);
  }
  await next();
});

export default liveKitRouter;
