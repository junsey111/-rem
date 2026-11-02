import Router from 'koa-router';
import { authJwt } from '@/app/auth/authJwt';
import { canManageOperations, requireOperations } from '@/app/auth/operations';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import userModel from '@/model/user.model';
import liveModel from '@/model/live.model';
import rechargeOrder from '@/model/rechargeOrder.model';
import axiosClient from 'axios';
import axios from '@/utils/request';
import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';

const router = new Router({ prefix: '/operations' });

async function operationsRevenue(path: string, method = 'GET', data?: any) {
  try {
    const options = { headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY }, timeout: 10000 };
    return method === 'POST' ? await axios.post(`${REVENUE_API_URL}/v1/internal/operations/${path}`, data, options)
      : await axios.get(`${REVENUE_API_URL}/v1/internal/operations/${path}`, options);
  } catch (error) {
    if (!axiosClient.isAxiosError(error)) throw error;
    const code = error.response?.data?.code;
    const messages = {
      INVALID_CURSOR: '记录分页已失效，请刷新', INVALID_OPERATION: '操作信息有误，请检查原因和单号',
      OPERATION_CONFLICT: '该单号已用于其他操作，请核对原操作记录',
      LIVE_SESSION_NOT_FOUND: '直播记录已不存在，请刷新', OPERATION_NOT_FOUND: '尚未查到已确认操作，请继续核实原单号',
    };
    const message = messages[code];
    if (message && error.response?.status && error.response.status < 500) throw new CustomError(message, error.response.status, error.response.status);
    throw new CustomError('操作服务暂时不可用，结果未确认时请核实原单号', 503, 503);
  }
}

for (const kind of ['lives', 'actions']) {
  router.get(`/${kind}`, async ctx => {
    await requireOperations(ctx);
    const cursor = ctx.query.before_id;
    if (cursor !== undefined && (typeof cursor !== 'string' || !/^[1-9][0-9]{0,19}$/.test(cursor))) throw new CustomError('分页无效，请刷新',400,400);
    const result = await operationsRevenue(kind + (cursor ? `?before_id=${cursor}` : ''));
    successHandler({ ctx, data: result });
  });
}

router.get('/actions/:requestId', async ctx => {
  await requireOperations(ctx);
  successHandler({ ctx, data: await operationsRevenue(`actions/${encodeURIComponent(ctx.params.requestId)}`) });
});

router.post('/lives/:id/close', async ctx => {
  const operator = await requireOperations(ctx);
  const id = ctx.params.id;
  const { request_id: requestId, reason } = ctx.request.body || {};
  if (!/^[1-9][0-9]{0,19}$/.test(id) || BigInt(id) > 18446744073709551615n
    || typeof requestId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(requestId)
    || typeof reason !== 'string' || !reason.trim() || Array.from(reason).length > 200) {
    ctx.status = 400;
    ctx.body = { code: 400, moderation_outcome: 'REJECTED', message: '操作未执行，请填写有效的直播、单号和 1 至 200 字操作原因' };
    return;
  }
  try {
    const result = await operationsRevenue(`lives/${id}/close`, 'POST', { request_id: requestId, reason, operator_id: Number(operator.id) });
    successHandler({ ctx, data: result });
  } catch (error) {
    if (!(error instanceof CustomError) || ![400,404].includes(error.statusCode)) throw error;
    ctx.status = error.statusCode;
    ctx.body = { code: error.errorCode, moderation_outcome: 'REJECTED', message: error.message };
  }
});

router.get('/access', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code, code);
  successHandler({ ctx, data: { can_manage: await canManageOperations(Number(userInfo.id)) } });
});

router.get('/summary', async ctx => {
  await requireOperations(ctx);
  const [users, disabledUsers, listedRooms, pendingOrders, creditedOrders] = await Promise.all([
    userModel.count(), userModel.count({ where: { status: 2 } }), liveModel.count(),
    rechargeOrder.count({ where: { status: 'PENDING' } }), rechargeOrder.count({ where: { status: 'CREDITED' } }),
  ]);
  successHandler({ ctx, data: { users, disabled_users: disabledUsers, listed_rooms: listedRooms,
    pending_orders: pendingOrders, credited_orders: creditedOrders, observed_at: new Date().toISOString() } });
});

router.get('/users', async ctx => {
  await requireOperations(ctx);
  const raw = String(ctx.query.page || '1');
  if (!/^[1-9][0-9]{0,5}$/.test(raw)) throw new CustomError('页码无效', 400, 400);
  const page = Number(raw), pageSize = 25;
  const result = await userModel.findAndCountAll({
    attributes: ['id', 'username', 'status', 'created_at'], order: [['id', 'DESC']],
    offset: (page - 1) * pageSize, limit: pageSize,
  });
  successHandler({ ctx, data: { users: result.rows, total: result.count, page, page_size: pageSize } });
});

// Recharge records are read from the payment gateway, with no payment credentials exposed.
router.get('/orders', async ctx => {
  await requireOperations(ctx);
  const raw=String(ctx.query.page || '1');
  if(!/^[1-9][0-9]{0,5}$/.test(raw)) throw new CustomError('页码无效',400,400);
  const page=Number(raw),pageSize=25;
  const result=await rechargeOrder.findAndCountAll({attributes:['request_id','user_id','coins','amount_fen','status','created_at','last_checked_at'],order:[['created_at','DESC'],['request_id','DESC']],offset:(page-1)*pageSize,limit:pageSize});
  successHandler({ctx,data:{orders:result.rows,total:result.count,page,page_size:pageSize}});
});

export default router;
