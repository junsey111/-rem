import Router from 'koa-router';
import { requireOperations } from '@/app/auth/operations';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import sequelize from '@/config/mysql';
import { PlatformContent, PlatformContentAction, SecretaryFollow } from '@/model/platformContent.model';
import { authJwt } from '@/app/auth/authJwt';
import { createHash, randomUUID } from 'crypto';
import sharp from 'sharp';
import ContentImage from '@/model/contentImage.model';
import { validAnnouncement } from '@/service/announcement-content';
import { RequestRateLimiter } from '@/app/request-rate';

const router = new Router();
const imageRate = new RequestRateLimiter();
for (const method of ['get', 'put', 'delete'] as const) router[method]('/platform-content/secretary/follow', async ctx => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id) throw new CustomError('请重新登录', 401, 401);
  const where = { user_id: Number(auth.userInfo.id) };
  if (method === 'put') await SecretaryFollow.findOrCreate({ where });
  if (method === 'delete') await SecretaryFollow.destroy({ where });
  successHandler({ ctx, data: { following: Boolean(await SecretaryFollow.findByPk(where.user_id)) } });
});
router.post('/operations/content-images', async ctx => {
  const operator = await requireOperations(ctx);
  if (imageRate.consume(`content:${operator.id}`, 30)) throw new CustomError('上传频繁，请稍后再试', 429, 429);
  if (ctx.get('content-type') !== 'application/octet-stream') throw new CustomError('请上传图片文件', 415, 415);
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of ctx.req) {
    const bytes = Buffer.from(chunk); size += bytes.length;
    if (size > 3 * 1024 * 1024) throw new CustomError('图片不能超过 3MB', 413, 413);
    chunks.push(bytes);
  }
  let image: Buffer;
  try {
    const input = Buffer.concat(chunks);
    const metadata = await sharp(input, { limitInputPixels: 16000000, failOn: 'warning' }).metadata();
    if (!['jpeg', 'png', 'webp'].includes(metadata.format || '') || (metadata.pages || 1) > 1) throw Error('format');
    image = await sharp(input, { limitInputPixels: 16000000, failOn: 'warning' }).rotate()
      .resize(1440, 2400, { fit: 'inside', withoutEnlargement: true }).jpeg({ quality: 85 }).toBuffer();
    if (image.length > 3 * 1024 * 1024) throw Error('size');
  } catch { throw new CustomError('仅支持 3MB 内的静态 JPG、PNG、WebP 图片', 400, 400); }
  const id = randomUUID();
  await ContentImage.create({ id, operator_id: Number(operator.id), image });
  successHandler({ ctx, data: { image_path: `/platform-content/images/${id}` } });
});
router.get('/platform-content/images/:id', async ctx => {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(ctx.params.id)) throw new CustomError('图片不存在', 404, 404);
  const row: any = await ContentImage.findByPk(ctx.params.id);
  if (!row) throw new CustomError('图片不存在', 404, 404);
  ctx.type = 'image/jpeg'; ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.set('Cache-Control', 'public,max-age=31536000,immutable'); ctx.body = row.image;
});
const keys = ['announcements', 'secretary', 'noble'];
const validText = (value: unknown, max: number) => typeof value === 'string' && value.trim().length > 0 && Array.from(value).length <= max;

function validate(key: string, value: any) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  if (key === 'secretary') return validText(value.message, 3000);
  if (key === 'announcements') return Array.isArray(value.items) && value.items.length <= 30 && value.items.every(validAnnouncement);
  if (key === 'noble') return Array.isArray(value.tiers) && value.tiers.length === 6 && value.tiers.every((tier: any) =>
    tier && validText(tier.condition, 200) && validText(tier.detail, 200) && Number.isInteger(tier.privilege_count)
      && tier.privilege_count >= 0 && tier.privilege_count <= 15
      && (tier.privilege_ids === undefined || (Array.isArray(tier.privilege_ids)
        && tier.privilege_ids.length === tier.privilege_count
        && tier.privilege_ids.every((id: unknown) => Number.isInteger(id) && Number(id) >= 1 && Number(id) <= 15)
        && new Set(tier.privilege_ids).size === tier.privilege_ids.length)))
    && Array.isArray(value.privileges) && value.privileges.length === 15 && value.privileges.every((item: any) =>
      item && validText(item.title, 40) && validText(item.description, 120));
  return false;
}

router.get('/platform-content/:key', async ctx => {
  const key = ctx.params.key;
  if (!keys.includes(key)) throw new CustomError('内容类型无效', 404, 404);
  const row: any = await PlatformContent.findByPk(key);
  const value = row ? JSON.parse(row.value_json) : null;
  successHandler({ ctx, data: { version: row?.version || 0, configured: !!row,
    value: key === 'announcements' && value ? { items: value.items.filter(item => item.published) } : value } });
});

router.get('/operations/content/:key', async ctx => {
  await requireOperations(ctx);
  const key = ctx.params.key;
  if (!keys.includes(key)) throw new CustomError('内容类型无效', 404, 404);
  const row: any = await PlatformContent.findByPk(key);
  successHandler({ ctx, data: { version: row?.version || 0, configured: !!row,
    value: row ? JSON.parse(row.value_json) : null } });
});

router.put('/operations/content/:key', async ctx => {
  const operator = await requireOperations(ctx);
  const key = ctx.params.key;
  if (!keys.includes(key)) throw new CustomError('内容类型无效', 404, 404);
  const { request_id: requestId, expected_version: expectedVersion, reason, value } = ctx.request.body || {};
  if (typeof requestId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(requestId)
    || !Number.isSafeInteger(expectedVersion) || expectedVersion < 0 || !validText(reason, 200) || !validate(key, value))
    throw new CustomError('请检查内容、版本、操作单号和修改原因', 400, 400);
  const payload = JSON.stringify(value);
  const payloadHash = createHash('sha256').update(payload).digest('hex');
  if (Buffer.byteLength(payload) > 100000) throw new CustomError('内容过长', 400, 400);
  const result = await sequelize.transaction(async transaction => {
    const existingAction: any = await PlatformContentAction.findOne({ where: { request_id: requestId }, transaction });
    if (existingAction) {
      if (existingAction.content_key !== key || Number(existingAction.operator_id) !== Number(operator.id)
        || existingAction.payload_hash !== payloadHash || existingAction.reason !== reason.trim())
        throw new CustomError('操作单号已用于其他修改', 409, 409);
      const current: any = await PlatformContent.findByPk(key, { transaction });
      return { version: current?.version || 0, configured: !!current, value: current ? JSON.parse(current.value_json) : null };
    }
    const row: any = await PlatformContent.findByPk(key, { transaction, lock: transaction.LOCK.UPDATE });
    const version = Number(row?.version || 0);
    if (version !== expectedVersion) throw new CustomError('内容已被其他管理员修改，请刷新后重试', 409, 409);
    if (row) await row.update({ version: version + 1, value_json: payload }, { transaction });
    else await PlatformContent.create({ content_key: key, version: 1, value_json: payload }, { transaction });
    await PlatformContentAction.create({ request_id: requestId, content_key: key, version: version + 1,
      operator_id: Number(operator.id), reason: reason.trim(), payload_hash: payloadHash }, { transaction });
    return { version: version + 1, configured: true, value };
  });
  successHandler({ ctx, data: result });
});

export default router;
