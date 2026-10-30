import jwt from 'jsonwebtoken';
import Router from 'koa-router';

import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { LIVEKIT_API_KEY, LIVEKIT_API_SECRET, REVENUE_API_URL } from '@/config/secret';
import { ALLOW_HTTP_CODE } from '@/constant';
import { CustomError } from '@/model/customError.model';
import axios from '@/utils/request';

const liveKitRouter = new Router({ prefix: '/livekit' });

liveKitRouter.post('/token', async (ctx, next) => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== ALLOW_HTTP_CODE.ok || !userInfo?.id) {
    throw new CustomError(message || '未登录！', code, code);
  }
  const liveSessionId = String(ctx.request.body?.live_session_id || '');
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
  const token = jwt.sign({
    video: {
      room: liveSessionId,
      roomJoin: true,
      canPublish: creatorId === Number(userInfo.id),
      canSubscribe: true,
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
