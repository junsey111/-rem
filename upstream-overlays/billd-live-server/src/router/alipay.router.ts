import crypto from 'crypto';
import AlipaySdk from 'alipay-sdk';
import Router from 'koa-router';
import { Op } from 'sequelize';

import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { ALIPAY_CONFIG, PAYMENT_WEBHOOK_SECRET, PUBLIC_BASE_URL, REVENUE_API_URL } from '@/config/secret';
import { ALLOW_HTTP_CODE } from '@/constant';
import { CustomError } from '@/model/customError.model';
import rechargeOrder from '@/model/rechargeOrder.model';
import axios from '@/utils/request';

const alipayRouter = new Router({ prefix: '/alipay' });
const configured = () => process.env.ALIPAY_RECHARGE_ENABLED === 'true' && ALIPAY_CONFIG.appId && ALIPAY_CONFIG.privateKey && ALIPAY_CONFIG.alipayPublicKey && PAYMENT_WEBHOOK_SECRET;
const createSdk = () => new AlipaySdk({
  ...ALIPAY_CONFIG,
  timeout: 8_000,
  camelcase: false,
  keyType: ALIPAY_CONFIG.privateKey.includes('BEGIN PRIVATE KEY') ? 'PKCS8' : 'PKCS1',
});

async function requireUser(ctx) {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== ALLOW_HTTP_CODE.ok || !userInfo?.id) throw new CustomError(message || '请先登录', code, code);
  return userInfo;
}
function publicOrder(order: any) {
  return { requestId: order.request_id, coins: Number(order.coins), amountFen: Number(order.amount_fen), status: order.status };
}

async function creditPayment(order: any, data: any): Promise<boolean> {
  const amount = String(data.total_amount || '');
  if (!/^\d{1,8}\.\d{2}$/.test(amount) || !/^\d{1,128}$/.test(String(data.trade_no || ''))) return false;
  const [yuan, fen] = amount.split('.');
  if (Number(order.amount_fen) !== Number(yuan) * 100 + Number(fen)) return false;
  if (order.payment_ref && order.payment_ref !== data.trade_no) return false;
  const payload = `${order.request_id}:${order.user_id}:${order.coins}:${data.trade_no}`;
  const signature = crypto.createHmac('sha256', PAYMENT_WEBHOOK_SECRET).update(payload).digest('base64url');
  await axios.post(`${REVENUE_API_URL}/v1/wallets/recharges`, {
    request_id: order.request_id, viewer_id: Number(order.user_id), coins: Number(order.coins), payment_ref: data.trade_no,
  }, { headers: { 'X-Payment-Signature': signature } });
  await order.update({ status: 'CREDITED', payment_ref: data.trade_no });
  return true;
}

async function reconcilePayment(order: any) {
  if (order.status !== 'PENDING' || !configured()) return;
  // Claim a persisted 30-second window across processes, not a per-process cache.
  const [claimed] = await rechargeOrder.update({ last_checked_at: new Date() }, { where: {
    request_id: order.request_id, status: 'PENDING',
    [Op.or]: [{ last_checked_at: null }, { last_checked_at: { [Op.lt]: new Date(Date.now() - 30_000) } }],
  } });
  if (!claimed) return;
  try {
    const result: any = await createSdk().exec('alipay.trade.query', {
      bizContent: { out_trade_no: order.request_id },
    }, { validateSign: true });
    if (result.code === '10000' && result.out_trade_no === order.request_id
      && ['TRADE_SUCCESS', 'TRADE_FINISHED'].includes(result.trade_status)) {
      await creditPayment(order, result);
    }
  } catch (_) {
    // Keep PENDING on timeout, invalid signature, or ledger failure; next check can recover.
  }
}

// The persisted claim window is shared with user lookups and every worker instance.
export function startRechargeReconciliation(server) {
  let stopped=false;
  let timer: ReturnType<typeof setTimeout>;
  const run=async()=>{
    try {
      if(configured()) {
        const orders=await rechargeOrder.findAll({where:{status:'PENDING',
          [Op.or]:[{last_checked_at:null},{last_checked_at:{[Op.lt]:new Date(Date.now()-30_000)}}]},
          order:[['last_checked_at','ASC'],['created_at','ASC']],limit:25});
        for(const order of orders){if(stopped)break;await reconcilePayment(order);}
      }
    } catch {console.error('Recharge reconciliation incomplete; will retry');}
    if(!stopped){timer=setTimeout(run,30_000);timer.unref();}
  };
  void run();
  const stop=()=>{stopped=true;clearTimeout(timer);};server.once('close',stop);return stop;
}
alipayRouter.get('/recharge', async (ctx, next) => {
  const user = await requireUser(ctx);
  if (!configured()) throw new CustomError('充值暂未开通，请稍后再试', 503, 503);
  const coins = Number(ctx.query.coins);
  if (![100, 500, 1000].includes(coins)) throw new CustomError('请选择有效充值档位', 400, 400);
  const requestId = `recharge-${crypto.randomUUID()}`;
  const payUrl = createSdk().pageExec('alipay.trade.wap.pay', {
    method: 'GET', notifyUrl: `${PUBLIC_BASE_URL}/alipay/notify`, returnUrl: `${PUBLIC_BASE_URL}/alipay/success`,
    bizContent: { out_trade_no: requestId, total_amount: (coins / 100).toFixed(2), subject: `直播金币${coins}`, product_code: 'QUICK_WAP_WAY' },
  });
  const order = await rechargeOrder.create({ request_id: requestId, user_id: user.id, coins, amount_fen: coins, status: 'PENDING' });
  successHandler({ ctx, data: { ...publicOrder(order), payUrl } });
  await next();
});
alipayRouter.get('/orders', async (ctx, next) => {
  const user = await requireUser(ctx);
  const orders = await rechargeOrder.findAll({ where: { user_id: user.id }, order: [['created_at', 'DESC']], limit: 20 });
  await Promise.all(orders.map(async order => { await reconcilePayment(order); await order.reload(); }));
  successHandler({ ctx, data: { orders: orders.map(publicOrder) } });
  await next();
});
alipayRouter.get('/orders/:id', async (ctx, next) => {
  const user = await requireUser(ctx);
  const order = await rechargeOrder.findOne({ where: { request_id: ctx.params.id, user_id: user.id } });
  if (!order) throw new CustomError('充值订单不存在', 404, 404);
  await reconcilePayment(order);
  await order.reload();
  successHandler({ ctx, data: publicOrder(order) });
  await next();
});
alipayRouter.post('/notify', async (ctx) => {
  ctx.body = 'fail';
  if (!configured()) return;
  const data = ctx.request.body || {};
  if (!createSdk().checkNotifySign(data) || data.app_id !== ALIPAY_CONFIG.appId) return;
  if (!['TRADE_SUCCESS', 'TRADE_FINISHED'].includes(data.trade_status)) { ctx.body = 'success'; return; }
  try {
    const order: any = await rechargeOrder.findByPk(String(data.out_trade_no || ''));
    if (order && await creditPayment(order, data)) ctx.body = 'success';
  } catch (_) {
    console.error('Recharge not confirmed; provider may retry notification');
  }
});
alipayRouter.get('/success', async (ctx) => {
  // A browser return never proves payment.
  ctx.type = 'html';
  ctx.body = '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>充值结果</title><body><main><h1>请返回 App 查看到账结果</h1><p>支付结果正在核实，是否到账以 App 中的充值订单为准。请勿重复付款。</p></main></body></html>';
});
export default alipayRouter;
