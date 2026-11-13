import Router from 'koa-router';
import sharp from 'sharp';
import Cover from '@/model/liveCover.model';
import ProfileCover from '@/model/profileCover.model';
import DisplayQr from '@/model/displayQr.model';
import GiftIcon from '@/model/giftIcon.model';
import { authJwt } from '@/app/auth/authJwt';
import { requireOperations } from '@/app/auth/operations';
import { CustomError } from '@/model/customError.model';
import successHandler from '@/app/handler/success-handle';
import { RequestRateLimiter } from '@/app/request-rate';
const router = new Router();
const rate = new RequestRateLimiter();
const giftIdPattern = /^[A-Za-z0-9_-]{1,32}$/;
router.get('/live/cover/me', async ctx => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id)
    throw new CustomError('请重新登录', 401, 401);
  const cover: any = await Cover.findByPk(Number(auth.userInfo.id), { attributes: ['user_id'] });
  successHandler({ ctx, data: { cover_path: cover ? `/live/covers/${auth.userInfo.id}` : null } });
});
router.put('/live/cover', async (ctx) => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id)
    throw new CustomError('请重新登录', 401, 401);
  if (rate.consume(`cover:${auth.userInfo.id}`, 5))
    throw new CustomError('上传频繁，请稍后重试', 429, 429);
  if (ctx.get('content-type') !== 'application/octet-stream')
    throw new CustomError('请以二进制方式上传图片', 415, 415);
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of ctx.req) {
    const buffer = Buffer.from(chunk);
    size += buffer.length;
    if (size > 3 * 1024 * 1024)
      throw new CustomError('图片不能超过 3MB', 413, 413);
    chunks.push(buffer);
  }
  let image: Buffer;
  try {
    const input = Buffer.concat(chunks);
    const meta = await sharp(input, {
      limitInputPixels: 16000000,
      failOn: 'warning',
    }).metadata();
    if (
      !['jpeg', 'png', 'webp'].includes(meta.format || '') ||
      (meta.pages || 1) > 1
    )
      throw Error('format');
    image = await sharp(input, {
      limitInputPixels: 16000000,
      failOn: 'warning',
    })
      .rotate()
      .resize(720, 960, { fit: 'cover', withoutEnlargement: true })
      .jpeg({ quality: 80 })
      .toBuffer();
  } catch {
    throw new CustomError('图片无效，仅支持静态 JPEG、PNG、WebP', 400, 400);
  }
  await Cover.upsert({ user_id: Number(auth.userInfo.id), image });
  successHandler({
    ctx,
    data: {
      cover_path: `/live/covers/${auth.userInfo.id}`,
      bytes: image.length,
    },
  });
});
router.get('/live/covers/:id', async (ctx) => {
  if (!/^[1-9][0-9]{0,9}$/.test(ctx.params.id))
    throw new CustomError('封面不存在', 404, 404);
  const cover: any = await Cover.findByPk(ctx.params.id);
  if (!cover) throw new CustomError('封面不存在', 404, 404);
  ctx.type = 'image/jpeg';
  ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.set('Cache-Control', 'public,max-age=60');
  ctx.body = cover.image;
});
router.put('/user/profile-cover', async ctx => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id)
    throw new CustomError('请重新登录', 401, 401);
  if (rate.consume(`profile-cover:${auth.userInfo.id}`, 5))
    throw new CustomError('上传频繁，请稍后重试', 429, 429);
  if (ctx.get('content-type') !== 'application/octet-stream')
    throw new CustomError('请上传图片文件', 415, 415);
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of ctx.req) {
    const buffer = Buffer.from(chunk);
    size += buffer.length;
    if (size > 3 * 1024 * 1024)
      throw new CustomError('图片不能超过 3MB', 413, 413);
    chunks.push(buffer);
  }
  let image: Buffer;
  try {
    const input = Buffer.concat(chunks);
    const meta = await sharp(input, { limitInputPixels: 16000000, failOn: 'warning' }).metadata();
    if (!['jpeg', 'png', 'webp'].includes(meta.format || '') || (meta.pages || 1) > 1)
      throw Error('format');
    image = await sharp(input, { limitInputPixels: 16000000, failOn: 'warning' })
      .rotate()
      .resize(1080, 720, { fit: 'cover', withoutEnlargement: true })
      .jpeg({ quality: 80 })
      .toBuffer();
  } catch {
    throw new CustomError('图片无效，仅支持静态 JPEG、PNG、WebP', 400, 400);
  }
  await ProfileCover.upsert({ user_id: Number(auth.userInfo.id), image });
  successHandler({ ctx, data: { cover_path: `/user/profile-covers/${auth.userInfo.id}` } });
});
router.get('/user/profile-covers/:id', async ctx => {
  if (!/^[1-9][0-9]{0,9}$/.test(ctx.params.id))
    throw new CustomError('封面不存在', 404, 404);
  const cover: any = await ProfileCover.findByPk(ctx.params.id);
  if (!cover) throw new CustomError('封面不存在', 404, 404);
  ctx.type = 'image/jpeg';
  ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.set('Cache-Control', 'no-store');
  ctx.body = cover.image;
});
const qrKind = (raw: string) => {
  if (raw !== 'support' && raw !== 'recharge')
    throw new CustomError('二维码类型不存在', 404, 404);
  return raw;
};
router.get('/operations/qr/:kind', async (ctx) => {
  await requireOperations(ctx);
  const qr: any = await DisplayQr.findByPk(qrKind(ctx.params.kind), { attributes: ['updated_at'] });
  successHandler({ ctx, data: { configured: !!qr, updated_at: qr?.updated_at || null } });
});
router.put('/operations/qr/:kind', async (ctx) => {
  await requireOperations(ctx);
  const kind = qrKind(ctx.params.kind);
  if (ctx.get('content-type') !== 'application/octet-stream')
    throw new CustomError('请上传图片文件', 415, 415);
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of ctx.req) {
    const buffer = Buffer.from(chunk);
    size += buffer.length;
    if (size > 3 * 1024 * 1024)
      throw new CustomError('图片不能超过 3MB', 413, 413);
    chunks.push(buffer);
  }
  let image: Buffer;
  try {
    const input = Buffer.concat(chunks);
    const meta = await sharp(input, { limitInputPixels: 16000000, failOn: 'warning' }).metadata();
    if (!['jpeg', 'png', 'webp'].includes(meta.format || '') || (meta.pages || 1) > 1)
      throw Error('format');
    image = await sharp(input, { limitInputPixels: 16000000, failOn: 'warning' })
      .rotate()
      .resize(960, 960, { fit: 'inside', withoutEnlargement: true })
      .png()
      .toBuffer();
    if (image.length > 3 * 1024 * 1024) throw Error('size');
  } catch {
    throw new CustomError('图片无效，仅支持 3MB 内的静态 JPEG、PNG、WebP', 400, 400);
  }
  await DisplayQr.upsert({ kind, image });
  successHandler({ ctx, data: { configured: true } });
});
router.get('/qr/:kind', async (ctx) => {
  const kind = qrKind(ctx.params.kind);
  const qr: any = await DisplayQr.findByPk(kind);
  if (!qr) throw new CustomError('二维码尚未配置', 404, 404);
  ctx.type = 'image/png';
  ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.set('Cache-Control', 'no-store');
  ctx.body = qr.image;
});
router.put('/operations/gifts/:id/icon', async ctx => {
  const operator = await requireOperations(ctx);
  const giftId = ctx.params.id;
  if (!giftIdPattern.test(giftId)) throw new CustomError('礼物标识无效', 400, 400);
  if (rate.consume(`gift-icon:${operator.id}`, 20))
    throw new CustomError('上传频繁，请稍后重试', 429, 429);
  if (ctx.get('content-type') !== 'application/octet-stream')
    throw new CustomError('请上传图片文件', 415, 415);
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of ctx.req) {
    const buffer = Buffer.from(chunk);
    size += buffer.length;
    if (size > 3 * 1024 * 1024)
      throw new CustomError('图片不能超过 3MB', 413, 413);
    chunks.push(buffer);
  }
  let image: Buffer;
  try {
    const input = Buffer.concat(chunks);
    const meta = await sharp(input, { limitInputPixels: 16000000, failOn: 'warning' }).metadata();
    if (!['jpeg', 'png', 'webp'].includes(meta.format || '') || (meta.pages || 1) > 1)
      throw Error('format');
    image = await sharp(input, { limitInputPixels: 16000000, failOn: 'warning' })
      .rotate()
      .resize(512, 512, { fit: 'inside', withoutEnlargement: true })
      .png()
      .toBuffer();
    if (image.length > 3 * 1024 * 1024) throw Error('size');
  } catch {
    throw new CustomError('图片无效，仅支持 3MB 内的静态 JPEG、PNG、WebP', 400, 400);
  }
  await GiftIcon.upsert({ gift_id: giftId, image });
  successHandler({ ctx, data: { icon_url: `/gift-icons/${giftId}`, updated_at: Date.now() } });
});
router.get('/gift-icons/:id', async ctx => {
  if (!giftIdPattern.test(ctx.params.id)) throw new CustomError('礼物图片不存在', 404, 404);
  const icon: any = await GiftIcon.findByPk(ctx.params.id);
  if (!icon) throw new CustomError('礼物图片不存在', 404, 404);
  ctx.type = 'image/png';
  ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.set('Cache-Control', 'no-store');
  ctx.body = icon.image;
});
export default router;
