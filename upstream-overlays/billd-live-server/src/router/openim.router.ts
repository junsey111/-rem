import Router from 'koa-router';
import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { OPENIM_PUBLIC_API_URL, OPENIM_WS_URL } from '@/config/secret';
import { CustomError } from '@/model/customError.model';
import { issueOpenImSession, resolveLiveRecipient } from '@/service/openim-session';
import { RequestRateLimiter } from '@/app/request-rate';

const openimRouter = new Router({ prefix: '/openim' });
const recipientRate = new RequestRateLimiter();
openimRouter.post('/rooms/:roomId/recipient', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message, code, code);
  const retry = recipientRate.consume(String(userInfo.id), 30);
  if (retry) {
    ctx.set('Retry-After', String(retry));
    throw new CustomError('发起私信较频繁，请稍后重试', 429, 429);
  }
  successHandler({ ctx, data: await resolveLiveRecipient(ctx.params.roomId, Number(userInfo.id)) });
});
openimRouter.post('/session', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message, code, code);
  const result = await issueOpenImSession(Number(userInfo.id), ctx.req.headers.authorization!.slice(7));
  successHandler({ ctx, data: { user_id: String(userInfo.id), ...result, api_url: OPENIM_PUBLIC_API_URL, ws_url: OPENIM_WS_URL } });
});
export default openimRouter;
