import crypto from 'crypto';
import Router from 'koa-router';
import axios from 'axios';
import sequelize from '@/config/mysql';
import Identity from '@/model/identityVerification.model';
import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import { RequestRateLimiter } from '@/app/request-rate';
const router = new Router();
const rate = new RequestRateLimiter();
export const identityEnabled = () =>
  process.env.IDENTITY_ENABLED === 'true' &&
  !!process.env.IDENTITY_PROVIDER_URL &&
  !!process.env.IDENTITY_PROVIDER_KEY;
const sign = (data: string) =>
  crypto
    .createHmac('sha256', process.env.IDENTITY_PROVIDER_KEY || '')
    .update(data)
    .digest('hex');
const uuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
router.get('/verification/me', async (ctx) => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id)
    throw new CustomError('请重新登录', 401, 401);
  const row: any = await Identity.findOne({
    where: { user_id: auth.userInfo.id },
    order: [['created_at', 'DESC']],
    attributes: ['request_id', 'status', 'created_at', 'updated_at'],
  });
  successHandler({
    ctx,
    data: { enabled: identityEnabled(), verification: row || null },
  });
});
router.post('/verification/start', async (ctx) => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id)
    throw new CustomError('请重新登录', 401, 401);
  if (!identityEnabled())
    throw new CustomError(
      '实名认证服务尚未启用，不会提交个人资料或返回认证成功',
      503,
      503
    );
  if (rate.consume(`identity:${auth.userInfo.id}`, 5))
    throw new CustomError('认证请求频繁，请稍后重试', 429, 429);
  const id = ctx.request.body?.request_id;
  if (typeof id !== 'string' || !uuid.test(id))
    throw new CustomError('认证申请单号无效', 400, 400);
  const [record]: any = await Identity.findOrCreate({
    where: { request_id: id },
    defaults: { user_id: Number(auth.userInfo.id) },
  });
  if (Number(record.user_id) !== Number(auth.userInfo.id))
    throw new CustomError('认证单号冲突', 409, 409);
  if (record.status !== 'PENDING') {
    successHandler({ ctx, data: { request_id: id, status: record.status } });
    return;
  }
  const endpoint = new URL(process.env.IDENTITY_PROVIDER_URL!);
  if (endpoint.protocol !== 'https:' || endpoint.username || endpoint.password)
    throw new CustomError('认证服务配置无效', 503, 503);
  const timestamp = String(Math.floor(Date.now() / 1000));
  const response = await axios.post(
    endpoint.href,
    {
      request_id: id,
      user_id: Number(auth.userInfo.id),
      timestamp,
      signature: sign(`${id}:${auth.userInfo.id}:${timestamp}`),
    },
    { timeout: 8000, maxRedirects: 0, maxContentLength: 16384 }
  );
  const data = response.data;
  const url = new URL(data?.verification_url);
  const allowed = new URL(
    process.env.IDENTITY_BROWSER_ORIGIN || endpoint.origin
  );
  if (
    data.request_id !== id ||
    url.protocol !== 'https:' ||
    url.origin !== allowed.origin ||
    url.username ||
    url.password
  )
    throw new CustomError('认证服务返回结果无效', 503, 503);
  successHandler({
    ctx,
    data: { request_id: id, status: 'PENDING', verification_url: url.href },
  });
});
router.post('/verification/callback', async (ctx) => {
  if (!identityEnabled()) throw new CustomError('认证服务尚未启用', 503, 503);
  const c = ctx.request.body || {};
  if (
    !uuid.test(c.request_id) ||
    !Number.isSafeInteger(c.user_id) ||
    c.user_id < 1 ||
    !['VERIFIED', 'REJECTED'].includes(c.status) ||
    !Number.isInteger(c.age_years) ||
    c.age_years < 0 ||
    c.age_years > 120 ||
    typeof c.region_code !== 'string' ||
    !/^[A-Z]{2}$/.test(c.region_code) ||
    typeof c.provider_reference !== 'string' ||
    !/^[A-Za-z0-9_-]{1,128}$/.test(c.provider_reference)
  )
    throw new CustomError('认证结果格式无效', 400, 400);
  const expected = sign(
      `${c.request_id}:${c.user_id}:${c.status}:${c.age_years}:${c.region_code}:${c.provider_reference}`
    ),
    signature = ctx.get('X-Identity-Signature');
  if (
    !/^[a-f0-9]{64}$/.test(signature) ||
    !crypto.timingSafeEqual(
      Buffer.from(signature, 'hex'),
      Buffer.from(expected, 'hex')
    )
  )
    throw new CustomError('认证回调签名无效', 403, 403);
  await sequelize.transaction(async (transaction) => {
    const row: any = await Identity.findByPk(c.request_id, {
      transaction,
      lock: transaction.LOCK.UPDATE,
    });
    if (!row || Number(row.user_id) !== c.user_id)
      throw new CustomError('认证申请不存在', 404, 404);
    if (row.status !== 'PENDING') {
      if (
        row.status !== c.status ||
        row.age_years !== c.age_years ||
        row.region_code !== c.region_code ||
        row.provider_reference !== c.provider_reference
      )
        throw new CustomError('认证回调与已确认结果冲突', 409, 409);
      return;
    }
    await row.update(
      {
        status: c.status,
        age_years: c.age_years,
        region_code: c.region_code,
        provider_reference: c.provider_reference,
      },
      { transaction }
    );
  });
  successHandler({ ctx, data: { received: true } });
});
export default router;
