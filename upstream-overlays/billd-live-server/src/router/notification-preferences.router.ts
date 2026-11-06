import Router from 'koa-router';

import { authJwt } from '@/app/auth/authJwt';
import { CustomError } from '@/model/customError.model';
import successHandler from '@/app/handler/success-handle';
import NotificationPreference from '@/model/notificationPreference.model';

const router = new Router({ prefix: '/user' });
const preferenceKeys = ['messages', 'visitors', 'announcements', 'marketing'] as const;

async function currentUser(ctx) {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id)
    throw new CustomError(auth.message || '请重新登录', auth.code || 401, auth.code || 401);
  return auth.userInfo;
}

function serialize(row: any) {
  return Object.fromEntries(preferenceKeys.map(key => [key, Boolean(row[key])]));
}

router.get('/notification-preferences', async ctx => {
  const user = await currentUser(ctx);
  const [row] = await NotificationPreference.findOrCreate({
    where: { user_id: user.id },
    defaults: { user_id: user.id, messages: true, visitors: true, announcements: true, marketing: false, updated_at: new Date() },
  });
  successHandler({ ctx, data: { preferences: serialize(row.get({ plain: true })) } });
});

router.put('/notification-preferences', async ctx => {
  const user = await currentUser(ctx);
  const body = ctx.request.body || {};
  const keys = Object.keys(body);
  if (!keys.length || keys.some(key => !preferenceKeys.includes(key as typeof preferenceKeys[number])) ||
    keys.some(key => typeof body[key] !== 'boolean'))
    throw new CustomError('通知选项无效', 400, 400);

  const [row] = await NotificationPreference.findOrCreate({
    where: { user_id: user.id },
    defaults: { user_id: user.id, messages: true, visitors: true, announcements: true, marketing: false, updated_at: new Date() },
  });
  await row.update({ ...body, updated_at: new Date() });
  successHandler({ ctx, data: { preferences: serialize(row.get({ plain: true })) } });
});

export default router;
