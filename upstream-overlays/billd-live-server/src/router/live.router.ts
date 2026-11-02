import Router from 'koa-router';

import successHandler from '@/app/handler/success-handle';
import DBController from '@/config/websocket/mysql.controller';
import Cover from '@/model/liveCover.model';
import { Op } from 'sequelize';

const liveRouter = new Router({ prefix: '/live' });

// 黑名单列表
liveRouter.get('/list', async (ctx, next) => {
  const result = await DBController.getAllLiveRoom();
  const parsed = result.rows.map((row) => {
    const data: any = row.get();
    let owner = 0;
    try {
      owner = JSON.parse(data.data)?.data?.userId;
    } catch {}
    return { data, owner };
  });
  const owners = parsed
    .map((row) => row.owner)
    .filter((id) => Number.isSafeInteger(id) && id > 0);
  const covers: any[] = owners.length
    ? await Cover.findAll({
        attributes: ['user_id'],
        where: { user_id: { [Op.in]: owners } },
      })
    : [];
  const available = new Set(covers.map((cover) => Number(cover.user_id)));
  const rows = parsed.map(({ data, owner }) => ({
    ...data,
    cover_path: available.has(owner) ? `/live/covers/${owner}` : null,
  }));
  successHandler({ ctx, data: { count: result.count, rows } });
  await next();
});

export default liveRouter;
