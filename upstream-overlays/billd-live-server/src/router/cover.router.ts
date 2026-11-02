import Router from 'koa-router';
import sharp from 'sharp';
import Cover from '@/model/liveCover.model';
import { authJwt } from '@/app/auth/authJwt';
import { CustomError } from '@/model/customError.model';
import successHandler from '@/app/handler/success-handle';
import { RequestRateLimiter } from '@/app/request-rate';
const router = new Router();
const rate = new RequestRateLimiter();
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
export default router;
