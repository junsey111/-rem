import Router from 'koa-router';
import axiosClient from 'axios';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import sharp from 'sharp';
import sequelize from '@/config/mysql';
import { fn, literal, Op, col } from 'sequelize';

import { authJwt } from '@/app/auth/authJwt';
import { RequestRateLimiter } from '@/app/request-rate';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import User from '@/model/user.model';
import FollowRelation from '@/model/followRelation.model';
import UserSocialProfile from '@/model/userSocialProfile.model';
import { Moment, MomentComment, MomentLike, MomentMedia } from '@/model/moment.model';
import DailyCheckin from '@/model/dailyCheckin.model';
import ReportEvidence from '@/model/reportEvidence.model';
import TaskClaim from '@/model/taskClaim.model';
import TaskConfig from '@/model/taskConfig.model';
import { isOneTimeTask, isSupportedTaskType, taskClaimDate } from '@/service/task-types';
import axios from '@/utils/request';
import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';

const router = new Router({ prefix: '/moments' });
const rate = new RequestRateLimiter();
const MEDIA_LIMIT = 8 * 1024 * 1024;

async function currentUser(ctx) {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id) throw new CustomError(auth.message || '请重新登录', auth.code || 401, auth.code || 401);
  return auth.userInfo;
}

function blockedUserIds(userId: number) {
  return literal(`(SELECT blocked_id FROM user_block_relation WHERE blocker_id = ${userId} UNION SELECT blocker_id FROM user_block_relation WHERE blocked_id = ${userId})`);
}

async function visibleMoment(id: string, userId: number) {
  return Moment.findOne({
    attributes: ['id', 'user_id'],
    where: { [Op.and]: [
      { id, visible: true, user_id: { [Op.notIn]: blockedUserIds(Number(userId)) } },
      { [Op.or]: [
        { review_status: 'APPROVED' },
        { user_id: userId, review_status: { [Op.ne]: 'REJECTED' } },
      ] },
    ] },
  });
}

function mediaRoot() {
  return path.resolve(process.env.MOMENT_MEDIA_DIR || path.join(process.cwd(), 'data', 'moments'));
}

function mediaFile(storageName: string) {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.jpg$/i.test(storageName))
    throw new CustomError('图片不存在', 404, 404);
  const root = mediaRoot();
  const target = path.resolve(root, storageName);
  if (target !== path.join(root, storageName)) throw new CustomError('图片不存在', 404, 404);
  return target;
}

function reportEvidenceRoot() {
  return path.resolve(process.env.REPORT_EVIDENCE_DIR || path.join(process.cwd(), 'data', 'report-evidence'));
}

export function reportEvidenceFile(storageName: string) {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.jpg$/i.test(storageName))
    throw new CustomError('证据不存在', 404, 404);
  const root = reportEvidenceRoot();
  const target = path.resolve(root, storageName);
  if (target !== path.join(root, storageName)) throw new CustomError('证据不存在', 404, 404);
  return target;
}

async function readImageBytes(ctx) {
  if (ctx.get('content-type') !== 'application/octet-stream') throw new CustomError('请以二进制方式上传图片', 415, 415);
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of ctx.req) {
    const buffer = Buffer.from(chunk);
    size += buffer.length;
    if (size > MEDIA_LIMIT) throw new CustomError('图片不能超过 8MB', 413, 413);
    chunks.push(buffer);
  }
  if (!size) throw new CustomError('图片内容为空', 400, 400);
  return Buffer.concat(chunks);
}

async function normalizeImage(input: Buffer) {
  try {
    const source = sharp(input, { limitInputPixels: 25000000, failOn: 'warning' });
    const meta = await source.metadata();
    if (!['jpeg', 'png', 'webp'].includes(meta.format || '') || (meta.pages || 1) > 1) throw Error('format');
    const image = await sharp(input, { limitInputPixels: 25000000, failOn: 'warning' })
      .rotate().resize(1600, 1600, { fit: 'inside', withoutEnlargement: true }).jpeg({ quality: 82, progressive: true }).toBuffer();
    const output = await sharp(image).metadata();
    return { image, width: output.width || 0, height: output.height || 0 };
  } catch {
    throw new CustomError('图片无效，仅支持静态 JPEG、PNG、WebP', 400, 400);
  }
}

async function writeMedia(storageName: string, bytes: Buffer) {
  const target = mediaFile(storageName);
  await fs.promises.mkdir(path.dirname(target), { recursive: true });
  const partial = `${target}.partial`;
  await fs.promises.rm(partial, { force: true });
  await fs.promises.writeFile(partial, bytes, { flag: 'wx', mode: 0o600 });
  await fs.promises.rename(partial, target);
}

async function writeReportEvidence(storageName: string, bytes: Buffer) {
  const target = reportEvidenceFile(storageName);
  await fs.promises.mkdir(path.dirname(target), { recursive: true });
  const partial = `${target}.partial`;
  await fs.promises.rm(partial, { force: true });
  await fs.promises.writeFile(partial, bytes, { flag: 'wx', mode: 0o600 });
  await fs.promises.rename(partial, target);
}

function mediaPayload(row: any) {
  return { id: String(row.id), path: `/moments/media/${row.id}`, content_type: row.content_type,
    bytes: Number(row.byte_length), width: Number(row.width), height: Number(row.height) };
}

function evidencePayload(row: any) {
  return { id: String(row.id), path: `/moments/report-evidence/${row.id}`, content_type: row.content_type,
    bytes: Number(row.byte_length), width: Number(row.width), height: Number(row.height) };
}

async function checkinStatus(userId: number) {
  const today = new Date().toISOString().slice(0, 10);
  const rows: any[] = await DailyCheckin.findAll({
    attributes: ['checkin_date'], where: { user_id: userId, checkin_date: { [Op.lte]: today } },
    order: [['checkin_date', 'DESC']], limit: 370, raw: true,
  });
  const offsets = new Set(rows.map(row => {
    const value = row.checkin_date instanceof Date ? row.checkin_date.toISOString().slice(0, 10) : String(row.checkin_date).slice(0, 10);
    return Math.round((Date.parse(`${today}T00:00:00Z`) - Date.parse(`${value}T00:00:00Z`)) / 86400000);
  }).filter(days => days >= 0));
  const checkedIn = offsets.has(0);
  let expected = checkedIn ? 0 : 1;
  let streak = 0;
  while (offsets.has(expected)) { streak++; expected++; }
  const config: any = await TaskConfig.findOne({ where: { task_key: 'daily_checkin', enabled: true }, raw: true }).catch(() => null);
  return { today, checked_in: checkedIn, streak_days: streak, reward_configured: Boolean(config && Number(config.reward_coins) > 0) };
}

async function taskProgress(userId: number, taskKey: string, todayRange: any, todayDate: string) {
  switch (taskKey) {
    case 'daily_checkin': return (await DailyCheckin.count({ where: { user_id: userId, checkin_date: todayDate } })) > 0 ? 1 : 0;
    case 'follow_user': return FollowRelation.count({ where: { follower_id: userId, created_at: todayRange } });
    case 'first_follow': return FollowRelation.count({ where: { follower_id: userId } });
    case 'set_city': {
      const profile: any = await UserSocialProfile.findOne({ attributes: ['city'], where: { user_id: userId }, raw: true });
      return String(profile?.city || '').trim() ? 1 : 0;
    }
    case 'send_gift':
    case 'start_live': {
      const result: any = await axios.post(`${REVENUE_API_URL}/v1/internal/task-progress`, {
        user_id: userId, task_key: taskKey, task_date: todayDate,
      }, { headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY }, timeout: 10000 });
      const progress = Number(result.progress);
      if (!Number.isSafeInteger(progress) || progress < 0) throw new CustomError('任务进度暂不可用，请稍后重试', 503, 503);
      return progress;
    }
    case 'publish_moment': return Moment.count({ where: { user_id: userId, visible: true, created_at: todayRange } });
    case 'like_moment': return MomentLike.count({ where: { user_id: userId, created_at: todayRange } });
    case 'comment_moment': return MomentComment.count({ where: { user_id: userId, visible: true, created_at: todayRange } });
    default: return 0;
  }
}

async function configuredTasks(userId: number, todayValue: string) {
  const allConfigs: any[] = await TaskConfig.findAll({ where: { enabled: true }, order: [['category', 'ASC'], ['task_key', 'ASC']], raw: true });
  const configs = allConfigs.filter(row => isSupportedTaskType(String(row.task_key)));
  if (!configs.length) return [];
  const todayRange = { [Op.gte]: literal('UTC_DATE()') };
  const claimDates = [...new Set(configs.map(row => taskClaimDate(String(row.task_key), todayValue)))];
  const claims: any[] = await TaskClaim.findAll({ where: { user_id: userId, task_date: { [Op.in]: claimDates }, task_key: { [Op.in]: configs.map(row => row.task_key) } }, raw: true });
  const claimed = new Set(claims.map(row => String(row.task_key)));
  return Promise.all(configs.map(async row => ({
    key: String(row.task_key), category: String(row.category), title: String(row.title), description: String(row.description || ''),
    target_count: Number(row.target_count), progress: Number(await taskProgress(userId, String(row.task_key), todayRange, todayValue)),
    reward_coins: Number(row.reward_coins), claimed: claimed.has(String(row.task_key)), claim_period: isOneTimeTask(String(row.task_key)) ? 'once' : 'daily',
  })));
}

router.get('/tasks/checkin', async ctx => {
  const user = await currentUser(ctx);
  successHandler({ ctx, data: await checkinStatus(Number(user.id)) });
});

router.post('/tasks/checkin', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`daily-checkin:${user.id}`, 10)) throw new CustomError('签到操作过于频繁，请稍后重试', 429, 429);
  const today = new Date().toISOString().slice(0, 10);
  await DailyCheckin.findOrCreate({ where: { user_id: user.id, checkin_date: today }, defaults: { user_id: user.id, checkin_date: today } });
  successHandler({ ctx, data: await checkinStatus(Number(user.id)) });
});

router.post('/tasks/:key/claim', async ctx => {
  const user = await currentUser(ctx);
  const taskKey = String(ctx.params.key || '').trim();
  if (!/^[a-z][a-z0-9_.-]{0,63}$/.test(taskKey)) throw new CustomError('任务无效', 400, 400);
  const config: any = await TaskConfig.findOne({ where: { task_key: taskKey, enabled: true }, raw: true });
  if (!config) throw new CustomError('任务尚未开放', 404, 404);
  if (!isSupportedTaskType(taskKey)) throw new CustomError('该任务类型暂不可领取，请联系平台更新配置', 409, 409);
  const today = new Date().toISOString().slice(0, 10);
  const claimDate = taskClaimDate(taskKey, today);
  const requestId = String(ctx.request.body?.request_id || '').toLowerCase();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(requestId)) throw new CustomError('领取单号无效，请重试', 400, 400);
  const current = await TaskClaim.findOne({ where: { user_id: user.id, task_key: taskKey, task_date: claimDate }, raw: true });
  if (current) {
    successHandler({ ctx, data: { ...current, idempotency: 'replayed' } });
    return;
  }
  const progress = Number(await taskProgress(Number(user.id), taskKey, { [Op.gte]: literal('UTC_DATE()') }, today));
  const target = Number(config.target_count);
  if (target < 1 || progress < target) throw new CustomError('任务尚未完成', 409, 409);
  let result: any;
  try {
    result = await axios.post(`${REVENUE_API_URL}/v1/internal/task-rewards`, {
      request_id: requestId, user_id: Number(user.id), task_key: taskKey, task_date: claimDate, reward_coins: Number(config.reward_coins),
    }, { headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY }, timeout: 10000 });
  } catch (error) {
    if (axiosClient.isAxiosError(error)) {
      const status = error.response?.status;
      throw new CustomError(status && status < 500 ? error.response?.data?.message || '任务奖励未发放' : '任务奖励结果未确认，请使用原单号重试', status && status < 500 ? status : 503, status && status < 500 ? status : 503);
    }
    throw error;
  }
  const [claim] = await TaskClaim.findOrCreate({
    where: { user_id: user.id, task_key: taskKey, task_date: claimDate },
    defaults: { user_id: user.id, task_key: taskKey, task_date: claimDate, request_id: requestId, reward_coins: Number(result.reward_coins), tx_id: Number(result.tx_id), new_balance: Number(result.new_balance) },
  });
  successHandler({ ctx, data: { ...claim.get(), idempotency: result.idempotency || 'created', new_balance: Number(result.new_balance) } });
});

router.get('/tasks/today', async ctx => {
  const user = await currentUser(ctx);
  const today = { [Op.gte]: literal('UTC_DATE()') };
  const [published, liked, commented] = await Promise.all([
    Moment.count({ where: { user_id: user.id, visible: true, created_at: today } }),
    MomentLike.count({ where: { user_id: user.id, created_at: today } }),
    MomentComment.count({ where: { user_id: user.id, created_at: today, visible: true } }),
  ]);
  const todayValue = new Date().toISOString().slice(0, 10);
  successHandler({ ctx, data: { published, liked, commented, tasks: await configuredTasks(Number(user.id), todayValue) } });
});

router.get('/', async ctx => {
  const user = await currentUser(ctx);
  const scope = String(ctx.query.scope || 'all');
  if (!['all', 'following', 'mine', 'user'].includes(scope)) throw new CustomError('动态范围无效', 400, 400);
  const targetUserId = String(ctx.query.user_id || '');
  if (scope === 'user' && (!/^[1-9][0-9]{0,9}$/.test(targetUserId) || !Number.isSafeInteger(Number(targetUserId))))
    throw new CustomError('用户 ID 无效', 400, 400);
  if (scope !== 'user' && targetUserId) throw new CustomError('动态范围无效', 400, 400);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页游标无效', 400, 400);
  const conditions: any[] = [{ visible: true }];
  if (scope === 'mine') conditions.push({ user_id: user.id });
  else {
    conditions.push({ user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } });
    if (scope === 'following') conditions.push({ user_id: { [Op.in]: literal(`(SELECT followed_id FROM user_follow_relation WHERE follower_id = ${Number(user.id)})`) } });
    if (scope === 'user') conditions.push({ user_id: Number(targetUserId) });
    conditions.push({ [Op.or]: [{ review_status: 'APPROVED' }, { user_id: user.id, review_status: { [Op.ne]: 'REJECTED' } }] });
  }
  const where: any = { [Op.and]: conditions };
  if (before) where.id = { [Op.lt]: before };
  const rows: any[] = await Moment.findAll({ where, order: [['id', 'DESC']], limit: 31, raw: true });
  const hasMore = rows.length > 30;
  const page = rows.slice(0, 30);
  const ids = page.map(row => row.id);
  const userIds = [...new Set(page.map(row => Number(row.user_id)))];
  const [authors, counts, commentCounts, liked, mediaRows] = await Promise.all([
    userIds.length ? User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: userIds }, status: 1 }, raw: true }) : [],
    ids.length ? MomentLike.findAll({ attributes: ['moment_id', [fn('COUNT', col('moment_id')), 'count']], where: { moment_id: { [Op.in]: ids } }, group: ['moment_id'], raw: true }) : [],
    ids.length ? MomentComment.findAll({ attributes: ['moment_id', [fn('COUNT', col('moment_id')), 'count']], where: { visible: true, review_status: 'APPROVED', moment_id: { [Op.in]: ids }, user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } }, group: ['moment_id'], raw: true }) : [],
    ids.length ? MomentLike.findAll({ attributes: ['moment_id'], where: { moment_id: { [Op.in]: ids }, user_id: user.id }, raw: true }) : [],
    ids.length ? MomentMedia.findAll({ where: { moment_id: { [Op.in]: ids } }, order: [['id', 'ASC']], raw: true }) : [],
  ]);
  const authorById = new Map(authors.map((author: any): [number, any] => [Number(author.id), author]));
  const countById = new Map(counts.map((item: any): [string, number] => [String(item.moment_id), Number(item.count)]));
  const commentCountById = new Map(commentCounts.map((item: any): [string, number] => [String(item.moment_id), Number(item.count)]));
  const likedIds = new Set(liked.map((item: any) => String(item.moment_id)));
  const mediaByMoment = new Map<string, any[]>();
  (mediaRows as any[]).forEach(row => {
    const key = String(row.moment_id);
    const list = mediaByMoment.get(key) || [];
    list.push(mediaPayload(row));
    mediaByMoment.set(key, list);
  });
  const moments = page.map(row => ({ id: String(row.id), user_id: Number(row.user_id), content: row.content, created_at: row.created_at,
    author: authorById.get(Number(row.user_id)) || null, like_count: countById.get(String(row.id)) || 0,
    comment_count: commentCountById.get(String(row.id)) || 0, is_liked: likedIds.has(String(row.id)),
    review_status: row.review_status || 'APPROVED', media: mediaByMoment.get(String(row.id)) || [] }))
    .filter(row => row.author);
  successHandler({ ctx, data: { moments, next_before_id: hasMore ? String(page[page.length - 1].id) : '' } });
});

router.post('/media', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`moment-media:${user.id}`, 12)) throw new CustomError('图片上传过于频繁，请稍后重试', 429, 429);
  const normalized = await normalizeImage(await readImageBytes(ctx));
  const storageName = `${crypto.randomUUID()}.jpg`;
  const sha256 = crypto.createHash('sha256').update(normalized.image).digest('hex');
  try {
    await writeMedia(storageName, normalized.image);
    const row: any = await MomentMedia.create({ user_id: user.id, storage_name: storageName, content_type: 'image/jpeg',
      byte_length: normalized.image.length, width: normalized.width, height: normalized.height, sha256 });
    successHandler({ ctx, data: { media: mediaPayload(row) } });
  } catch (error) {
    await fs.promises.rm(mediaFile(storageName), { force: true }).catch(() => undefined);
    throw error;
  }
});

router.post('/report-evidence', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`report-evidence:${user.id}`, 8)) throw new CustomError('图片上传过于频繁，请稍后重试', 429, 429);
  const normalized = await normalizeImage(await readImageBytes(ctx));
  const storageName = `${crypto.randomUUID()}.jpg`;
  const sha256 = crypto.createHash('sha256').update(normalized.image).digest('hex');
  try {
    await writeReportEvidence(storageName, normalized.image);
    const row: any = await ReportEvidence.create({ user_id: user.id, storage_name: storageName, content_type: 'image/jpeg',
      byte_length: normalized.image.length, width: normalized.width, height: normalized.height, sha256 });
    successHandler({ ctx, data: { evidence: evidencePayload(row), reference: `report-evidence:${row.id}` } });
  } catch (error) {
    await fs.promises.rm(reportEvidenceFile(storageName), { force: true }).catch(() => undefined);
    throw error;
  }
});

router.get('/report-evidence/:id', async ctx => {
  const user = await currentUser(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('证据不存在', 404, 404);
  const row: any = await ReportEvidence.findOne({ where: { id: ctx.params.id, user_id: user.id } });
  if (!row) throw new CustomError('证据不存在', 404, 404);
  const file = reportEvidenceFile(row.storage_name);
  if (!fs.existsSync(file)) throw new CustomError('证据暂时不可用，请稍后重试', 503, 503);
  ctx.type = row.content_type;
  ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.set('Cache-Control', 'private,max-age=60');
  ctx.body = fs.createReadStream(file);
});

router.get('/media/:id', async ctx => {
  const user = await currentUser(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('图片不存在', 404, 404);
  const row: any = await MomentMedia.findByPk(ctx.params.id);
  if (!row) throw new CustomError('图片不存在', 404, 404);
  const own = Number(row.user_id) === Number(user.id);
  if (row.moment_id && !own) {
    const moment: any = await Moment.findOne({ where: { id: row.moment_id, visible: true, review_status: 'APPROVED', user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } } });
    if (!moment) throw new CustomError('图片不存在或不可查看', 404, 404);
  } else if (!row.moment_id && !own) throw new CustomError('图片不存在', 404, 404);
  const file = mediaFile(row.storage_name);
  if (!fs.existsSync(file)) throw new CustomError('图片暂时不可用，请稍后重试', 503, 503);
  ctx.type = row.content_type;
  ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.set('Cache-Control', 'private,max-age=60');
  ctx.body = fs.createReadStream(file);
});

router.delete('/media/:id', async ctx => {
  const user = await currentUser(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('图片不存在', 404, 404);
  const row: any = await MomentMedia.findOne({ where: { id: ctx.params.id, user_id: user.id, moment_id: null } });
  if (!row) throw new CustomError('图片不存在或已使用', 404, 404);
  await row.destroy();
  await fs.promises.rm(mediaFile(row.storage_name), { force: true }).catch(() => undefined);
  successHandler({ ctx, data: { id: ctx.params.id, deleted: true } });
});

router.get('/:id/comments', async ctx => {
  const user = await currentUser(ctx);
  if (!user.id) throw new CustomError('请重新登录', 401, 401);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页游标无效', 400, 400);
  const moment: any = await visibleMoment(ctx.params.id, user.id);
  if (!moment) throw new CustomError('动态不存在或不可查看', 404, 404);
  const where: any = { moment_id: ctx.params.id, visible: true, user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) },
    [Op.or]: [{ review_status: 'APPROVED' }, { user_id: user.id }] };
  if (before) where.id = { [Op.lt]: before };
  const rows: any[] = await MomentComment.findAll({ where, order: [['id', 'DESC']], limit: 31, raw: true });
  const hasMore = rows.length > 30;
  const page = rows.slice(0, 30);
  const authorIds = [...new Set(page.map(row => Number(row.user_id)))];
  const authors: any[] = authorIds.length ? await User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: authorIds }, status: 1 }, raw: true }) : [];
  const authorById = new Map(authors.map((author: any): [number, any] => [Number(author.id), author]));
  const comments = page.map(row => ({ id: String(row.id), user_id: Number(row.user_id), content: row.content,
    created_at: row.created_at, review_status: row.review_status, moderation_reason: Number(row.user_id) === Number(user.id) ? row.moderation_reason || '' : '',
    author: authorById.get(Number(row.user_id)) || null, is_mine: Number(row.user_id) === Number(user.id) }))
    .filter(row => row.author);
  successHandler({ ctx, data: { comments, next_before_id: hasMore ? String(page[page.length - 1].id) : '' } });
});

router.post('/:id/comments', async ctx => {
  const user = await currentUser(ctx);
  if (!user.id) throw new CustomError('请重新登录', 401, 401);
  if (rate.consume(`moment-comment:${user.id}`, 30)) throw new CustomError('评论过于频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const content = typeof ctx.request.body?.content === 'string' ? ctx.request.body.content.trim() : '';
  if (!content || Array.from(content).length > 500) throw new CustomError('评论需为 1 到 500 个字符', 400, 400);
  const moment: any = await visibleMoment(ctx.params.id, user.id);
  if (!moment) throw new CustomError('动态不存在或不可查看', 404, 404);
  const row: any = await MomentComment.create({ moment_id: moment.id, user_id: user.id, content, review_status: 'PENDING' });
  successHandler({ ctx, data: { comment: { id: String(row.id), user_id: Number(user.id), content: row.content,
    created_at: row.created_at, review_status: row.review_status, moderation_reason: '',
    author: { id: Number(user.id), username: user.username }, is_mine: true } } });
});

router.delete('/:id/comments/:commentId', async ctx => {
  const user = await currentUser(ctx);
  if (!user.id) throw new CustomError('请重新登录', 401, 401);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id) || !/^[1-9][0-9]{0,19}$/.test(ctx.params.commentId))
    throw new CustomError('评论不存在', 404, 404);
  const moment: any = await visibleMoment(ctx.params.id, user.id);
  if (!moment) throw new CustomError('动态不存在或不可查看', 404, 404);
  const [updated] = await MomentComment.update({ visible: false }, {
    where: { id: ctx.params.commentId, moment_id: ctx.params.id, user_id: user.id, visible: true },
  });
  if (!updated) throw new CustomError('评论不存在或已删除', 404, 404);
  successHandler({ ctx, data: { id: ctx.params.commentId, deleted: true } });
});

router.post('/', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`moment-create:${user.id}`, 10)) throw new CustomError('发布过于频繁，请稍后重试', 429, 429);
  const content = typeof ctx.request.body?.content === 'string' ? ctx.request.body.content.trim() : '';
  if (Array.from(content).length > 500) throw new CustomError('动态内容不能超过 500 个字符', 400, 400);
  const mediaIds = Array.isArray(ctx.request.body?.media_ids) ? ctx.request.body.media_ids : [];
  if (mediaIds.length > 9 || mediaIds.some(id => typeof id !== 'string' || !/^[1-9][0-9]{0,19}$/.test(id)))
    throw new CustomError('动态图片数量或编号无效', 400, 400);
  if (!content && !mediaIds.length) throw new CustomError('动态内容或图片至少填写一项', 400, 400);
  const reviewStatus = 'PENDING';
  const row: any = await sequelize.transaction(async transaction => {
    const media = mediaIds.length ? await MomentMedia.findAll({
      where: { id: { [Op.in]: mediaIds }, user_id: user.id, moment_id: null }, transaction,
    }) : [];
    if (media.length !== mediaIds.length) throw new CustomError('图片不存在、已使用或不属于当前账号', 409, 409);
    const created: any = await Moment.create({ user_id: user.id, content, review_status: reviewStatus }, { transaction });
    if (media.length) await MomentMedia.update({ moment_id: created.id }, { where: { id: { [Op.in]: mediaIds } }, transaction });
    return { created, media };
  });
  successHandler({ ctx, data: { id: String(row.created.id), user_id: Number(user.id), content: row.created.content,
    created_at: row.created.created_at, review_status: reviewStatus, media: row.media.map(mediaPayload) } });
});

router.delete('/:id', async ctx => {
  const user = await currentUser(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const [updated] = await Moment.update({ visible: false }, { where: { id: ctx.params.id, user_id: user.id, visible: true } });
  if (!updated) throw new CustomError('动态不存在或已删除', 404, 404);
  successHandler({ ctx, data: { id: ctx.params.id, deleted: true } });
});

router.put('/:id/like', async ctx => {
  const user = await currentUser(ctx);
  if (!user.id) throw new CustomError('请重新登录', 401, 401);
  if (rate.consume(`moment-like:${user.id}`, 120)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const moment: any = await visibleMoment(ctx.params.id, user.id);
  if (!moment) throw new CustomError('动态不存在', 404, 404);
  await MomentLike.findOrCreate({ where: { moment_id: moment.id, user_id: user.id } });
  successHandler({ ctx, data: { moment_id: String(moment.id), is_liked: true } });
});

router.delete('/:id/like', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`moment-like:${user.id}`, 120)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  await MomentLike.destroy({ where: { moment_id: ctx.params.id, user_id: user.id } });
  successHandler({ ctx, data: { moment_id: ctx.params.id, is_liked: false } });
});

export default router;
