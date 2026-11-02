import fs from 'fs';
import Router from 'koa-router';
import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { OPENIM_PUBLIC_API_URL, OPENIM_WS_URL } from '@/config/secret';
import { CustomError } from '@/model/customError.model';
import { issueOpenImSession, resolveLiveRecipient } from '@/service/openim-session';
import {
  describeDmVideo, DmHookResult, openDmVideoMedia, parseVideoHeader, readDmGift, readVideoBytes, resolveSupportContact,
  storeDmVideo, submitDmGift, unlockDmVideo,
} from '@/service/openim-dm';
import { RequestRateLimiter } from '@/app/request-rate';

const openimRouter = new Router({ prefix: '/openim' });
const recipientRate = new RequestRateLimiter();
const giftRate = new RequestRateLimiter();
const videoRate = new RequestRateLimiter();

async function requireUser(ctx) {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message, code, code);
  return userInfo;
}

function bearer(ctx) {
  return ctx.req.headers.authorization!.slice(7);
}

async function respond(ctx, run) {
  try {
    successHandler({ ctx, data: await run() });
  } catch (error) {
    if (error instanceof DmHookResult) {
      ctx.status = error.httpStatus;
      ctx.body = error.payload;
      return;
    }
    throw error;
  }
}

openimRouter.post('/rooms/:roomId/recipient', async ctx => {
  const userInfo = await requireUser(ctx);
  const retry = recipientRate.consume(String(userInfo.id), 30);
  if (retry) {
    ctx.set('Retry-After', String(retry));
    throw new CustomError('发起私信较频繁，请稍后重试', 429, 429);
  }
  successHandler({ ctx, data: await resolveLiveRecipient(ctx.params.roomId, Number(userInfo.id)) });
});
openimRouter.post('/session', async ctx => {
  const userInfo = await requireUser(ctx);
  const result = await issueOpenImSession(Number(userInfo.id), bearer(ctx));
  successHandler({ ctx, data: { user_id: String(userInfo.id), ...result, api_url: OPENIM_PUBLIC_API_URL, ws_url: OPENIM_WS_URL } });
});
openimRouter.get('/support', async ctx => {
  const userInfo = await requireUser(ctx);
  const retry = recipientRate.consume(`support:${userInfo.id}`, 30);
  if (retry) {
    ctx.set('Retry-After', String(retry));
    throw new CustomError('发起私信较频繁，请稍后重试', 429, 429);
  }
  successHandler({ ctx, data: await resolveSupportContact(Number(userInfo.id)) });
});
openimRouter.post('/dm-gifts', async ctx => {
  const userInfo = await requireUser(ctx);
  const retry = giftRate.consume(String(userInfo.id), 20);
  if (retry) {
    ctx.set('Retry-After', String(retry));
    throw new CustomError('私信送礼较频繁，请稍后重试', 429, 429);
  }
  await respond(ctx, () => submitDmGift(Number(userInfo.id), ctx.request.body || {}, bearer(ctx)));
});
openimRouter.get('/dm-gifts/:requestId', async ctx => {
  const userInfo = await requireUser(ctx);
  await respond(ctx, () => readDmGift(ctx.params.requestId, bearer(ctx)));
});
openimRouter.post('/dm-videos', async ctx => {
  const userInfo = await requireUser(ctx);
  const retry = videoRate.consume(`upload:${userInfo.id}`, 8);
  if (retry) {
    ctx.set('Retry-After', String(retry));
    throw new CustomError('视频上传较频繁，请稍后重试', 429, 429);
  }
  const bytes = await readVideoBytes(ctx);
  const meta = parseVideoHeader(ctx.get('x-dm-video'));
  await respond(ctx, () => storeDmVideo(Number(userInfo.id), meta, bytes));
});
openimRouter.get('/dm-videos/:videoId', async ctx => {
  const userInfo = await requireUser(ctx);
  await respond(ctx, () => describeDmVideo(Number(userInfo.id), ctx.params.videoId, bearer(ctx)));
});
openimRouter.post('/dm-videos/:videoId/unlock', async ctx => {
  const userInfo = await requireUser(ctx);
  const retry = videoRate.consume(`unlock:${userInfo.id}`, 20);
  if (retry) {
    ctx.set('Retry-After', String(retry));
    throw new CustomError('解锁较频繁，请稍后重试', 429, 429);
  }
  await respond(ctx, () => unlockDmVideo(Number(userInfo.id), ctx.params.videoId, ctx.request.body || {}, bearer(ctx)));
});
openimRouter.get('/dm-videos/:videoId/media', async ctx => {
  const userInfo = await requireUser(ctx);
  try {
    const file = await openDmVideoMedia(Number(userInfo.id), ctx.params.videoId, bearer(ctx));
    ctx.type = file.contentType;
    ctx.set('X-Content-Type-Options', 'nosniff');
    ctx.set('Cache-Control', 'private, no-store');
    ctx.body = fs.createReadStream(file.path);
  } catch (error) {
    if (error instanceof DmHookResult) {
      ctx.status = error.httpStatus;
      ctx.body = error.payload;
      return;
    }
    throw error;
  }
});
export default openimRouter;
