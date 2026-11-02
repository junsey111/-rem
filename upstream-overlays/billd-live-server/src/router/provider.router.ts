import Router from 'koa-router';
import crypto from 'crypto';
import { requireOperations } from '@/app/auth/operations';
import { CustomError } from '@/model/customError.model';
import Attempt from '@/model/providerAttempt.model';
import Order from '@/model/rechargeOrder.model';
import axios from '@/utils/request';
import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';
import {
  executeAlipay,
  paymentProviderEnabled,
} from '@/service/payment-provider';
import successHandler from '@/app/handler/success-handle';
const router = new Router();
router.get('/operations/integrations', async (ctx) => {
  await requireOperations(ctx);
  successHandler({
    ctx,
    data: {
      payments: { provider: 'alipay', enabled: paymentProviderEnabled() },
      identity: {
        provider: 'external_signed_adapter',
        enabled:
          process.env.IDENTITY_ENABLED === 'true' &&
          !!process.env.IDENTITY_PROVIDER_URL &&
          !!process.env.IDENTITY_PROVIDER_KEY,
      },
      message: '接口默认关闭，不会模拟支付或实名成功',
    },
  });
});
router.post('/operations/finance/:kind/:id/execute', async (ctx) => {
  const operator = await requireOperations(ctx);
  const { kind, id } = ctx.params;
  const body = ctx.request.body || {};
  if (
    !['refunds', 'withdrawals'].includes(kind) ||
    !/^[1-9][0-9]{0,19}$/.test(id) ||
    typeof body.reason !== 'string' ||
    !body.reason.trim() ||
    body.reason.length > 200 ||
    typeof body.request_id !== 'string' ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(
      body.request_id
    )
  )
    throw new CustomError('操作参数无效', 400, 400);
  if (!paymentProviderEnabled())
    throw new CustomError(
      '真实资金接口暂未启用；此请求不会发起打款或退款',
      503,
      503
    );
  const headers = { 'X-Internal-Key': INTERNAL_SERVICE_KEY };
  const subject: any = await axios.get(
    `${REVENUE_API_URL}/v1/internal/finance/${kind}/${id}`,
    { headers, timeout: 5000 }
  );
  if (subject.status !== 'APPROVED' && subject.status !== 'PAID')
    throw new CustomError('仅审核通过的申请可执行', 409, 409);
  const key = `${kind}-${id}`;
  const hash = crypto
    .createHash('sha256')
    .update(
      JSON.stringify({
        kind,
        id,
        user: subject.user_id,
        amount: subject.payout_fen || subject.amount_fen,
        account: subject.payout_account || subject.recharge_request_id,
      })
    )
    .digest('hex');
  const [attempt]: any = await Attempt.findOrCreate({
    where: { business_key: key },
    defaults: {
      operator_id: Number(operator.id),
      request_id: body.request_id,
      command_hash: hash,
      reason: body.reason.trim(),
    },
  });
  if (attempt.command_hash !== hash)
    throw new CustomError('原申请信息发生变化，请人工核实', 409, 409);
  if (subject.status === 'PAID') {
    if (
      attempt.status === 'PAID' &&
      attempt.provider_ref === subject.payment_ref
    ) {
      successHandler({
        ctx,
        data: { status: 'PAID', payment_ref: attempt.provider_ref },
      });
      return;
    }
    throw new CustomError('该单已由其他流程登记支付，禁止再次发起', 409, 409);
  }
  const order =
    kind === 'refunds'
      ? await Order.findOne({
          where: {
            request_id: subject.recharge_request_id,
            status: 'CREDITED',
          },
        })
      : null;
  const ref =
    attempt.provider_ref ||
    (await executeAlipay(kind as any, key, subject, order));
  await attempt.update({ provider_ref: ref, status: 'PROVIDER_CONFIRMED' });
  const result = await axios.post(
    `${REVENUE_API_URL}/v1/internal/finance/${kind}/${id}`,
    {
      request_id: attempt.request_id,
      operator_id: Number(attempt.operator_id),
      action: 'PAID',
      reason: attempt.reason,
      payment_ref: ref,
    },
    { headers, timeout: 10000 }
  );
  await attempt.update({ status: 'PAID' });
  successHandler({ ctx, data: result });
});
export default router;
