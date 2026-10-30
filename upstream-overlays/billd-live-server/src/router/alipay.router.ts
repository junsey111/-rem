import crypto from 'crypto';
import AlipaySdk from 'alipay-sdk';
import Router from 'koa-router';

import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { ALIPAY_CONFIG, PAYMENT_WEBHOOK_SECRET, PUBLIC_BASE_URL, REVENUE_API_URL } from '@/config/secret';
import { ALLOW_HTTP_CODE } from '@/constant';
import { CustomError } from '@/model/customError.model';
import axios from '@/utils/request';

const alipayRouter = new Router({ prefix: '/alipay' });

const createSdk = () => new AlipaySdk({
  appId: ALIPAY_CONFIG.appId,
  privateKey: ALIPAY_CONFIG.privateKey,
  alipayPublicKey: ALIPAY_CONFIG.alipayPublicKey,
});

const signRecharge = (payload: string) => crypto
  .createHmac('sha256', PAYMENT_WEBHOOK_SECRET)
  .update(payload)
  .digest()
  .toString('base64url');

alipayRouter.get('/recharge', async (ctx, next) => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== ALLOW_HTTP_CODE.ok || !userInfo?.id) {
    throw new CustomError(message || '未登录！', code, code);
  }
  const coins = Number(ctx.query.coins);
  if (!Number.isInteger(coins) || coins < 1 || coins > 100000) {
    throw new CustomError('coins参数必须在1到100000之间！', ALLOW_HTTP_CODE.paramsError, ALLOW_HTTP_CODE.paramsError);
  }
  const requestId = `recharge-${userInfo.id}-${coins}-${Date.now()}-${crypto.randomInt(1000)}`;
  const result = createSdk().pageExec('alipay.trade.page.pay', {
    method: 'GET',
    notifyUrl: `${PUBLIC_BASE_URL}/alipay/notify`,
    returnUrl: `${PUBLIC_BASE_URL}/alipay/success`,
    bizContent: {
      out_trade_no: requestId,
      total_amount: (coins / 100).toFixed(2),
      subject: `直播金币${coins}`,
      product_code: 'FAST_INSTANT_TRADE_PAY',
    },
  });
  successHandler({ ctx, data: { requestId, coins, payUrl: result } });
  await next();
});

alipayRouter.post('/notify', async (ctx) => {
  const postData = ctx.request.body || {};
  const sdk = createSdk();
  if (!sdk.checkNotifySign(postData)) {
    ctx.body = 'fail';
    return;
  }
  if (!['TRADE_SUCCESS', 'TRADE_FINISHED'].includes(postData.trade_status)) {
    ctx.body = 'success';
    return;
  }
  const match = String(postData.out_trade_no || '').match(/^recharge-(\d+)-(\d+)-/);
  if (!match || !PAYMENT_WEBHOOK_SECRET) {
    ctx.body = 'fail';
    return;
  }
  const viewerId = Number(match[1]);
  const coins = Number(match[2]);
  if (Math.round(Number(postData.total_amount) * 100) !== coins) {
    ctx.body = 'fail';
    return;
  }
  const paymentRef = String(postData.trade_no || postData.out_trade_no);
  const payload = `${postData.out_trade_no}:${viewerId}:${coins}:${paymentRef}`;
  try {
    await axios.post(`${REVENUE_API_URL}/v1/wallets/recharges`, {
      request_id: String(postData.out_trade_no),
      viewer_id: viewerId,
      coins,
      payment_ref: paymentRef,
    }, { headers: { 'X-Payment-Signature': signRecharge(payload) } });
    ctx.body = 'success';
  } catch (error) {
    console.error('revenue recharge callback failed', error);
    ctx.body = 'fail';
  }
});

alipayRouter.get('/success', async (ctx, next) => {
  successHandler({ ctx, data: 'successsuccess' });
  await next();
});

export default alipayRouter;
