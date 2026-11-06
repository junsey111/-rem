import Router from 'koa-router';
import { Op } from 'sequelize';

import { authJwt } from '@/app/auth/authJwt';
import { RequestRateLimiter } from '@/app/request-rate';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import CloseFriendRelation from '@/model/closeFriendRelation.model';
import User from '@/model/user.model';
import UserBlock from '@/model/userBlock.model';

const router = new Router({ prefix: '/close-friends' });
const rate = new RequestRateLimiter();

async function currentUser(ctx) {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id) throw new CustomError(auth.message || '请重新登录', auth.code || 401, auth.code || 401);
  return auth.userInfo;
}

function pairIds(a: number, b: number) {
  return { user_low_id: Math.min(a, b), user_high_id: Math.max(a, b) };
}

async function relationshipData(userId: number) {
  const rows: any[] = await CloseFriendRelation.findAll({
    where: { [Op.or]: [{ user_low_id: userId }, { user_high_id: userId }] },
    order: [['updated_at', 'DESC'], ['id', 'DESC']], limit: 100, raw: true,
  });
  const blocked: any[] = await UserBlock.findAll({
    attributes: ['blocker_id', 'blocked_id'],
    where: { [Op.or]: [{ blocker_id: userId }, { blocked_id: userId }] }, raw: true,
  });
  const hiddenIds = new Set(blocked.map(row => Number(row.blocker_id) === userId ? Number(row.blocked_id) : Number(row.blocker_id)));
  const otherIds = [...new Set(rows.map(row => Number(row.user_low_id) === userId ? Number(row.user_high_id) : Number(row.user_low_id)))];
  const users: any[] = otherIds.length ? await User.findAll({
    attributes: ['id', 'username', 'avatar', 'desc'],
    where: { id: { [Op.in]: otherIds }, status: 1 }, raw: true,
  }) : [];
  const byId = new Map(users.map(user => [Number(user.id), user]));
  const result: { friends: any[]; incoming: any[]; outgoing: any[] } = { friends: [], incoming: [], outgoing: [] };
  rows.forEach(row => {
    const otherId = Number(row.user_low_id) === userId ? Number(row.user_high_id) : Number(row.user_low_id);
    const user = byId.get(otherId);
    if (!user || hiddenIds.has(otherId)) return;
    const value = { user, created_at: row.created_at };
    if (row.status === 'accepted') result.friends.push(value);
    else if (Number(row.requested_by) === userId) result.outgoing.push(value);
    else result.incoming.push(value);
  });
  return result;
}

router.get('/', async ctx => {
  const user = await currentUser(ctx);
  successHandler({ ctx, data: await relationshipData(Number(user.id)) });
});

router.post('/', async ctx => {
  const user = await currentUser(ctx);
  const userId = Number(user.id);
  if (rate.consume(`close-friend:${userId}`, 30)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  const targetId = Number(ctx.request.body?.user_id);
  if (!Number.isSafeInteger(targetId) || targetId < 1 || targetId === userId) throw new CustomError('用户 ID 无效', 400, 400);
  const target = await User.findOne({ attributes: ['id'], where: { id: targetId, status: 1 } });
  if (!target) throw new CustomError('用户不存在或不可用', 404, 404);
  const blocked = await UserBlock.findOne({ where: { [Op.or]: [
    { blocker_id: userId, blocked_id: targetId }, { blocker_id: targetId, blocked_id: userId },
  ] } });
  if (blocked) throw new CustomError('存在屏蔽关系，无法发送蜜友邀请', 409, 409);
  const where = pairIds(userId, targetId);
  const [relationValue, created] = await CloseFriendRelation.findOrCreate({
    where,
    defaults: { ...where, requested_by: userId, status: 'pending' },
  });
  const relation: any = relationValue;
  if (!created) {
    if (relation.status === 'accepted') throw new CustomError('你们已经是蜜友', 409, 409);
    if (Number(relation.requested_by) === userId) return successHandler({ ctx, data: { pending: true } });
    throw new CustomError('对方已向你发送邀请，请先处理对方的邀请', 409, 409);
  }
  successHandler({ ctx, data: { pending: true } });
});

router.post('/:userId/accept', async ctx => {
  const user = await currentUser(ctx);
  const userId = Number(user.id);
  if (rate.consume(`close-friend:${userId}`, 30)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.userId)) throw new CustomError('用户 ID 无效', 400, 400);
  const requesterId = Number(ctx.params.userId);
  const requester = await User.findOne({ attributes: ['id'], where: { id: requesterId, status: 1 } });
  if (!requester) throw new CustomError('邀请用户不存在或不可用', 404, 404);
  const blocked = await UserBlock.findOne({ where: { [Op.or]: [
    { blocker_id: userId, blocked_id: requesterId }, { blocker_id: requesterId, blocked_id: userId },
  ] } });
  if (blocked) throw new CustomError('存在屏蔽关系，无法接受邀请', 409, 409);
  const [updated] = await CloseFriendRelation.update({ status: 'accepted' }, {
    where: { ...pairIds(userId, requesterId), requested_by: requesterId, status: 'pending' },
  });
  if (!updated) throw new CustomError('邀请不存在或已处理', 404, 404);
  successHandler({ ctx, data: { accepted: true } });
});

router.delete('/:userId', async ctx => {
  const user = await currentUser(ctx);
  const userId = Number(user.id);
  if (rate.consume(`close-friend:${userId}`, 30)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.userId)) throw new CustomError('用户 ID 无效', 400, 400);
  const targetId = Number(ctx.params.userId);
  await CloseFriendRelation.destroy({ where: pairIds(userId, targetId) });
  successHandler({ ctx, data: { removed: true } });
});

export default router;
