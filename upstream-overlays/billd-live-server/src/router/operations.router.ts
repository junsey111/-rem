import Router from 'koa-router';
import fs from 'fs';
import { Op } from 'sequelize';
import { authJwt } from '@/app/auth/authJwt';
import { canManageOperations, requireOperations } from '@/app/auth/operations';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import userModel from '@/model/user.model';
import liveModel from '@/model/live.model';
import rechargeOrder from '@/model/rechargeOrder.model';
import User from '@/model/user.model';
import { Moment, MomentMedia, MomentComment } from '@/model/moment.model';
import axiosClient from 'axios';
import axios from '@/utils/request';
import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';
import ReportEvidence from '@/model/reportEvidence.model';
import { reportEvidenceFile } from '@/router/moments.router';
import TaskConfig from '@/model/taskConfig.model';
import { isSupportedTaskType, TASK_TYPES } from '@/service/task-types';
import sequelize from '@/config/mysql';
import { PlatformAnnouncement, PlatformAnnouncementAudit } from '@/model/platformAnnouncement.model';
import { queueAnnouncementPush } from '@/service/push-notifications';

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

router.get('/tasks', async ctx => {
  await requireOperations(ctx);
  const tasks = await TaskConfig.findAll({ order: [['category', 'ASC'], ['task_key', 'ASC']] });
  successHandler({ ctx, data: {
    tasks: tasks.map(task => {
      const row: any = task.get({ plain: true });
      return { ...row, progress_supported: isSupportedTaskType(String(row.task_key)) };
    }),
    supported_task_types: TASK_TYPES,
  } });
});

router.put('/tasks/:key', async ctx => {
  const operator = await requireOperations(ctx);
  const taskKey = String(ctx.params.key || '').trim();
  const body = ctx.request.body || {};
  const category = String(body.category || '').trim();
  const title = typeof body.title === 'string' ? body.title.trim() : '';
  const description = typeof body.description === 'string' ? body.description.trim() : '';
  const targetCount = Number(body.target_count);
  const rewardCoins = Number(body.reward_coins);
  const enabled = body.enabled === true;
  if (!isSupportedTaskType(taskKey) || !/^[a-z][a-z0-9_-]{0,23}$/.test(category)
    || !title || Array.from(title).length > 100 || Array.from(description).length > 255
    || !Number.isInteger(targetCount) || targetCount < 1 || targetCount > 1000
    || !Number.isInteger(rewardCoins) || rewardCoins < 1 || rewardCoins > 1000000) {
    throw new CustomError('任务配置无效', 400, 400);
  }
  const [task, created] = await TaskConfig.findOrCreate({ where: { task_key: taskKey }, defaults: {
    task_key: taskKey, category, title, description, target_count: targetCount, reward_coins: rewardCoins, enabled, updated_by: Number(operator.id),
  } });
  if (!created) await task.update({ category, title, description, target_count: targetCount, reward_coins: rewardCoins, enabled, updated_by: Number(operator.id) });
  successHandler({ ctx, data: { task } });
});

router.delete('/tasks/:key', async ctx => {
  await requireOperations(ctx);
  const task = await TaskConfig.findByPk(String(ctx.params.key || ''));
  if (!task) throw new CustomError('任务不存在', 404, 404);
  await task.update({ enabled: false });
  successHandler({ ctx, data: { task_key: String(ctx.params.key), enabled: false } });
});

router.get('/announcements', async ctx => {
  await requireOperations(ctx);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页位置无效，请刷新', 400, 400);
  const where: any = {};
  if (before) where.announcement_id = { [Op.lt]: before };
  const rows: any[] = await PlatformAnnouncement.findAll({ where, order: [['announcement_id', 'DESC']], limit: 51, raw: true });
  const hasMore = rows.length > 50;
  const page = rows.slice(0, 50);
  successHandler({ ctx, data: {
    announcements: page.map(row => ({ ...row, id: String(row.announcement_id), created_by: Number(row.created_by), updated_by: Number(row.updated_by) })),
    next_before_id: hasMore ? String(page[page.length - 1].announcement_id) : '',
  } });
});

async function saveAnnouncement(ctx, action: 'CREATE' | 'UPDATE') {
  const operator = await requireOperations(ctx);
  const id = action === 'UPDATE' ? String(ctx.params.id || '') : '';
  if (action === 'UPDATE' && !/^[1-9][0-9]{0,19}$/.test(id)) throw new CustomError('公告 ID 无效', 400, 400);
  const body = ctx.request.body || {};
  const requestId = body.request_id;
  const reason = typeof body.reason === 'string' ? body.reason.trim() : '';
  const title = typeof body.title === 'string' ? body.title.trim() : '';
  const summary = typeof body.summary === 'string' ? body.summary.trim() : '';
  const content = typeof body.content === 'string' ? body.content.trim() : '';
  const enabled = body.enabled === true;
  if (typeof requestId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(requestId)
    || !reason || Array.from(reason).length > 200 || !title || Array.from(title).length > 120
    || Array.from(summary).length > 240 || !content || Array.from(content).length > 30000
    || typeof body.enabled !== 'boolean') throw new CustomError('公告内容、发布状态、操作原因或单号无效', 400, 400);
  const payload = JSON.stringify({ title, summary, content, enabled });
  const existingAudit: any = await PlatformAnnouncementAudit.findOne({ where: { request_id: requestId }, raw: true });
  if (existingAudit) {
    if (existingAudit.action !== action || String(existingAudit.announcement_id || '') !== id || existingAudit.reason !== reason || existingAudit.payload !== payload)
      throw new CustomError('该操作单号已用于其他公告变更', 409, 409);
    const announcement = await PlatformAnnouncement.findByPk(existingAudit.announcement_id, { raw: true });
    successHandler({ ctx, data: { announcement, replayed: true } });
    return;
  }
  const result = await sequelize.transaction(async transaction => {
    let row: any;
    let shouldNotify = false;
    if (action === 'CREATE') {
      row = await PlatformAnnouncement.create({ title, summary, content, enabled, created_by: Number(operator.id), updated_by: Number(operator.id) }, { transaction });
      shouldNotify = enabled;
    } else {
      row = await PlatformAnnouncement.findByPk(id, { transaction, lock: transaction.LOCK.UPDATE });
      if (!row) throw new CustomError('公告不存在', 404, 404);
      const wasEnabled = Boolean(row.get('enabled'));
      await row.update({ title, summary, content, enabled, updated_by: Number(operator.id) }, { transaction });
      shouldNotify = enabled && !wasEnabled;
    }
    await PlatformAnnouncementAudit.create({ request_id: requestId, announcement_id: row.announcement_id, operator_id: Number(operator.id), action, reason, payload }, { transaction });
    return { row, shouldNotify };
  });
  const announcement: any = result.row;
  if (result.shouldNotify) queueAnnouncementPush({ id: String(announcement.announcement_id), title, summary });
  successHandler({ ctx, data: { announcement, replayed: false } });
}

router.post('/announcements', async ctx => saveAnnouncement(ctx, 'CREATE'));
router.put('/announcements/:id', async ctx => saveAnnouncement(ctx, 'UPDATE'));

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

// Both content queues use the same permission, pagination and moderation rules.
for (const kind of ['moments', 'comments']) {
const ContentModel: any = kind === 'moments' ? Moment : MomentComment;
router.get(`/${kind}`, async ctx => {
  await requireOperations(ctx);
  const status = String(ctx.query.status || 'PENDING');
  if (!['PENDING', 'APPROVED', 'REJECTED', 'ALL'].includes(status)) throw new CustomError('动态审核状态无效', 400, 400);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页无效，请刷新', 400, 400);
  const where: any = status === 'ALL' ? {} : { review_status: status };
  if (kind === 'comments') where.visible = true; // Author-deleted comments cannot be restored by moderation.
  if (before) where.id = { [Op.lt]: before };
  const rows: any[] = await ContentModel.findAll({ where, order: [['id', 'DESC']], limit: 51, raw: true });
  const hasMore = rows.length > 50;
  const page = rows.slice(0, 50);
  const ids = page.map(row => row.id);
  const userIds = [...new Set(page.map(row => Number(row.user_id)))];
  const [authors, media] = await Promise.all([
    userIds.length ? User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: userIds } }, raw: true }) : [],
    kind === 'moments' && ids.length ? MomentMedia.findAll({ where: { moment_id: { [Op.in]: ids } }, order: [['id', 'ASC']], raw: true }) : [],
  ]);
  const authorById = new Map((authors as any[]).map(author => [Number(author.id), author]));
  const mediaByMoment = new Map<string, any[]>();
  (media as any[]).forEach(row => {
    const key = String(row.moment_id);
    const list = mediaByMoment.get(key) || [];
    list.push({ id: String(row.id), path: `/moments/media/${row.id}`, content_type: row.content_type,
      bytes: Number(row.byte_length), width: Number(row.width), height: Number(row.height) });
    mediaByMoment.set(key, list);
  });
  successHandler({ ctx, data: { [kind]: page.map(row => ({ id: String(row.id), user_id: Number(row.user_id),
    ...(kind === 'comments' ? { moment_id: String(row.moment_id) } : {}),
    author: authorById.get(Number(row.user_id)) || null, content: row.content, created_at: row.created_at,
    visible: Boolean(row.visible), review_status: row.review_status || 'APPROVED', moderation_reason: row.moderation_reason || '',
    reviewed_by: row.reviewed_by ? Number(row.reviewed_by) : null, reviewed_at: row.reviewed_at,
    media: mediaByMoment.get(String(row.id)) || [] })).filter(row => row.author),
    next_before_id: hasMore ? String(page[page.length - 1].id) : '' } });
});

router.post(`/${kind}/:id`, async ctx => {
  const operator = await requireOperations(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const action = String(ctx.request.body?.action || '');
  if (!['APPROVED', 'REJECTED', 'HIDE', 'RESTORE'].includes(action)) throw new CustomError('审核操作无效', 400, 400);
  const reason = typeof ctx.request.body?.reason === 'string' ? ctx.request.body.reason.trim() : '';
  if (['REJECTED', 'HIDE'].includes(action) && (!reason || Array.from(reason).length > 200))
    throw new CustomError('驳回或隐藏必须填写 1 至 200 字原因', 400, 400);
  const moment: any = await sequelize.transaction(async transaction => {
    const row: any = await ContentModel.findByPk(ctx.params.id, { transaction, lock: transaction.LOCK.UPDATE });
    if (!row) throw new CustomError('内容不存在', 404, 404);
    if (kind === 'comments' && !row.visible) throw new CustomError('评论已被作者删除，不可恢复', 409, 409);
    const rejected = action === 'REJECTED' || action === 'HIDE';
    await row.update({ review_status: rejected ? 'REJECTED' : 'APPROVED', visible: kind === 'comments' || action !== 'HIDE',
      moderation_reason: reason || null, reviewed_by: Number(operator.id), reviewed_at: new Date() }, { transaction });
    return row;
  });
  successHandler({ ctx, data: { id: String(moment.id), action, review_status: moment.review_status,
    visible: Boolean(moment.visible), reviewed_by: Number(operator.id), reviewed_at: moment.reviewed_at } });
});
}

router.get('/reports/evidence/:id', async ctx => {
  await requireOperations(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('证据不存在', 404, 404);
  const row: any = await ReportEvidence.findByPk(ctx.params.id);
  if (!row) throw new CustomError('证据不存在', 404, 404);
  const file = reportEvidenceFile(row.storage_name);
  if (!fs.existsSync(file)) throw new CustomError('证据暂时不可用，请稍后重试', 503, 503);
  ctx.type = row.content_type;
  ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.set('Cache-Control', 'private,max-age=60');
  ctx.body = fs.createReadStream(file);
});

export default router;
