import Router from 'koa-router';
import { Op } from 'sequelize';

import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import NotificationPreference from '@/model/notificationPreference.model';
import { PlatformAnnouncement, UserAnnouncementRead } from '@/model/platformAnnouncement.model';
import { UserNotification, UserPushToken } from '@/model/userNotification.model';

const router = new Router({ prefix: '/user' });

async function currentUser(ctx) {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id)
    throw new CustomError(auth.message || '请重新登录', auth.code || 401, auth.code || 401);
  return auth.userInfo;
}

router.get('/announcements', async ctx => {
  const user = await currentUser(ctx);
  const announcements: any[] = await PlatformAnnouncement.findAll({
    where: { enabled: true }, order: [['announcement_id', 'DESC']], limit: 100, raw: true,
  });
  const ids = announcements.map(row => row.announcement_id);
  const reads: any[] = ids.length ? await UserAnnouncementRead.findAll({
    where: { user_id: user.id, announcement_id: { [Op.in]: ids } }, raw: true,
  }) : [];
  const readAt = new Map(reads.map(row => [String(row.announcement_id), row.read_at]));
  successHandler({ ctx, data: { announcements: announcements.map(row => ({
    id: String(row.announcement_id), title: row.title, summary: row.summary, content: row.content,
    created_at: row.created_at, read_at: readAt.get(String(row.announcement_id)) || null,
  })) } });
});

router.post('/announcements/:id/read', async ctx => {
  const user = await currentUser(ctx);
  const id = ctx.params.id;
  if (!/^[1-9][0-9]{0,19}$/.test(id)) throw new CustomError('公告 ID 无效', 400, 400);
  const announcement = await PlatformAnnouncement.findOne({ where: { announcement_id: id, enabled: true }, attributes: ['announcement_id'] });
  if (!announcement) throw new CustomError('公告不存在', 404, 404);
  await UserAnnouncementRead.findOrCreate({
    where: { user_id: user.id, announcement_id: id }, defaults: { user_id: user.id, announcement_id: id, read_at: new Date() },
  });
  successHandler({ ctx, data: { id, read: true } });
});

router.post('/announcements/read-all', async ctx => {
  const user = await currentUser(ctx);
  const announcements: any[] = await PlatformAnnouncement.findAll({ where: { enabled: true }, attributes: ['announcement_id'], raw: true });
  const ids = announcements.map(row => row.announcement_id);
  const existing: any[] = ids.length ? await UserAnnouncementRead.findAll({
    where: { user_id: user.id, announcement_id: { [Op.in]: ids } }, attributes: ['announcement_id'], raw: true,
  }) : [];
  const seen = new Set(existing.map(row => String(row.announcement_id)));
  const unread = announcements.filter(row => !seen.has(String(row.announcement_id)))
    .map(row => ({ user_id: Number(user.id), announcement_id: row.announcement_id, read_at: new Date() }));
  if (unread.length) await UserAnnouncementRead.bulkCreate(unread, { ignoreDuplicates: true });
  successHandler({ ctx, data: { updated: unread.length } });
});

router.get('/notifications', async ctx => {
  const user = await currentUser(ctx);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页位置无效，请刷新', 400, 400);
  const where: any = { user_id: user.id };
  if (before) where.notification_id = { [Op.lt]: before };
  const rows: any[] = await UserNotification.findAll({ where, order: [['notification_id', 'DESC']], limit: 51, raw: true });
  const hasMore = rows.length > 50;
  const page = rows.slice(0, 50);
  const [preference]: any = await NotificationPreference.findOrCreate({
    where: { user_id: user.id },
    defaults: { user_id: user.id, messages: true, visitors: true, announcements: true, marketing: false, updated_at: new Date() },
  });
  const unreadCount = await UserNotification.count({ where: { user_id: user.id, read_at: null } });
  const announcementRows: any[] = preference.get('announcements') ? await PlatformAnnouncement.findAll({
    where: { enabled: true }, order: [['announcement_id', 'DESC']], limit: 100, raw: true,
  }) : [];
  const announcementIds = announcementRows.map(row => row.announcement_id);
  const announcementReads: any[] = announcementIds.length ? await UserAnnouncementRead.findAll({
    where: { user_id: user.id, announcement_id: { [Op.in]: announcementIds } }, raw: true,
  }) : [];
  const readAnnouncements = new Map(announcementReads.map(row => [String(row.announcement_id), row.read_at]));
  const announcementNotifications = !before ? announcementRows.map(row => ({
    id: `announcement:${row.announcement_id}`, kind: 'ANNOUNCEMENT', actor_id: null, title: row.title,
    body: row.summary || '平台公告', read_at: readAnnouncements.get(String(row.announcement_id)) || null,
    created_at: row.created_at,
  })) : [];
  const notifications = [...page.map(row => ({ ...row, id: String(row.notification_id), actor_id: row.actor_id == null ? null : String(row.actor_id) })), ...announcementNotifications]
    .sort((left, right) => String(right.created_at).localeCompare(String(left.created_at))).slice(0, 50);
  const announcementUnreadCount = announcementNotifications.filter(row => !row.read_at).length;
  successHandler({ ctx, data: {
    notifications,
    unread_count: unreadCount + announcementUnreadCount,
    next_before_id: hasMore ? String(page[page.length - 1].notification_id) : '',
  } });
});

router.post('/notifications/read-all', async ctx => {
  const user = await currentUser(ctx);
  const [updated] = await UserNotification.update({ read_at: new Date() }, { where: { user_id: user.id, read_at: null } });
  const announcements: any[] = await PlatformAnnouncement.findAll({ where: { enabled: true }, attributes: ['announcement_id'], raw: true });
  const ids = announcements.map(row => row.announcement_id);
  const existing: any[] = ids.length ? await UserAnnouncementRead.findAll({
    where: { user_id: user.id, announcement_id: { [Op.in]: ids } }, attributes: ['announcement_id'], raw: true,
  }) : [];
  const seen = new Set(existing.map(row => String(row.announcement_id)));
  const reads = announcements.filter(row => !seen.has(String(row.announcement_id)))
    .map(row => ({ user_id: Number(user.id), announcement_id: row.announcement_id, read_at: new Date() }));
  if (reads.length) await UserAnnouncementRead.bulkCreate(reads, { ignoreDuplicates: true });
  successHandler({ ctx, data: { updated: updated + reads.length } });
});

router.post('/notifications/:id/read', async ctx => {
  const user = await currentUser(ctx);
  const id = ctx.params.id;
  const announcementMatch = /^announcement:([1-9][0-9]{0,19})$/.exec(id);
  if (announcementMatch) {
    const announcement = await PlatformAnnouncement.findOne({ where: { announcement_id: announcementMatch[1], enabled: true }, attributes: ['announcement_id'] });
    if (!announcement) throw new CustomError('通知不存在', 404, 404);
    await UserAnnouncementRead.findOrCreate({ where: { user_id: user.id, announcement_id: announcementMatch[1] }, defaults: { user_id: user.id, announcement_id: announcementMatch[1], read_at: new Date() } });
    successHandler({ ctx, data: { id, read: true } });
    return;
  }
  if (!/^[1-9][0-9]{0,19}$/.test(id)) throw new CustomError('通知 ID 无效', 400, 400);
  const [updated] = await UserNotification.update({ read_at: new Date() }, { where: { notification_id: id, user_id: user.id, read_at: null } });
  const exists = updated || await UserNotification.count({ where: { notification_id: id, user_id: user.id } });
  if (!exists) throw new CustomError('通知不存在', 404, 404);
  successHandler({ ctx, data: { id, read: true } });
});

router.put('/push-tokens', async ctx => {
  const user = await currentUser(ctx);
  const { token, platform } = ctx.request.body || {};
  if (typeof token !== 'string' || token.length < 20 || token.length > 512 || !['android', 'ios', 'web'].includes(platform))
    throw new CustomError('推送令牌无效', 400, 400);
  const [row] = await UserPushToken.findOrCreate({
    where: { token }, defaults: { user_id: user.id, token, platform, updated_at: new Date() },
  });
  await row.update({ user_id: user.id, platform, updated_at: new Date() });
  successHandler({ ctx, data: { registered: true } });
});

router.delete('/push-tokens', async ctx => {
  const user = await currentUser(ctx);
  const token = ctx.request.body?.token;
  if (typeof token !== 'string' || token.length > 512) throw new CustomError('推送令牌无效', 400, 400);
  await UserPushToken.destroy({ where: { user_id: user.id, token } });
  successHandler({ ctx, data: { removed: true } });
});

export default router;
