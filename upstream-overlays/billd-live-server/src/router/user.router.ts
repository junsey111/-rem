import Router from 'koa-router';
import { Op, literal } from 'sequelize';

import userController from '@/controller/user.controller';
import { authJwt } from '@/app/auth/authJwt';
import { CustomError } from '@/model/customError.model';
import successHandler from '@/app/handler/success-handle';
import { changePassword } from '@/app/auth/password-login';
import sequelize from '@/config/mysql';
import User from '@/model/user.model';
import { RequestRateLimiter } from '@/app/request-rate';
import FollowRelation from '@/model/followRelation.model';
import ProfileVisit from '@/model/profileVisit.model';
import UserBlock from '@/model/userBlock.model';
import axiosClient from 'axios';
import { REVENUE_API_URL } from '@/config/secret';

const userRouter = new Router({ prefix: '/user' });
const searchRate = new RequestRateLimiter();

userRouter.post('/create', userController.create);
userRouter.post('/login', userController.login);
userRouter.post('/password', changePassword);
userRouter.post('/logout', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message, code, code);
  // A delayed logout must never invalidate a newer login.
  await sequelize.transaction(async transaction => {
    const user: any = await User.findByPk(userInfo.id, { transaction, lock: transaction.LOCK.UPDATE });
    if (user?.token === ctx.req.headers.authorization!.slice(7)) await user.update({ token: null }, { transaction });
  });
  successHandler({ ctx, message: '已退出登录' });
});
userRouter.get('/me', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo) throw new CustomError(message, code, code);
  successHandler({ ctx, data: { id: userInfo.id, username: userInfo.username, status: userInfo.status } });
});

userRouter.post('/search', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message, code || 401, code || 401);
  if (searchRate.consume(`user-search:${userInfo.id}`, 30))
    throw new CustomError('搜索频繁，请稍后重试', 429, 429);
  const query = typeof ctx.request.body?.query === 'string' ? ctx.request.body.query.trim() : '';
  if (!query || Array.from(query).length > 20)
    throw new CustomError('请输入 1 到 20 个字符', 400, 400);
  const users = await User.findAll({
    attributes: ['id', 'username', 'avatar', 'desc'],
    where: {
      [Op.and]: [
        { status: 1 },
        { id: { [Op.ne]: userInfo.id } },
        { id: { [Op.notIn]: literal(`(SELECT blocked_id FROM user_block_relation WHERE blocker_id = ${Number(userInfo.id)})`) } },
        { id: { [Op.notIn]: literal(`(SELECT blocker_id FROM user_block_relation WHERE blocked_id = ${Number(userInfo.id)})`) } },
        /^[0-9]{1,10}$/.test(query)
          ? { id: Number(query) }
          : { username: { [Op.like]: `%${query.replace(/[\\%_]/g, '\\$&')}%` } },
      ],
    },
    limit: 20,
    order: [['id', 'ASC']],
  });
  const followedIds = new Set((await FollowRelation.findAll({
    attributes: ['followed_id'],
    where: { follower_id: userInfo.id, followed_id: { [Op.in]: users.map(user => user.id) } },
    raw: true,
  })).map((row: any) => Number(row.followed_id)));
  successHandler({ ctx, data: { users: users.map((user: any) => ({ ...user.get(), is_following: followedIds.has(Number(user.id)) })) } });
});

userRouter.get('/recommendations', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code || 401, code || 401);
  if (searchRate.consume(`user-recommend:${userInfo.id}`, 60)) throw new CustomError('请求频繁，请稍后重试', 429, 429);
  const gender = String(ctx.query.gender || '').toUpperCase();
  const ageMinRaw = String(ctx.query.age_min || '');
  const ageMaxRaw = String(ctx.query.age_max || '');
  const region = String(ctx.query.region || '');
  if (gender && !['MALE', 'FEMALE'].includes(gender)) throw new CustomError('性别筛选无效', 400, 400);
  const ageMin = ageMinRaw ? Number(ageMinRaw) : undefined;
  const ageMax = ageMaxRaw ? Number(ageMaxRaw) : undefined;
  if ((ageMinRaw && (!Number.isInteger(ageMin) || ageMin! < 18 || ageMin! > 120))
    || (ageMaxRaw && (!Number.isInteger(ageMax) || ageMax! < 18 || ageMax! > 120))
    || (ageMin !== undefined && ageMax !== undefined && ageMax < ageMin)) throw new CustomError('年龄筛选无效', 400, 400);
  if (region && region !== 'same') throw new CustomError('地区筛选无效', 400, 400);
  let matchingIds: number[] | undefined;
  if (gender || ageMin !== undefined || ageMax !== undefined || region) {
    try {
      const result: any = await axiosClient.get(`${REVENUE_API_URL}/v1/users/discovery`, {
        params: { ...(gender ? { gender } : {}), ...(ageMin !== undefined ? { age_min: ageMin } : {}),
          ...(ageMax !== undefined ? { age_max: ageMax } : {}), ...(region ? { region } : {}) },
        headers: { Authorization: ctx.headers.authorization || '' }, timeout: 10000,
      });
      matchingIds = Array.isArray(result.data?.user_ids) ? result.data.user_ids.map(Number).filter(Number.isSafeInteger) : [];
    } catch (error) {
      if (axiosClient.isAxiosError(error) && error.response?.status && error.response.status < 500)
        throw new CustomError(error.response.data?.message || '筛选条件无效', error.response.status, error.response.status);
      throw new CustomError('用户筛选服务暂不可用，请稍后重试', 503, 503);
    }
  }
  const users = await User.findAll({
    attributes: ['id', 'username', 'avatar', 'desc'],
    where: { [Op.and]: [
      { status: 1 },
      { id: { [Op.ne]: userInfo.id } },
      { id: { [Op.notIn]: literal(`(SELECT blocked_id FROM user_block_relation WHERE blocker_id = ${Number(userInfo.id)})`) } },
      { id: { [Op.notIn]: literal(`(SELECT blocker_id FROM user_block_relation WHERE blocked_id = ${Number(userInfo.id)})`) } },
      { id: { [Op.notIn]: literal(`(SELECT followed_id FROM user_follow_relation WHERE follower_id = ${Number(userInfo.id)})`) } },
      ...(matchingIds !== undefined ? [{ id: { [Op.in]: matchingIds } }] : []),
    ] },
    order: [['id', 'DESC']],
    limit: 20,
  });
  successHandler({ ctx, data: { users: users.map((user: any) => ({ ...user.get(), is_following: false })) } });
});

userRouter.get('/relations', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message, code || 401, code || 401);
  const type = String(ctx.query.type || 'following');
  if (!['following', 'followers', 'friends'].includes(type))
    throw new CustomError('关系类型无效', 400, 400);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页游标无效', 400, 400);
  const direction = type === 'followers' ? 'followed_id' : 'follower_id';
  const opposite = type === 'followers' || type === 'friends' ? 'follower_id' : 'followed_id';
  const where: any = type === 'friends'
    ? { followed_id: userInfo.id, follower_id: { [Op.in]: literal(`(SELECT followed_id FROM user_follow_relation WHERE follower_id = ${Number(userInfo.id)})`) } }
    : { [direction]: userInfo.id };
  if (before) where.id = { [Op.lt]: before };
  const relations: any[] = await FollowRelation.findAll({ where, order: [['id', 'DESC']], limit: 51, raw: true });
  const hasMore = relations.length > 50;
  const page = relations.slice(0, 50);
  const userIds = page.map(row => Number(row[opposite]));
  const users: any[] = userIds.length ? await User.findAll({
    attributes: ['id', 'username', 'avatar', 'desc'], where: { id: { [Op.in]: userIds }, status: 1 }, raw: true,
  }) : [];
  const byId = new Map(users.map(user => [Number(user.id), user]));
  const result = page.map(row => byId.get(Number(row[opposite]))).filter(Boolean);
  successHandler({ ctx, data: { users: result, next_before_id: hasMore ? String(page[page.length - 1].id) : '' } });
});

userRouter.post('/visits', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code || 401, code || 401);
  if (searchRate.consume(`profile-visit:${userInfo.id}`, 60)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  const viewedUserId = Number(ctx.request.body?.user_id);
  if (!Number.isSafeInteger(viewedUserId) || viewedUserId < 1 || viewedUserId === Number(userInfo.id))
    throw new CustomError('用户 ID 无效', 400, 400);
  const target = await User.findOne({ attributes: ['id'], where: { id: viewedUserId, status: 1 } });
  if (!target) throw new CustomError('用户不存在或不可用', 404, 404);
  await ProfileVisit.upsert({ visitor_id: userInfo.id, viewed_user_id: viewedUserId, last_seen_at: new Date() });
  successHandler({ ctx, data: { user_id: viewedUserId, recorded: true } });
});

userRouter.get('/visitors', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code || 401, code || 401);
  const visits: any[] = await ProfileVisit.findAll({ where: { viewed_user_id: userInfo.id }, order: [['last_seen_at', 'DESC']], limit: 100, raw: true });
  const visitorIds = visits.map(visit => Number(visit.visitor_id));
  const users: any[] = visitorIds.length ? await User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: visitorIds }, status: 1 }, raw: true }) : [];
  const byId = new Map(users.map((visitor: any): [number, any] => [Number(visitor.id), visitor]));
  successHandler({ ctx, data: { visitors: visits.map(visit => ({
    user: byId.get(Number(visit.visitor_id)) || null,
    last_seen_at: visit.last_seen_at,
  })).filter(visit => visit.user) } });
});

userRouter.post('/follow', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message, code || 401, code || 401);
  if (searchRate.consume(`user-follow:${userInfo.id}`, 60)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  const targetId = Number(ctx.request.body?.user_id);
  if (!Number.isSafeInteger(targetId) || targetId < 1 || targetId === Number(userInfo.id))
    throw new CustomError('用户 ID 无效', 400, 400);
  const target = await User.findOne({ attributes: ['id'], where: { id: targetId, status: 1 } });
  if (!target) throw new CustomError('用户不存在或不可用', 404, 404);
  await FollowRelation.findOrCreate({ where: { follower_id: userInfo.id, followed_id: targetId } });
  successHandler({ ctx, data: { user_id: targetId, is_following: true } });
});

userRouter.post('/unfollow', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message, code || 401, code || 401);
  if (searchRate.consume(`user-follow:${userInfo.id}`, 60)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  const targetId = Number(ctx.request.body?.user_id);
  if (!Number.isSafeInteger(targetId) || targetId < 1)
    throw new CustomError('用户 ID 无效', 400, 400);
  await FollowRelation.destroy({ where: { follower_id: userInfo.id, followed_id: targetId } });
  successHandler({ ctx, data: { user_id: targetId, is_following: false } });
});

userRouter.get('/blocks', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code || 401, code || 401);
  const blocks: any[] = await UserBlock.findAll({ where: { blocker_id: userInfo.id }, order: [['created_at', 'DESC']], limit: 100, raw: true });
  const ids = blocks.map(row => Number(row.blocked_id));
  const users: any[] = ids.length ? await User.findAll({ attributes: ['id', 'username', 'avatar', 'desc'], where: { id: { [Op.in]: ids }, status: 1 }, raw: true }) : [];
  const byId = new Map(users.map((item: any): [number, any] => [Number(item.id), item]));
  successHandler({ ctx, data: { users: blocks.map(row => ({ user: byId.get(Number(row.blocked_id)) || null, blocked_at: row.created_at })).filter(row => row.user) } });
});

userRouter.post('/blocks', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code || 401, code || 401);
  if (searchRate.consume(`user-block:${userInfo.id}`, 60)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  const targetId = Number(ctx.request.body?.user_id);
  if (!Number.isSafeInteger(targetId) || targetId < 1 || targetId === Number(userInfo.id)) throw new CustomError('用户 ID 无效', 400, 400);
  const target = await User.findOne({ attributes: ['id'], where: { id: targetId, status: 1 } });
  if (!target) throw new CustomError('用户不存在或不可用', 404, 404);
  await UserBlock.findOrCreate({ where: { blocker_id: userInfo.id, blocked_id: targetId }, defaults: { created_at: new Date() } });
  successHandler({ ctx, data: { user_id: targetId, blocked: true } });
});

userRouter.delete('/blocks/:id', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code || 401, code || 401);
  if (!/^[1-9][0-9]{0,9}$/.test(ctx.params.id)) throw new CustomError('用户 ID 无效', 400, 400);
  await UserBlock.destroy({ where: { blocker_id: userInfo.id, blocked_id: Number(ctx.params.id) } });
  successHandler({ ctx, data: { user_id: Number(ctx.params.id), blocked: false } });
});

export default userRouter;
