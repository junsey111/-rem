import Router from 'koa-router';
import axiosClient from 'axios';
import { Op } from 'sequelize';

import { authJwt } from '@/app/auth/authJwt';
import { requireOperations } from '@/app/auth/operations';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import User from '@/model/user.model';
import sequelize from '@/config/mysql';
import { Guild, GuildApplication, GuildMember } from '@/model/guild.model';
import { NobleLevel, NobleMembership } from '@/model/noble.model';
import { CostumeItem, UserCostume } from '@/model/costume.model';
import axios from '@/utils/request';
import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';

const router = new Router();

async function currentUser(ctx: any) {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id) throw new CustomError(auth.message || '请重新登录', auth.code || 401, auth.code || 401);
  return auth.userInfo;
}

function id(value: unknown, label: string) {
  const text = String(value || '');
  if (!/^[1-9][0-9]{0,19}$/.test(text)) throw new CustomError(`${label}无效`, 400, 400);
  return text;
}

function text(value: unknown, max: number, required = false) {
  const result = typeof value === 'string' ? value.trim() : '';
  if ((required && !result) || Array.from(result).length > max) throw new CustomError('输入内容无效', 400, 400);
  return result;
}

const uuidV4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

async function chargeCatalog(userId: number, requestId: string, itemType: 'NOBLE' | 'COSTUME', itemId: string, priceCoins: number) {
  try {
    return await axios.post(`${REVENUE_API_URL}/v1/internal/catalog/purchases`, {
      user_id: userId, request_id: requestId, item_type: itemType, item_id: itemId, price_coins: priceCoins,
    }, { headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY }, timeout: 10000 });
  } catch (error) {
    if (axiosClient.isAxiosError(error)) {
      const status = error.response?.status;
      const code = error.response?.data?.code;
      const message = code === 'INSUFFICIENT_BALANCE' ? '金币余额不足，请先充值' : code === 'PURCHASE_REVERSED' ? '该购买单已撤销，请重新发起' : error.response?.data?.message;
      if (status && status < 500 && message) throw new CustomError(message, status, status);
    }
    throw new CustomError('购买结果尚未确认，请保留原单号稍后重试', 503, 503);
  }
}

async function reverseCatalog(userId: number, requestId: string, reason: string) {
  try {
    await axios.post(`${REVENUE_API_URL}/v1/internal/catalog/purchases/${encodeURIComponent(requestId)}`, { user_id: userId, reason }, {
      headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY }, timeout: 10000,
    });
  } catch {
    throw new CustomError('金币扣除已确认，但权益未发放；请保留购买单号联系客服处理', 503, 503);
  }
}

function purchaseRequestId(value: unknown) {
  const requestId = typeof value === 'string' ? value.trim().toLowerCase() : '';
  if (!uuidV4.test(requestId)) throw new CustomError('购买单号无效，请使用 UUID v4', 400, 400);
  return requestId;
}

async function guildView(row: any) {
  const memberCount = await GuildMember.count({ where: { guild_id: row.id, status: 'ACTIVE' } });
  const owner: any = await User.findOne({ attributes: ['id', 'username', 'avatar'], where: { id: row.owner_id }, raw: true });
  return { id: String(row.id), owner_id: Number(row.owner_id), name: row.name, description: row.description,
    status: row.status, member_count: memberCount, owner: owner || null, created_at: row.created_at };
}

router.get('/guilds', async ctx => {
  await currentUser(ctx);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页游标无效', 400, 400);
  const where: any = { status: 'ACTIVE' };
  if (before) where.id = { [Op.lt]: before };
  const rows: any[] = await Guild.findAll({ where, order: [['id', 'DESC']], limit: 21, raw: true });
  const hasMore = rows.length > 20;
  const page = rows.slice(0, 20);
  successHandler({ ctx, data: { guilds: await Promise.all(page.map(guildView)), next_before_id: hasMore ? String(page[page.length - 1].id) : '' } });
});

router.get('/guilds/me', async ctx => {
  const user = await currentUser(ctx);
  const member: any = await GuildMember.findOne({ where: { user_id: user.id, status: 'ACTIVE' }, order: [['id', 'DESC']], raw: true });
  if (!member) return successHandler({ ctx, data: { guild: null, membership: null } });
  const guild: any = await Guild.findOne({ where: { id: member.guild_id, status: 'ACTIVE' }, raw: true });
  successHandler({ ctx, data: { guild: guild ? await guildView(guild) : null, membership: guild ? { role: member.role, joined_at: member.created_at } : null } });
});

router.post('/guilds', async ctx => {
  const user = await currentUser(ctx);
  const name = text(ctx.request.body?.name, 40, true);
  const description = text(ctx.request.body?.description, 255);
  const existing = await GuildMember.findOne({ where: { user_id: user.id, status: 'ACTIVE' } });
  if (existing) throw new CustomError('你已经加入公会，请先退出当前公会', 409, 409);
  const guild = await sequelize.transaction(async transaction => {
    const created: any = await Guild.create({ owner_id: user.id, name, description, status: 'ACTIVE' }, { transaction });
    await GuildMember.create({ guild_id: created.id, user_id: user.id, role: 'OWNER', status: 'ACTIVE' }, { transaction });
    return created;
  });
  successHandler({ ctx, data: { guild: await guildView(guild) } });
});

router.post('/guilds/:id/applications', async ctx => {
  const user = await currentUser(ctx);
  const guildId = id(ctx.params.id, '公会编号');
  const guild: any = await Guild.findOne({ where: { id: guildId, status: 'ACTIVE' } });
  if (!guild) throw new CustomError('公会不存在或已关闭', 404, 404);
  const member = await GuildMember.findOne({ where: { user_id: user.id, status: 'ACTIVE' } });
  if (member) throw new CustomError('你已经加入公会', 409, 409);
  const message = text(ctx.request.body?.message, 255);
  const [application, created] = await GuildApplication.findOrCreate({
    where: { guild_id: guildId, user_id: user.id },
    defaults: { guild_id: guildId, user_id: user.id, message, status: 'PENDING' },
  });
  if (!created) {
    if (application.get('status') === 'PENDING') throw new CustomError('申请已提交，请等待审核', 409, 409);
    await application.update({ message, status: 'PENDING', decided_by: null, decided_at: null });
  }
  successHandler({ ctx, data: { application: { id: String(application.get('id')), guild_id: guildId, status: 'PENDING' } } });
});

router.get('/guilds/:id/applications', async ctx => {
  const user = await currentUser(ctx);
  const guildId = id(ctx.params.id, '公会编号');
  const manager = await GuildMember.findOne({ where: { guild_id: guildId, user_id: user.id, status: 'ACTIVE', role: { [Op.in]: ['OWNER', 'ADMIN'] } } });
  if (!manager) throw new CustomError('没有公会管理权限', 403, 403);
  const rows: any[] = await GuildApplication.findAll({ where: { guild_id: guildId, status: 'PENDING' }, order: [['id', 'ASC']], limit: 100, raw: true });
  const users: any[] = rows.length ? await User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: rows.map(row => row.user_id) } }, raw: true }) : [];
  const byId = new Map(users.map(user => [Number(user.id), user]));
  successHandler({ ctx, data: { applications: rows.map(row => ({ id: String(row.id), user_id: Number(row.user_id), user: byId.get(Number(row.user_id)) || null, message: row.message, created_at: row.created_at })) } });
});

router.post('/guilds/:id/applications/:applicationId/decision', async ctx => {
  const user = await currentUser(ctx);
  const guildId = id(ctx.params.id, '公会编号');
  const applicationId = id(ctx.params.applicationId, '申请编号');
  const decision = String(ctx.request.body?.decision || '').toUpperCase();
  if (!['APPROVED', 'REJECTED'].includes(decision)) throw new CustomError('审核结果无效', 400, 400);
  const manager = await GuildMember.findOne({ where: { guild_id: guildId, user_id: user.id, status: 'ACTIVE', role: { [Op.in]: ['OWNER', 'ADMIN'] } } });
  if (!manager) throw new CustomError('没有公会管理权限', 403, 403);
  const result = await sequelize.transaction(async transaction => {
    const application: any = await GuildApplication.findOne({ where: { id: applicationId, guild_id: guildId, status: 'PENDING' }, transaction, lock: transaction.LOCK.UPDATE });
    if (!application) throw new CustomError('申请不存在或已处理', 404, 404);
    if (decision === 'REJECTED') {
      await application.update({ status: 'REJECTED', decided_by: user.id, decided_at: new Date() }, { transaction });
      return { status: decision };
    }
    const current = await GuildMember.findOne({ where: { user_id: application.user_id, status: 'ACTIVE' }, transaction, lock: transaction.LOCK.UPDATE });
    if (current) throw new CustomError('申请人已经加入其他公会', 409, 409);
    const previous = await GuildMember.findOne({ where: { guild_id: guildId, user_id: application.user_id }, transaction, lock: transaction.LOCK.UPDATE });
    if (previous) await previous.update({ role: 'MEMBER', status: 'ACTIVE' }, { transaction });
    else await GuildMember.create({ guild_id: guildId, user_id: application.user_id, role: 'MEMBER', status: 'ACTIVE' }, { transaction });
    await application.update({ status: 'APPROVED', decided_by: user.id, decided_at: new Date() }, { transaction });
    return { status: decision };
  });
  successHandler({ ctx, data: { application_id: applicationId, ...result } });
});

router.post('/guilds/:id/leave', async ctx => {
  const user = await currentUser(ctx);
  const guildId = id(ctx.params.id, '公会编号');
  const member: any = await GuildMember.findOne({ where: { guild_id: guildId, user_id: user.id, status: 'ACTIVE' } });
  if (!member) throw new CustomError('你不在该公会', 404, 404);
  if (member.role === 'OWNER') throw new CustomError('会长请先转让公会或关闭公会', 409, 409);
  await member.update({ status: 'LEFT' });
  successHandler({ ctx, data: { left: true } });
});

router.get('/nobles/levels', async ctx => {
  const levels: any[] = await NobleLevel.findAll({ where: { enabled: true }, order: [['level', 'ASC']], raw: true });
  successHandler({ ctx, data: { levels } });
});

router.get('/nobles/me', async ctx => {
  const user = await currentUser(ctx);
  const membership: any = await NobleMembership.findOne({ where: { user_id: user.id, status: 'ACTIVE', [Op.or]: [{ expires_at: null }, { expires_at: { [Op.gt]: new Date() } }] }, order: [['level', 'DESC'], ['id', 'DESC']], raw: true });
  if (!membership) return successHandler({ ctx, data: { membership: null } });
  const level: any = await NobleLevel.findByPk(membership.level, { raw: true });
  successHandler({ ctx, data: { membership: { ...membership, id: String(membership.id), level: level || null } } });
});

router.post('/nobles/:level/purchase', async ctx => {
  const user = await currentUser(ctx);
  const levelId = id(ctx.params.level, '贵族等级');
  const requestId = purchaseRequestId(ctx.request.body?.request_id);
  const existing: any = await NobleMembership.findOne({ where: { user_id: user.id, purchase_request_id: requestId }, raw: true });
  if (existing) {
    if (String(existing.level) !== levelId) throw new CustomError('购买单号已用于其他贵族等级，请重新发起', 409, 409);
    return successHandler({ ctx, data: { purchase_request_id: requestId, membership: { ...existing, id: String(existing.id) }, idempotency: 'replayed' } });
  }
  const level: any = await NobleLevel.findOne({ where: { level: levelId, enabled: true }, raw: true });
  if (!level) throw new CustomError('贵族等级暂未开放', 404, 404);
  await chargeCatalog(Number(user.id), requestId, 'NOBLE', levelId, Number(level.price_coins));
  try {
    const membership: any = await NobleMembership.create({ user_id: user.id, level: levelId, status: 'ACTIVE', starts_at: new Date(),
      expires_at: new Date(Date.now() + Number(level.duration_days) * 86400000), granted_by: null, purchase_request_id: requestId });
    successHandler({ ctx, data: { purchase_request_id: requestId, membership: { ...membership.get(), id: String(membership.id), level }, idempotency: 'created', price_coins: Number(level.price_coins) } });
  } catch (error) {
    const winner: any = await NobleMembership.findOne({ where: { user_id: user.id, purchase_request_id: requestId }, raw: true });
    if (winner) return successHandler({ ctx, data: { purchase_request_id: requestId, membership: { ...winner, id: String(winner.id), level }, idempotency: 'replayed' } });
    await reverseCatalog(Number(user.id), requestId, '贵族权益写入失败');
    throw error;
  }
});

router.get('/costumes/catalog', async ctx => {
  const category = String(ctx.query.category || '').trim();
  const where: any = { enabled: true };
  if (category) where.category = category;
  const items: any[] = await CostumeItem.findAll({ where, order: [['category', 'ASC'], ['id', 'ASC']], raw: true });
  successHandler({ ctx, data: { items } });
});

router.get('/costumes/me', async ctx => {
  const user = await currentUser(ctx);
  const rows: any[] = await UserCostume.findAll({ where: { user_id: user.id }, order: [['equipped', 'DESC'], ['id', 'DESC']], raw: true });
  const items: any[] = rows.length ? await CostumeItem.findAll({ where: { id: { [Op.in]: rows.map(row => row.costume_id) } }, raw: true }) : [];
  const byId = new Map(items.map(item => [String(item.id), item]));
  successHandler({ ctx, data: { items: rows.map(row => ({ ...row, id: String(row.id), costume: byId.get(String(row.costume_id)) || null })).filter(row => row.costume) } });
});

router.post('/costumes/:id/purchase', async ctx => {
  const user = await currentUser(ctx);
  const costumeId = id(ctx.params.id, '装扮编号');
  const requestId = purchaseRequestId(ctx.request.body?.request_id);
  const existingByRequest: any = await UserCostume.findOne({ where: { user_id: user.id, purchase_request_id: requestId }, raw: true });
  if (existingByRequest) {
    if (String(existingByRequest.costume_id) !== costumeId) throw new CustomError('购买单号已用于其他装扮，请重新发起', 409, 409);
    return successHandler({ ctx, data: { purchase_request_id: requestId, costume_id: costumeId, idempotency: 'replayed' } });
  }
  const item: any = await CostumeItem.findOne({ where: { id: costumeId, enabled: true }, raw: true });
  if (!item) throw new CustomError('装扮暂未上架', 404, 404);
  const owned = await UserCostume.findOne({ where: { user_id: user.id, costume_id: costumeId } });
  if (owned) throw new CustomError('你已经拥有该装扮', 409, 409);
  await chargeCatalog(Number(user.id), requestId, 'COSTUME', costumeId, Number(item.price_coins));
  try {
    const ownedItem: any = await UserCostume.create({ user_id: user.id, costume_id: costumeId, equipped: false, acquired_at: new Date(), purchase_request_id: requestId });
    successHandler({ ctx, data: { purchase_request_id: requestId, costume_id: costumeId, owned_id: String(ownedItem.id), idempotency: 'created', price_coins: Number(item.price_coins) } });
  } catch (error) {
    const winner: any = await UserCostume.findOne({ where: { user_id: user.id, purchase_request_id: requestId }, raw: true });
    if (winner) return successHandler({ ctx, data: { purchase_request_id: requestId, costume_id: costumeId, owned_id: String(winner.id), idempotency: 'replayed' } });
    const alreadyOwned = await UserCostume.findOne({ where: { user_id: user.id, costume_id: costumeId } });
    if (alreadyOwned) {
      await reverseCatalog(Number(user.id), requestId, '装扮已被其他请求购买');
      throw new CustomError('你已经拥有该装扮', 409, 409);
    }
    await reverseCatalog(Number(user.id), requestId, '装扮权益写入失败');
    throw error;
  }
});

router.post('/costumes/:id/equip', async ctx => {
  const user = await currentUser(ctx);
  const costumeId = id(ctx.params.id, '装扮编号');
  const owned: any = await UserCostume.findOne({ where: { user_id: user.id, costume_id: costumeId } });
  if (!owned) throw new CustomError('你尚未拥有该装扮', 404, 404);
  await sequelize.transaction(async transaction => {
    await UserCostume.update({ equipped: false }, { where: { user_id: user.id }, transaction });
    await owned.update({ equipped: true }, { transaction });
  });
  successHandler({ ctx, data: { costume_id: costumeId, equipped: true } });
});

router.get('/operations/nobles', async ctx => {
  await requireOperations(ctx);
  const levels = await NobleLevel.findAll({ order: [['level', 'ASC']] });
  successHandler({ ctx, data: { levels } });
});

router.put('/operations/nobles/:level', async ctx => {
  const operator = await requireOperations(ctx);
  const level = id(ctx.params.level, '贵族等级');
  if (Number(level) > 100) throw new CustomError('贵族等级无效', 400, 400);
  const name = text(ctx.request.body?.name, 40, true);
  const description = text(ctx.request.body?.description, 255);
  const priceCoins = Number(ctx.request.body?.price_coins);
  const durationDays = Number(ctx.request.body?.duration_days);
  if (!Number.isInteger(priceCoins) || priceCoins < 0 || priceCoins > 100000000 || !Number.isInteger(durationDays) || durationDays < 1 || durationDays > 3650 || typeof ctx.request.body?.enabled !== 'boolean') throw new CustomError('贵族配置无效', 400, 400);
  const [row] = await NobleLevel.findOrCreate({ where: { level }, defaults: { level, name, description, price_coins: priceCoins, duration_days: durationDays, enabled: ctx.request.body.enabled, updated_by: operator.id } });
  if (row) await row.update({ name, description, price_coins: priceCoins, duration_days: durationDays, enabled: ctx.request.body.enabled, updated_by: operator.id });
  successHandler({ ctx, data: { level: row } });
});

router.get('/operations/costumes', async ctx => {
  await requireOperations(ctx);
  const items = await CostumeItem.findAll({ order: [['category', 'ASC'], ['id', 'DESC']] });
  successHandler({ ctx, data: { items } });
});

router.post('/operations/costumes', async ctx => {
  const operator = await requireOperations(ctx);
  const category = text(ctx.request.body?.category, 32, true);
  const name = text(ctx.request.body?.name, 80, true);
  const description = text(ctx.request.body?.description, 255);
  const assetUrl = text(ctx.request.body?.asset_url, 512);
  const priceCoins = Number(ctx.request.body?.price_coins);
  if (!Number.isInteger(priceCoins) || priceCoins < 0 || priceCoins > 100000000 || typeof ctx.request.body?.enabled !== 'boolean') throw new CustomError('装扮配置无效', 400, 400);
  const item = await CostumeItem.create({ category, name, description, asset_url: assetUrl, price_coins: priceCoins, enabled: ctx.request.body.enabled, updated_by: operator.id });
  successHandler({ ctx, data: { item } });
});

router.put('/operations/costumes/:id', async ctx => {
  const operator = await requireOperations(ctx);
  const costumeId = id(ctx.params.id, '装扮编号');
  const item: any = await CostumeItem.findByPk(costumeId);
  if (!item) throw new CustomError('装扮不存在', 404, 404);
  const category = text(ctx.request.body?.category, 32, true);
  const name = text(ctx.request.body?.name, 80, true);
  const description = text(ctx.request.body?.description, 255);
  const assetUrl = text(ctx.request.body?.asset_url, 512);
  const priceCoins = Number(ctx.request.body?.price_coins);
  if (!Number.isInteger(priceCoins) || priceCoins < 0 || priceCoins > 100000000 || typeof ctx.request.body?.enabled !== 'boolean') throw new CustomError('装扮配置无效', 400, 400);
  await item.update({ category, name, description, asset_url: assetUrl, price_coins: priceCoins, enabled: ctx.request.body.enabled, updated_by: operator.id });
  successHandler({ ctx, data: { item } });
});

router.get('/operations/guilds', async ctx => {
  await requireOperations(ctx);
  const rows: any[] = await Guild.findAll({ order: [['id', 'DESC']], limit: 200, raw: true });
  successHandler({ ctx, data: { guilds: await Promise.all(rows.map(guildView)) } });
});

router.post('/operations/guilds/:id/status', async ctx => {
  const operator = await requireOperations(ctx);
  const guildId = id(ctx.params.id, '公会编号');
  const status = String(ctx.request.body?.status || '').toUpperCase();
  if (!['ACTIVE', 'SUSPENDED', 'CLOSED'].includes(status)) throw new CustomError('公会状态无效', 400, 400);
  const guild: any = await Guild.findByPk(guildId);
  if (!guild) throw new CustomError('公会不存在', 404, 404);
  await guild.update({ status });
  if (status !== 'ACTIVE') await GuildApplication.update({ status: 'CANCELLED', decided_by: operator.id, decided_at: new Date() }, { where: { guild_id: guildId, status: 'PENDING' } });
  successHandler({ ctx, data: { guild_id: guildId, status } });
});

router.get('/operations/guilds/:id', async ctx => {
  await requireOperations(ctx);
  const guildId = id(ctx.params.id, '公会编号');
  const guild: any = await Guild.findByPk(guildId, { raw: true });
  if (!guild) throw new CustomError('公会不存在', 404, 404);
  const members: any[] = await GuildMember.findAll({ where: { guild_id: guildId, status: 'ACTIVE' }, order: [['role', 'ASC'], ['id', 'ASC']], raw: true });
  const applications: any[] = await GuildApplication.findAll({ where: { guild_id: guildId, status: 'PENDING' }, order: [['id', 'ASC']], raw: true });
  const userIds = [...new Set([...members, ...applications].map(row => Number(row.user_id)))];
  const users: any[] = userIds.length ? await User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: userIds } }, raw: true }) : [];
  const byId = new Map(users.map(user => [Number(user.id), user]));
  successHandler({ ctx, data: {
    guild: await guildView(guild),
    members: members.map(row => ({ ...row, id: String(row.id), user: byId.get(Number(row.user_id)) || null })),
    applications: applications.map(row => ({ ...row, id: String(row.id), user: byId.get(Number(row.user_id)) || null })),
  } });
});

router.post('/operations/guilds/:id/applications/:applicationId', async ctx => {
  const operator = await requireOperations(ctx);
  const guildId = id(ctx.params.id, '公会编号');
  const applicationId = id(ctx.params.applicationId, '申请编号');
  const decision = String(ctx.request.body?.decision || '').toUpperCase();
  if (!['APPROVED', 'REJECTED'].includes(decision)) throw new CustomError('审核结果无效', 400, 400);
  const result = await sequelize.transaction(async transaction => {
    const application: any = await GuildApplication.findOne({ where: { id: applicationId, guild_id: guildId, status: 'PENDING' }, transaction, lock: transaction.LOCK.UPDATE });
    if (!application) throw new CustomError('申请不存在或已处理', 404, 404);
    if (decision === 'REJECTED') {
      await application.update({ status: 'REJECTED', decided_by: operator.id, decided_at: new Date() }, { transaction });
      return { status: decision };
    }
    const current = await GuildMember.findOne({ where: { user_id: application.user_id, status: 'ACTIVE' }, transaction, lock: transaction.LOCK.UPDATE });
    if (current) throw new CustomError('申请人已经加入其他公会', 409, 409);
    const previous = await GuildMember.findOne({ where: { guild_id: guildId, user_id: application.user_id }, transaction, lock: transaction.LOCK.UPDATE });
    if (previous) await previous.update({ role: 'MEMBER', status: 'ACTIVE' }, { transaction });
    else await GuildMember.create({ guild_id: guildId, user_id: application.user_id, role: 'MEMBER', status: 'ACTIVE' }, { transaction });
    await application.update({ status: 'APPROVED', decided_by: operator.id, decided_at: new Date() }, { transaction });
    return { status: decision };
  });
  successHandler({ ctx, data: { guild_id: guildId, application_id: applicationId, ...result } });
});

export default router;
