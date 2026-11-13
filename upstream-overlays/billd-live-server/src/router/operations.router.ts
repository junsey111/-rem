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
import { Op, fn, col, literal } from 'sequelize';
import fs from 'fs';
import { Moment, MomentMedia, MomentLike, MomentComment } from '@/model/moment.model';
import MomentModeration from '@/model/momentModeration.model';
import sequelize from '@/config/mysql';
import MomentReview from '@/model/momentReview.model';
import { approvedMomentSql, momentReviewFields } from '@/service/moment-review';

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

function rangeFromQuery(ctx) {
  const start = String(ctx.query.start || '');
  const end = String(ctx.query.end || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(start) || !/^\d{4}-\d{2}-\d{2}$/.test(end))
    throw new CustomError('请选择有效的开始和结束日期', 400, 400);
  const from = new Date(`${start}T00:00:00+08:00`);
  const until = new Date(`${end}T00:00:00+08:00`);
  until.setUTCDate(until.getUTCDate() + 1);
  if (!Number.isFinite(from.getTime()) || !Number.isFinite(until.getTime()) || from >= until || until.getTime() - from.getTime() > 366 * 86400000)
    throw new CustomError('日期范围不能超过一年', 400, 400);
  return { from, until };
}

router.get('/dashboard', async ctx => {
  await requireOperations(ctx);
  const { from, until } = rangeFromQuery(ctx);
  const now = new Date();
  const today = new Date(now.toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' }) + 'T00:00:00+08:00');
  const week = new Date(today);
  week.setUTCDate(week.getUTCDate() - ((today.getUTCDay() + 6) % 7));
  const [total, todayUsers, weekUsers, rangeUsers, credited] = await Promise.all([
    userModel.count(),
    userModel.count({ where: { created_at: { [Op.gte]: today } } }),
    userModel.count({ where: { created_at: { [Op.gte]: week } } }),
    userModel.count({ where: { created_at: { [Op.gte]: from, [Op.lt]: until } } }),
    rechargeOrder.findAll({ attributes: [[fn('COALESCE', fn('SUM', col('amount_fen')), 0), 'amount_fen']], where: { status: 'CREDITED', created_at: { [Op.gte]: from, [Op.lt]: until } }, raw: true }),
  ]);
  const revenue: any = await axios.get(`${REVENUE_API_URL}/v1/internal/admin-insights/dashboard`, {
    headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY },
    params: { from: from.toISOString(), until: until.toISOString() }, timeout: 10000,
  });
  let downloads: any = null;
  const statsFile = process.env.APP_DOWNLOAD_STATS_FILE;
  if (statsFile && fs.existsSync(statsFile)) {
    const recorded = JSON.parse(fs.readFileSync(statsFile, 'utf8'));
    const daily = recorded.daily || {};
    const count = (since: Date, before?: Date) => Object.entries(daily).reduce((sum, [day, value]) => {
      const date = new Date(`${day}T00:00:00+08:00`);
      return date >= since && (!before || date < before) ? sum + Number(value || 0) : sum;
    }, 0);
    downloads = { today: count(today), week: count(week), total: Object.values(daily).reduce((sum: number, value) => sum + Number(value || 0), 0),
      selected: count(from, until), since: recorded.since };
  }
  successHandler({ ctx, data: { users: { today: todayUsers, week: weekUsers, total, selected: rangeUsers },
    selected_recharge_fen: Number((credited[0] as any)?.amount_fen || 0),
    gift_coins: revenue.gift_coins, downloads, download_note: downloads ? '仅统计从启用记录起经本地更新服务完成的 APK 下载' : '下载记录尚未产生，不包含历史下载或应用商店安装',
    from: from.toISOString(), until: until.toISOString() } });
});

router.get('/users', async ctx => {
  await requireOperations(ctx);
  const raw = String(ctx.query.page || '1');
  if (!/^[1-9][0-9]{0,5}$/.test(raw)) throw new CustomError('页码无效', 400, 400);
  const page = Number(raw), pageSize = 25;
  const search = String(ctx.query.search || '').trim();
  if (search.length > 40) throw new CustomError('搜索内容过长', 400, 400);
  const escaped = search.replace(/[\\%_]/g, '\\$&');
  const where: any = search ? { [Op.or]: [
    { username: { [Op.like]: `%${escaped}%` } },
    ...(Number.isSafeInteger(Number(search)) && Number(search) > 0 ? [{ id: Number(search) }, { public_id: Number(search) }] : []),
  ] } : {};
  const result = await userModel.findAndCountAll({
    attributes: ['id', 'public_id', 'username', 'avatar', 'status', 'created_at'], where, order: [['id', 'DESC']],
    offset: (page - 1) * pageSize, limit: pageSize,
  });
  successHandler({ ctx, data: { users: result.rows, total: result.count, page, page_size: pageSize } });
});

router.get('/moments', async ctx => {
  await requireOperations(ctx);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页游标无效', 400, 400);
  const where: any = before ? { id: { [Op.lt]: before } } : {};
  const status = String(ctx.query.review_status || 'ALL');
  if (!['ALL', 'PENDING', 'APPROVED', 'REJECTED'].includes(status)) throw new CustomError('审核状态无效', 400, 400);
  if (status !== 'ALL') where[Op.and] = [literal(status === 'APPROVED' ? approvedMomentSql
    : `EXISTS (SELECT 1 FROM moment_review review WHERE review.moment_id = user_moment.id AND review.status = '${status}')`)];
  const items: any[] = await Moment.findAll({ where, attributes: ['id', 'user_id', 'content', 'visible', 'hidden', 'created_at'],
    order: [['id', 'DESC']], limit: 26, raw: true });
  const page = items.slice(0, 25);
  const ids = page.map(item => item.id);
  const [authors, media, likes, comments, reviews] = await Promise.all([
    userModel.findAll({ where: { id: { [Op.in]: [...new Set(page.map(item => item.user_id))] } }, attributes: ['id', 'username'], raw: true }),
    MomentMedia.findAll({ where: { moment_id: { [Op.in]: ids } }, attributes: ['moment_id', 'media_kind'], raw: true }),
    MomentLike.findAll({ where: { moment_id: { [Op.in]: ids } }, attributes: ['moment_id', [fn('COUNT', col('moment_id')), 'count']], group: ['moment_id'], raw: true }),
    MomentComment.findAll({ where: { moment_id: { [Op.in]: ids }, visible: true }, attributes: ['moment_id', [fn('COUNT', col('moment_id')), 'count']], group: ['moment_id'], raw: true }),
    MomentReview.findAll({ where: { moment_id: { [Op.in]: ids } }, raw: true }),
  ]);
  const names = new Map(authors.map((row: any) => [Number(row.id), row.username]));
  const kinds = new Map(media.map((row: any) => [String(row.moment_id), row.media_kind]));
  const likeCounts = new Map(likes.map((row: any) => [String(row.moment_id), Number(row.count)]));
  const commentCounts = new Map(comments.map((row: any) => [String(row.moment_id), Number(row.count)]));
  const reviewById = new Map(reviews.map((row: any) => [String(row.moment_id), row]));
  successHandler({ ctx, data: { moments: page.map(row => ({ ...row, id: String(row.id), username: names.get(Number(row.user_id)) || '已注销用户',
    media_kind: kinds.get(String(row.id)) || null, like_count: likeCounts.get(String(row.id)) || 0,
    comment_count: commentCounts.get(String(row.id)) || 0, ...momentReviewFields(reviewById.get(String(row.id))) })), next_before_id: items.length > 25 ? String(page[24].id) : '' } });
});

router.post('/moments/:id/hide', async ctx => {
  const operator = await requireOperations(ctx);
  const id = ctx.params.id;
  const requestId = ctx.request.body?.request_id;
  if (!/^[1-9][0-9]{0,19}$/.test(id) || typeof ctx.request.body?.reason !== 'string'
    || !ctx.request.body.reason.trim() || Array.from(ctx.request.body.reason).length > 200
    || typeof requestId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(requestId))
    throw new CustomError('请填写有效动态编号和处理原因', 400, 400);
  await sequelize.transaction(async transaction => {
    const previous: any = await MomentModeration.findOne({ where: { request_id: requestId }, transaction });
    if (previous) {
      if (String(previous.moment_id) !== id || Number(previous.operator_id) !== Number(operator.id)
        || previous.reason !== ctx.request.body.reason.trim()) throw new CustomError('操作单号已使用', 409, 409);
      return;
    }
    const moment: any = await Moment.findByPk(id, { transaction, lock: transaction.LOCK.UPDATE });
    if (!moment) throw new CustomError('动态不存在', 404, 404);
    await moment.update({ visible: false }, { transaction });
    await MomentModeration.create({ request_id: requestId, moment_id: id,
      operator_id: Number(operator.id), reason: ctx.request.body.reason.trim() }, { transaction });
  });
  successHandler({ ctx, data: { id, visible: false } });
});

router.get('/insights/:kind', async ctx => {
  await requireOperations(ctx);
  const kind = ctx.params.kind;
  if (!['creators', 'transactions'].includes(kind)) throw new CustomError('查询类型无效', 404, 404);
  const params: any = {};
  if (kind === 'creators') {
    const { from, until } = rangeFromQuery(ctx);
    params.from = from.toISOString();
    params.until = until.toISOString();
  } else if (ctx.query.user_id !== undefined) {
    const id = String(ctx.query.user_id);
    if (!/^[1-9][0-9]{0,9}$/.test(id)) throw new CustomError('用户编号无效', 400, 400);
    params.user_id = id;
  }
  const data: any = await axios.get(`${REVENUE_API_URL}/v1/internal/admin-insights/${kind}`, {
    headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY }, params, timeout: 10000,
  });
  successHandler({ ctx, data });
});

router.get('/users/:id/wallet', async ctx => {
  await requireOperations(ctx);
  const id = ctx.params.id;
  if (!/^[1-9][0-9]{0,9}$/.test(id)) throw new CustomError('用户编号无效', 400, 400);
  const data: any = await axios.get(`${REVENUE_API_URL}/v1/internal/admin-insights/wallet/${id}`, {
    headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY }, timeout: 10000,
  });
  successHandler({ ctx, data });
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
