import Router from 'koa-router';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import sharp from 'sharp';
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
import UserSocialProfile from '@/model/userSocialProfile.model';
import UserBlock from '@/model/userBlock.model';
import axiosClient from 'axios';
import { REVENUE_API_URL } from '@/config/secret';
import { createUserNotification } from '@/service/push-notifications';
import { isSmsLoginConfigured, loginWithSms, sendSmsLoginCode } from '@/service/sms-auth';

const userRouter = new Router({ prefix: '/user' });
const searchRate = new RequestRateLimiter();
const AVATAR_LIMIT = 5 * 1024 * 1024;
const AVATAR_NAME = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.jpg$/i;
const socialIncomeOptions = new Set(['UNDER_50K', '50K_100K', '100K_200K', '200K_500K', 'OVER_500K']);
const maritalStatusOptions = new Set(['SINGLE', 'IN_RELATIONSHIP', 'MARRIED', 'DIVORCED', 'WIDOWED']);

async function currentUser(ctx) {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id) throw new CustomError(auth.message || '请重新登录', auth.code || 401, auth.code || 401);
  return auth.userInfo;
}

function avatarRoot() {
  return path.resolve(process.env.USER_AVATAR_DIR || path.join(process.cwd(), 'data', 'user-avatars'));
}

function avatarFile(userId: number, filename: string) {
  if (!Number.isSafeInteger(userId) || userId < 1 || !AVATAR_NAME.test(filename)) throw new CustomError('头像不存在', 404, 404);
  const root = avatarRoot();
  const directory = path.join(root, String(userId));
  const target = path.resolve(directory, filename);
  if (target !== path.join(directory, filename)) throw new CustomError('头像不存在', 404, 404);
  return target;
}

function storedAvatarFile(userId: number, value: unknown) {
  if (typeof value !== 'string') return null;
  const prefix = `/user/avatars/${userId}/`;
  if (!value.startsWith(prefix)) return null;
  const filename = value.slice(prefix.length);
  return AVATAR_NAME.test(filename) ? avatarFile(userId, filename) : null;
}

async function readAvatarBytes(ctx) {
  if (String(ctx.get('content-type')).split(';')[0].trim().toLowerCase() !== 'application/octet-stream')
    throw new CustomError('请以二进制方式上传头像', 415, 415);
  const declaredSize = Number(ctx.req.headers['content-length'] || 0);
  if (Number.isFinite(declaredSize) && declaredSize > AVATAR_LIMIT) throw new CustomError('头像不能超过 5MB', 413, 413);
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of ctx.req) {
    const buffer = Buffer.from(chunk);
    size += buffer.length;
    if (size > AVATAR_LIMIT) throw new CustomError('头像不能超过 5MB', 413, 413);
    chunks.push(buffer);
  }
  if (!size) throw new CustomError('头像图片为空', 400, 400);
  return Buffer.concat(chunks);
}

async function normalizeAvatar(input: Buffer) {
  try {
    const options = { limitInputPixels: 25000000, failOn: 'warning' as const };
    const metadata = await sharp(input, options).metadata();
    if (!['jpeg', 'png', 'webp'].includes(metadata.format || '') || (metadata.pages || 1) > 1) throw Error('format');
    const image = await sharp(input, options).rotate().resize(1024, 1024, { fit: 'cover', withoutEnlargement: true })
      .jpeg({ quality: 84, progressive: true }).toBuffer();
    if (image.length > 2 * 1024 * 1024) throw Error('size');
    return image;
  } catch {
    throw new CustomError('头像图片无效，仅支持静态 JPEG、PNG、WebP', 400, 400);
  }
}

async function writeAvatar(userId: number, filename: string, image: Buffer) {
  const target = avatarFile(userId, filename);
  const partial = `${target}.partial`;
  await fs.promises.mkdir(path.dirname(target), { recursive: true, mode: 0o700 });
  try {
    await fs.promises.writeFile(partial, image, { flag: 'wx', mode: 0o600 });
    await fs.promises.rename(partial, target);
  } catch (error) {
    await fs.promises.rm(partial, { force: true }).catch(() => undefined);
    throw error;
  }
  return target;
}

const optionalSocialText = (value: any, label: string, maxLength: number): string | null => {
  if (value == null) return null;
  if (typeof value !== 'string') throw new CustomError(`${label}格式无效`, 400, 400);
  const text = value.trim();
  if (Array.from(text).length > maxLength) throw new CustomError(`${label}不能超过 ${maxLength} 个字符`, 400, 400);
  return text || null;
};

const optionalSocialNumber = (value: any, label: string, min: number, max: number): number | null => {
  if (value == null) return null;
  if (!Number.isInteger(value) || value < min || value > max) throw new CustomError(`${label}范围无效`, 400, 400);
  return value;
};

const socialProfileData = (row: any, publicOnly = false) => {
  const profile = row?.get?.({ plain: true }) || {};
  const isPublic = profile.is_public === true || Number(profile.is_public) === 1;
  if (publicOnly && !isPublic) return { is_public: false };
  let tags: string[] = [];
  try {
    const parsed = JSON.parse(profile.tags_json || '[]');
    if (Array.isArray(parsed)) tags = parsed.filter((tag: any) => typeof tag === 'string');
  } catch (_) { /* Empty or legacy data has no tags. */ }
  return {
    height_cm: profile.height_cm == null ? null : Number(profile.height_cm),
    weight_kg: profile.weight_kg == null ? null : Number(profile.weight_kg),
    occupation: profile.occupation || null,
    annual_income: profile.annual_income || null,
    city: profile.city || null,
    marital_status: profile.marital_status || null,
    profile_tags: tags,
    dating_preferences: profile.dating_preferences || null,
    is_public: isPublic,
  };
};

userRouter.get('/auth-methods', async ctx => {
  successHandler({ ctx, data: { password: true, sms: isSmsLoginConfigured() } });
});
userRouter.post('/sms/send-code', async ctx => {
  await sendSmsLoginCode(ctx.request.body?.phone, ctx);
  successHandler({ ctx, message: '验证码已提交，请查收短信' });
});
userRouter.post('/sms/login', async ctx => {
  const token = await loginWithSms(ctx.request.body?.phone, ctx.request.body?.code);
  successHandler({ ctx, data: token, message: '登录成功' });
});
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
  successHandler({ ctx, data: { id: userInfo.id, username: userInfo.username, status: userInfo.status, avatar: userInfo.avatar || null } });
});

userRouter.post('/avatar', async ctx => {
  const user = await currentUser(ctx);
  const retryAfter = searchRate.consume(`avatar-upload:${user.id}`, 6);
  if (retryAfter) {
    ctx.set('Retry-After', String(retryAfter));
    throw new CustomError('头像上传过于频繁，请稍后重试', 429, 429);
  }
  const image = await normalizeAvatar(await readAvatarBytes(ctx));
  const filename = `${crypto.randomUUID()}.jpg`;
  const avatarPath = `/user/avatars/${user.id}/${filename}`;
  const target = await writeAvatar(Number(user.id), filename, image);
  try {
    const account: any = await User.findByPk(user.id);
    if (!account || Number(account.status) !== 1) throw new CustomError('账号不可用', 404, 404);
    const previousFile = storedAvatarFile(Number(user.id), account.avatar);
    await account.update({ avatar: avatarPath });
    if (previousFile && previousFile !== target) await fs.promises.rm(previousFile, { force: true }).catch(() => undefined);
  } catch (error) {
    await fs.promises.rm(target, { force: true }).catch(() => undefined);
    throw error;
  }
  successHandler({ ctx, data: { avatar_path: avatarPath } });
});

userRouter.get('/avatars/:id/:filename', async ctx => {
  const viewer = await currentUser(ctx);
  if (!/^[1-9][0-9]{0,9}$/.test(ctx.params.id) || !AVATAR_NAME.test(ctx.params.filename))
    throw new CustomError('头像不存在', 404, 404);
  const ownerId = Number(ctx.params.id);
  if (!Number.isSafeInteger(ownerId)) throw new CustomError('头像不存在', 404, 404);
  if (ownerId !== Number(viewer.id)) {
    const blocked = await UserBlock.findOne({ where: { [Op.or]: [
      { blocker_id: viewer.id, blocked_id: ownerId },
      { blocker_id: ownerId, blocked_id: viewer.id },
    ] } });
    if (blocked) throw new CustomError('头像不存在', 404, 404);
  }
  const avatarPath = `/user/avatars/${ownerId}/${ctx.params.filename}`;
  const owner: any = await User.findOne({ attributes: ['id', 'avatar'], where: { id: ownerId, status: 1 } });
  if (!owner || owner.avatar !== avatarPath) throw new CustomError('头像不存在', 404, 404);
  const file = avatarFile(ownerId, ctx.params.filename);
  if (!fs.existsSync(file)) throw new CustomError('头像暂时不可用，请稍后重试', 503, 503);
  ctx.type = 'image/jpeg';
  ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.set('Cache-Control', 'private,max-age=60');
  ctx.body = fs.createReadStream(file);
});

userRouter.get('/social-profile', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code || 401, code || 401);
  const profile = await UserSocialProfile.findByPk(userInfo.id);
  successHandler({ ctx, data: socialProfileData(profile) });
});

userRouter.put('/social-profile', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code || 401, code || 401);
  if (searchRate.consume(`social-profile-write:${userInfo.id}`, 20)) throw new CustomError('保存过于频繁，请稍后重试', 429, 429);
  const body: any = ctx.request.body;
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw new CustomError('资料格式无效', 400, 400);
  const height = optionalSocialNumber(body.height_cm, '身高', 80, 250);
  const weight = optionalSocialNumber(body.weight_kg, '体重', 20, 300);
  const occupation = optionalSocialText(body.occupation, '工作', 50);
  const city = optionalSocialText(body.city, '所在地', 60);
  const datingPreferences = optionalSocialText(body.dating_preferences, '征友条件', 300);
  const annualIncome = optionalSocialText(body.annual_income, '年收入', 16);
  const maritalStatus = optionalSocialText(body.marital_status, '婚姻状况', 24);
  if (annualIncome && !socialIncomeOptions.has(annualIncome)) throw new CustomError('年收入选项无效', 400, 400);
  if (maritalStatus && !maritalStatusOptions.has(maritalStatus)) throw new CustomError('婚姻状况选项无效', 400, 400);
  if (!Array.isArray(body.profile_tags) || body.profile_tags.length > 10) throw new CustomError('最多填写 10 个标签', 400, 400);
  const tags = body.profile_tags.map((tag: any) => optionalSocialText(tag, '标签', 12)).filter(Boolean) as string[];
  if (tags.length !== body.profile_tags.length) throw new CustomError('标签不能为空', 400, 400);
  if (new Set(tags).size !== tags.length) throw new CustomError('请移除重复标签', 400, 400);
  if (typeof body.is_public !== 'boolean') throw new CustomError('请确认资料公开设置', 400, 400);
  await UserSocialProfile.upsert({
    user_id: userInfo.id,
    height_cm: height,
    weight_kg: weight,
    occupation,
    annual_income: annualIncome,
    city,
    marital_status: maritalStatus,
    tags_json: JSON.stringify(tags),
    dating_preferences: datingPreferences,
    is_public: body.is_public,
  });
  const profile = await UserSocialProfile.findByPk(userInfo.id);
  successHandler({ ctx, data: socialProfileData(profile) });
});

userRouter.get('/social-profile/:id', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code || 401, code || 401);
  if (!/^[1-9][0-9]{0,9}$/.test(ctx.params.id)) throw new CustomError('用户 ID 无效', 400, 400);
  const targetId = Number(ctx.params.id);
  const target = await User.findOne({ attributes: ['id'], where: { id: targetId, status: 1 } });
  if (!target) throw new CustomError('用户不存在或不可用', 404, 404);
  if (targetId !== Number(userInfo.id)) {
    const blocked = await UserBlock.findOne({ where: { [Op.or]: [
      { blocker_id: userInfo.id, blocked_id: targetId },
      { blocker_id: targetId, blocked_id: userInfo.id },
    ] } });
    if (blocked) throw new CustomError('用户不存在或不可用', 404, 404);
  }
  const profile = await UserSocialProfile.findByPk(targetId);
  successHandler({ ctx, data: socialProfileData(profile, true) });
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
  let matchingIds: number[];
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
  const users = await User.findAll({
    attributes: ['id', 'username', 'avatar', 'desc'],
    where: { [Op.and]: [
      { status: 1 },
      { id: { [Op.ne]: userInfo.id } },
      { id: { [Op.notIn]: literal(`(SELECT blocked_id FROM user_block_relation WHERE blocker_id = ${Number(userInfo.id)})`) } },
      { id: { [Op.notIn]: literal(`(SELECT blocker_id FROM user_block_relation WHERE blocked_id = ${Number(userInfo.id)})`) } },
      { id: { [Op.notIn]: literal(`(SELECT followed_id FROM user_follow_relation WHERE follower_id = ${Number(userInfo.id)})`) } },
      { id: { [Op.in]: matchingIds } },
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
  await createUserNotification(viewedUserId, {
    kind: 'VISITOR',
    actor_id: Number(userInfo.id),
    title: '有人访问了你的主页',
    body: '点击查看最近访客',
  });
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
