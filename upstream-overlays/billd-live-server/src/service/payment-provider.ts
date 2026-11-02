import AlipaySdk from 'alipay-sdk';
import { ALIPAY_CONFIG } from '@/config/secret';
import { CustomError } from '@/model/customError.model';
export const paymentProviderEnabled = () =>
  process.env.ALIPAY_DISBURSEMENTS_ENABLED === 'true' &&
  !!ALIPAY_CONFIG.appId &&
  !!ALIPAY_CONFIG.privateKey &&
  !!ALIPAY_CONFIG.alipayPublicKey;
const amount = (fen: number) => {
  if (!Number.isSafeInteger(fen) || fen < 1 || fen > 100000000)
    throw new CustomError('金额无效', 400, 400);
  return `${Math.floor(fen / 100)}.${String(fen % 100).padStart(2, '0')}`;
};
const client = () =>
  new AlipaySdk({
    ...ALIPAY_CONFIG,
    timeout: 8000,
    camelcase: false,
    keyType: ALIPAY_CONFIG.privateKey.includes('BEGIN PRIVATE KEY')
      ? 'PKCS8'
      : 'PKCS1',
  });
// Stable provider business keys survive process restarts and ambiguous network replies.
export async function executeAlipay(
  kind: 'refunds' | 'withdrawals',
  key: string,
  subject: any,
  order?: any
): Promise<string> {
  if (!paymentProviderEnabled())
    throw new CustomError(
      '真实资金接口暂未启用；此请求不会发起打款或退款',
      503,
      503
    );
  const sdk = client();
  if (kind === 'refunds') {
    if (
      !order?.payment_ref ||
      Number(order.user_id) !== Number(subject.user_id) ||
      Number(order.amount_fen) !== Number(subject.amount_fen)
    )
      throw new CustomError('原充值信息不匹配', 409, 409);
    const money = amount(Number(subject.amount_fen));
    const query: any = await sdk.exec(
      'alipay.trade.fastpay.refund.query',
      { bizContent: { out_trade_no: order.request_id, out_request_no: key } },
      { validateSign: true }
    );
    if (
      query.code === '10000' &&
      query.refund_status === 'REFUND_SUCCESS' &&
      query.out_request_no === key &&
      query.trade_no === order.payment_ref &&
      query.refund_amount === money
    )
      return `${query.trade_no}:${key}`;
    if (
      query.code === '10000' &&
      query.refund_status &&
      query.refund_status !== 'REFUND_SUCCESS'
    )
      throw new CustomError('退款仍在渠道处理中，请用原单号核实', 503, 503);
    if (query.code === '10000' && query.refund_status === 'REFUND_SUCCESS')
      throw new CustomError('渠道退款记录与原单不匹配，请人工核实', 409, 409);
    if (
      query.code !== '10000' &&
      !(
        query.code === '40004' &&
        ['ACQ.TRADE_NOT_EXIST', 'ACQ.REFUND_NOT_EXIST'].includes(query.sub_code)
      )
    )
      throw new CustomError('渠道退款查单尚未确认', 503, 503);
    const result: any = await sdk.exec(
      'alipay.trade.refund',
      {
        bizContent: {
          out_trade_no: order.request_id,
          trade_no: order.payment_ref,
          refund_amount: money,
          out_request_no: key,
          refund_reason: subject.reason,
        },
      },
      { validateSign: true }
    );
    if (
      result.code !== '10000' ||
      result.trade_no !== order.payment_ref ||
      result.refund_fee !== money
    )
      throw new CustomError('渠道未确认退款，请使用原业务单号核实', 503, 503);
    return `${result.trade_no}:${key}`;
  }
  if (subject.payout_method !== 'alipay')
    throw new CustomError(
      '自动打款仅支持支付宝，其他方式使用人工核实登记',
      409,
      409
    );
  const query: any = await sdk.exec(
    'alipay.fund.trans.common.query',
    {
      bizContent: {
        out_biz_no: key,
        product_code: 'TRANS_ACCOUNT_NO_PWD',
        biz_scene: 'DIRECT_TRANSFER',
      },
    },
    { validateSign: true }
  );
  if (
    query.code === '10000' &&
    query.status === 'SUCCESS' &&
    query.out_biz_no === key &&
    query.order_id
  )
    return String(query.order_id);
  if (query.code === '10000')
    throw new CustomError(
      '渠道转账尚未成功，请用原单号核实，勿再次人工打款',
      503,
      503
    );
  if (
    query.code !== '40004' ||
    !['ORDER_NOT_EXIST', 'BIZ_ORDER_NOT_EXIST'].includes(query.sub_code)
  )
    throw new CustomError('渠道转账查单未确认', 503, 503);
  const result: any = await sdk.exec(
    'alipay.fund.trans.uni.transfer',
    {
      bizContent: {
        out_biz_no: key,
        trans_amount: amount(Number(subject.payout_fen)),
        product_code: 'TRANS_ACCOUNT_NO_PWD',
        biz_scene: 'DIRECT_TRANSFER',
        order_title: '主播收益提现',
        payee_info: {
          identity: subject.payout_account,
          identity_type: 'ALIPAY_LOGON_ID',
          name: subject.payout_name,
        },
      },
    },
    { validateSign: true }
  );
  if (
    result.code !== '10000' ||
    result.status !== 'SUCCESS' ||
    result.out_biz_no !== key ||
    !result.order_id
  )
    throw new CustomError(
      '渠道未确认打款，请用原单号核实，勿再次人工打款',
      503,
      503
    );
  return String(result.order_id);
}
