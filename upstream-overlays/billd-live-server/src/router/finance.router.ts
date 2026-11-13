import Router from 'koa-router';
import axiosClient from 'axios';
import axios from '@/utils/request';
import { requireOperations } from '@/app/auth/operations';
import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';
import rechargeOrder from '@/model/rechargeOrder.model';
import User from '@/model/user.model';
import Attempt from '@/model/providerAttempt.model';

const router = new Router();
router.get('/operations/levels/settings', async ctx => {
  await requireOperations(ctx);
  await forward(ctx, '/v1/internal/business/levels', true);
});
router.put('/operations/levels/settings', async ctx => {
  const operator = await requireOperations(ctx);
  await forward(ctx, '/v1/internal/business/levels', true, {
    ...ctx.request.body, operator_id: Number(operator.id),
  });
});
// Claim the same unique business key as automatic execution before forwarding.
// An uncertain provider transfer must never race an external manual payment.
async function reserveManualPayment(ctx, kind: string, operator) {
  const body = ctx.request.body || {};
  if (body.action !== 'PAID') return;
  if (
    typeof body.request_id !== 'string' ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(
      body.request_id
    ) ||
    typeof body.reason !== 'string' ||
    !body.reason.trim() ||
    body.reason.length > 200 ||
    typeof body.payment_ref !== 'string' ||
    !body.payment_ref.trim() ||
    body.payment_ref.length > 128
  )
    throw new CustomError('登记参数无效', 400, 400);
  const [attempt]: any = await Attempt.findOrCreate({
    where: { business_key: `${kind}-${ctx.params.id}` },
    defaults: {
      operator_id: Number(operator.id),
      request_id: body.request_id,
      command_hash: 'manual',
      reason: body.reason,
      status: 'MANUAL',
      provider_ref: body.payment_ref,
    },
  });
  if (
    attempt.status !== 'MANUAL' ||
    attempt.request_id !== body.request_id ||
    attempt.provider_ref !== body.payment_ref ||
    attempt.reason !== body.reason ||
    Number(attempt.operator_id) !== Number(operator.id)
  )
    throw new CustomError(
      '该单已有付款流程，请先核实并用原操作单号重试，禁止重复付款',
      409,
      409
    );
}
router.get('/operations/admission', async (ctx) => {
  await requireOperations(ctx);
  await forward(ctx, '/v1/internal/admission', true);
});
router.put('/operations/admission', async (ctx) => {
  const operator = await requireOperations(ctx);
  await forward(ctx, '/v1/internal/admission', true, {
    ...ctx.request.body,
    operator_id: Number(operator.id),
  });
});
async function forward(ctx, path: string, internal: boolean, data?: any) {
  try {
    const result = await axios.request({
      url: `${REVENUE_API_URL}${path}`,
      method: ctx.method,
      params: ctx.method === 'GET' ? ctx.query : undefined,
      data,
      timeout: 10000,
      headers: internal
        ? { 'X-Internal-Key': INTERNAL_SERVICE_KEY }
        : { Authorization: ctx.headers.authorization || '' },
    });
    successHandler({ ctx, data: result });
  } catch (error) {
    if (!axiosClient.isAxiosError(error)) throw error;
    const status = error.response?.status;
    if (status && status >= 400 && status < 500)
      throw new CustomError(
        error.response?.data?.message || '请求未执行，请刷新核实',
        status,
        status
      );
    throw new CustomError(
      '资金服务暂不可用，操作结果未确认，请保留原单号核实',
      503,
      503
    );
  }
}
router.get('/operations/finance/settings', async (ctx) => {
  await requireOperations(ctx);
  await forward(ctx, '/v1/internal/finance/settings', true);
});
router.get('/operations/finance/audit', async (ctx) => {
  await requireOperations(ctx);
  await forward(ctx, '/v1/internal/finance/audit', true);
});
router.get('/operations/finance/manual-recharges', async (ctx) => {
  await requireOperations(ctx);
  await forward(ctx, '/v1/internal/finance/manual-recharges', true);
});
router.get('/operations/finance/recharges', async (ctx) => {
  await requireOperations(ctx);
  await forward(ctx, '/v1/internal/finance/recharges', true);
});
router.get('/operations/finance/balance-adjustments', async (ctx) => {
  await requireOperations(ctx);
  const userId = String(ctx.query.user_id || '');
  if (!/^[1-9][0-9]{0,9}$/.test(userId)) throw new CustomError('用户编号无效', 400, 400);
  await forward(ctx, '/v1/internal/finance/balance-adjustments', true);
});
router.post('/operations/finance/balance-adjustments', async (ctx) => {
  const operator = await requireOperations(ctx);
  const body = ctx.request.body || {};
  const userId = Number(body.user_id);
  const expected = Number(body.expected_balance);
  const target = Number(body.target_balance);
  const reason = typeof body.reason === 'string' ? body.reason.trim() : '';
  if (typeof body.request_id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(body.request_id)
    || !Number.isSafeInteger(userId) || userId < 1 || !Number.isSafeInteger(expected) || expected < 0
    || !Number.isSafeInteger(target) || target < 0 || target > 1000000000
    || Array.from(reason).length < 1 || Array.from(reason).length > 200)
    throw new CustomError('请填写有效的用户、余额、单号和调整原因', 400, 400);
  const user: any = await User.findByPk(userId);
  if (!user) throw new CustomError('用户不存在', 404, 404);
  await forward(ctx, '/v1/internal/finance/balance-adjustments', true, {
    request_id: body.request_id, user_id: userId, operator_id: Number(operator.id),
    expected_balance: expected, target_balance: target, reason,
  });
});
router.post('/operations/finance/manual-recharges', async (ctx) => {
  const operator = await requireOperations(ctx);
  const body = ctx.request.body || {};
  const userId = Number(body.user_id);
  const coins = Number(body.coins);
  const reason = typeof body.reason === 'string' ? body.reason.trim() : '';
  if (typeof body.request_id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(body.request_id)
    || !Number.isSafeInteger(userId) || userId < 1 || !Number.isInteger(coins) || coins < 1 || coins > 100000
    || Array.from(reason).length < 1 || Array.from(reason).length > 40)
    throw new CustomError('请填写有效的用户、金币、单号和入账原因', 400, 400);
  const user: any = await User.findByPk(userId);
  if (!user || user.status !== 1) throw new CustomError('用户不存在或不可用', 404, 404);
  await forward(ctx, '/v1/internal/finance/manual-recharges', true, {
    request_id: body.request_id, user_id: userId, coins, reason, operator_id: Number(operator.id),
  });
});
router.get('/revenue/recharges', async (ctx) => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id)
    throw new CustomError(auth.message || '请重新登录', auth.code, auth.code);
  await forward(ctx, '/v1/users/me/recharges', false);
});
router.get('/operations/gifts', async (ctx) => {
  await requireOperations(ctx);
  await forward(ctx, '/v1/internal/catalog', true);
});
router.put('/operations/gifts', async (ctx) => {
  const operator = await requireOperations(ctx);
  await forward(ctx, '/v1/internal/catalog', true, {
    ...ctx.request.body,
    operator_id: Number(operator.id),
  });
});
for (const [route, target] of [
  ['finance-rules', '/v1/finance/rules'],
  ['withdrawable', '/v1/creators/me/withdrawable'],
])
  router.get(`/revenue/${route}`, async (ctx) => {
    const auth = await authJwt(ctx);
    if (auth.code !== 200 || !auth.userInfo?.id)
      throw new CustomError(auth.message || '请重新登录', auth.code, auth.code);
    await forward(ctx, target, false);
  });
router.put('/operations/finance/settings', async (ctx) => {
  const operator = await requireOperations(ctx);
  await forward(ctx, '/v1/internal/finance/settings', true, {
    ...ctx.request.body,
    operator_id: Number(operator.id),
  });
});
router.get('/operations/finance/withdrawals', async (ctx) => {
  await requireOperations(ctx);
  await forward(ctx, '/v1/internal/finance/withdrawals', true);
});
router.post('/operations/finance/withdrawals/:id', async (ctx) => {
  const operator = await requireOperations(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id))
    throw new CustomError('提现单号无效', 400, 400);
  await reserveManualPayment(ctx, 'withdrawals', operator);
  await forward(
    ctx,
    `/v1/internal/finance/withdrawals/${ctx.params.id}`,
    true,
    { ...ctx.request.body, operator_id: Number(operator.id) }
  );
});
for (const method of ['get', 'post'] as const)
  router[method]('/revenue/withdrawals', async (ctx) => {
    const auth = await authJwt(ctx);
    if (auth.code !== 200 || !auth.userInfo?.id)
      throw new CustomError(auth.message || '请重新登录', auth.code, auth.code);
    await forward(
      ctx,
      '/v1/creators/me/withdrawals',
      false,
      method === 'post' ? ctx.request.body : undefined
    );
  });
router.get('/operations/finance/refunds', async (ctx) => {
  await requireOperations(ctx);
  await forward(ctx, '/v1/internal/finance/refunds', true);
});
router.post('/operations/finance/refunds/:id', async (ctx) => {
  const operator = await requireOperations(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id))
    throw new CustomError('退款单号无效', 400, 400);
  await reserveManualPayment(ctx, 'refunds', operator);
  await forward(ctx, `/v1/internal/finance/refunds/${ctx.params.id}`, true, {
    ...ctx.request.body,
    operator_id: Number(operator.id),
  });
});
router.get('/revenue/refunds', async (ctx) => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id)
    throw new CustomError(auth.message || '请重新登录', auth.code, auth.code);
  await forward(ctx, '/v1/users/me/refunds', false);
});
router.post('/revenue/refunds', async (ctx) => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id)
    throw new CustomError(auth.message || '请重新登录', auth.code, auth.code);
  const body = ctx.request.body || {};
  if (
    typeof body.recharge_request_id !== 'string' ||
    body.recharge_request_id.length > 64
  )
    throw new CustomError('充值订单无效', 400, 400);
  const order: any = await rechargeOrder.findOne({
    where: {
      request_id: body.recharge_request_id,
      user_id: auth.userInfo.id,
      status: 'CREDITED',
    },
  });
  if (!order)
    throw new CustomError('尚未确认该充值到账，不能申请退款', 409, 409);
  await forward(ctx, '/v1/internal/finance/refunds', true, {
    request_id: body.request_id,
    user_id: Number(auth.userInfo.id),
    recharge_request_id: order.request_id,
    amount_fen: Number(order.amount_fen),
    reason: body.reason,
  });
});
for (const kind of ['creators', 'reports']) {
  router.get(`/operations/${kind}`, async (ctx) => {
    await requireOperations(ctx);
    await forward(ctx, `/v1/internal/reviews/${kind}`, true);
  });
  router.post(`/operations/${kind}/:id`, async (ctx) => {
    const operator = await requireOperations(ctx);
    if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id))
      throw new CustomError('申请单号无效', 400, 400);
    await forward(ctx, `/v1/internal/reviews/${kind}/${ctx.params.id}`, true, {
      ...ctx.request.body,
      operator_id: Number(operator.id),
    });
  });
}
for (const kind of ['creator-applications', 'reports'])
  for (const method of ['get', 'post'] as const)
    router[method](`/revenue/${kind}`, async (ctx) => {
      const auth = await authJwt(ctx);
      if (auth.code !== 200 || !auth.userInfo?.id)
        throw new CustomError(
          auth.message || '请重新登录',
          auth.code,
          auth.code
        );
      await forward(
        ctx,
        `/v1/users/me/${kind}`,
        false,
        method === 'post' ? ctx.request.body : undefined
      );
    });
export default router;
