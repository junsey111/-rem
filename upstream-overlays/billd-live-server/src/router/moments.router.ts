import Router from 'koa-router';
import { fn, literal, Op, col } from 'sequelize';

import { authJwt } from '@/app/auth/authJwt';
import { RequestRateLimiter } from '@/app/request-rate';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import User from '@/model/user.model';
import { Moment, MomentComment, MomentLike } from '@/model/moment.model';
import DailyCheckin from '@/model/dailyCheckin.model';

const router = new Router({ prefix: '/moments' });
const rate = new RequestRateLimiter();

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
    where: { id, visible: true, user_id: { [Op.notIn]: blockedUserIds(Number(userId)) } },
  });
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
  return { today, checked_in: checkedIn, streak_days: streak, reward_configured: false };
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

router.get('/tasks/today', async ctx => {
  const user = await currentUser(ctx);
  const today = { [Op.gte]: literal('UTC_DATE()') };
  const [published, liked, commented] = await Promise.all([
    Moment.count({ where: { user_id: user.id, visible: true, createdAt: today } }),
    MomentLike.count({ where: { user_id: user.id, createdAt: today } }),
    MomentComment.count({ where: { user_id: user.id, createdAt: today, visible: true } }),
  ]);
  successHandler({ ctx, data: { published, liked, commented } });
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
  }
  const where: any = { [Op.and]: conditions };
  if (before) where.id = { [Op.lt]: before };
  const rows: any[] = await Moment.findAll({ where, order: [['id', 'DESC']], limit: 31, raw: true });
  const hasMore = rows.length > 30;
  const page = rows.slice(0, 30);
  const ids = page.map(row => row.id);
  const userIds = [...new Set(page.map(row => Number(row.user_id)))];
  const [authors, counts, commentCounts, liked] = await Promise.all([
    userIds.length ? User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: userIds }, status: 1 }, raw: true }) : [],
    ids.length ? MomentLike.findAll({ attributes: ['moment_id', [fn('COUNT', col('moment_id')), 'count']], where: { moment_id: { [Op.in]: ids } }, group: ['moment_id'], raw: true }) : [],
    ids.length ? MomentComment.findAll({ attributes: ['moment_id', [fn('COUNT', col('moment_id')), 'count']], where: { visible: true, moment_id: { [Op.in]: ids }, user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } }, group: ['moment_id'], raw: true }) : [],
    ids.length ? MomentLike.findAll({ attributes: ['moment_id'], where: { moment_id: { [Op.in]: ids }, user_id: user.id }, raw: true }) : [],
  ]);
  const authorById = new Map(authors.map((author: any): [number, any] => [Number(author.id), author]));
  const countById = new Map(counts.map((item: any): [string, number] => [String(item.moment_id), Number(item.count)]));
  const commentCountById = new Map(commentCounts.map((item: any): [string, number] => [String(item.moment_id), Number(item.count)]));
  const likedIds = new Set(liked.map((item: any) => String(item.moment_id)));
  const moments = page.map(row => ({ id: String(row.id), user_id: Number(row.user_id), content: row.content, created_at: row.created_at,
    author: authorById.get(Number(row.user_id)) || null, like_count: countById.get(String(row.id)) || 0,
    comment_count: commentCountById.get(String(row.id)) || 0, is_liked: likedIds.has(String(row.id)) }))
    .filter(row => row.author);
  successHandler({ ctx, data: { moments, next_before_id: hasMore ? String(page[page.length - 1].id) : '' } });
});

router.get('/:id/comments', async ctx => {
  const user = await currentUser(ctx);
  if (!user.id) throw new CustomError('请重新登录', 401, 401);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页游标无效', 400, 400);
  const moment: any = await visibleMoment(ctx.params.id, user.id);
  if (!moment) throw new CustomError('动态不存在或不可查看', 404, 404);
  const where: any = { moment_id: ctx.params.id, visible: true, user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } };
  if (before) where.id = { [Op.lt]: before };
  const rows: any[] = await MomentComment.findAll({ where, order: [['id', 'DESC']], limit: 31, raw: true });
  const hasMore = rows.length > 30;
  const page = rows.slice(0, 30);
  const authorIds = [...new Set(page.map(row => Number(row.user_id)))];
  const authors: any[] = authorIds.length ? await User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: authorIds }, status: 1 }, raw: true }) : [];
  const authorById = new Map(authors.map((author: any): [number, any] => [Number(author.id), author]));
  const comments = page.map(row => ({ id: String(row.id), user_id: Number(row.user_id), content: row.content,
    created_at: row.created_at, author: authorById.get(Number(row.user_id)) || null, is_mine: Number(row.user_id) === Number(user.id) }))
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
  const row: any = await MomentComment.create({ moment_id: moment.id, user_id: user.id, content });
  successHandler({ ctx, data: { comment: { id: String(row.id), user_id: Number(user.id), content: row.content,
    created_at: row.created_at, author: { id: Number(user.id), username: user.username }, is_mine: true } } });
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
  if (!content || Array.from(content).length > 500) throw new CustomError('动态内容需为 1 到 500 个字符', 400, 400);
  const row: any = await Moment.create({ user_id: user.id, content });
  successHandler({ ctx, data: { id: String(row.id), user_id: Number(user.id), content: row.content, created_at: row.created_at } });
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
  if (rate.consume(`moment-like:${user.id}`, 120)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const moment: any = await Moment.findOne({ attributes: ['id'], where: { id: ctx.params.id, visible: true } });
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
