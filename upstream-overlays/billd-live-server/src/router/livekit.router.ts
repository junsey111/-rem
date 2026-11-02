import jwt from 'jsonwebtoken';
import axiosClient from 'axios';
import Router from 'koa-router';

import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { LIVEKIT_API_KEY, LIVEKIT_API_SECRET, REVENUE_API_URL } from '@/config/secret';
import { ALLOW_HTTP_CODE } from '@/constant';
import { CustomError } from '@/model/customError.model';
import axios from '@/utils/request';
import { mediaAuthority, mediaSessionHash } from '@/service/media-authority';

const liveKitRouter = new Router({ prefix: '/livekit' });

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
  const publishSources = authority.publish_sources?.[String(userInfo.id)];
  const token = jwt.sign({
    metadata: JSON.stringify({ session_hash: mediaSessionHash(ctx.headers.authorization?.split(' ')[1] || '') }),
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
    expiresIn: '10m',
  });
  successHandler({ ctx, data: { url: process.env.LIVEKIT_URL || 'ws://10.0.2.2:7880', token } });
  await next();
});

export default liveKitRouter;
