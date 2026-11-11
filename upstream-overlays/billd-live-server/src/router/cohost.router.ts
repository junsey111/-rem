import Router from 'koa-router';
import axiosClient from 'axios';
import { authJwt } from '@/app/auth/authJwt';
import axios from '@/utils/request';
import { REVENUE_API_URL } from '@/config/secret';
import { CustomError } from '@/model/customError.model';
import successHandler from '@/app/handler/success-handle';
import User from '@/model/user.model';
import { Op } from 'sequelize';
const router = new Router();
for (const path of ['/revenue/cohosts/:liveId', '/revenue/cohosts/:liveId/:id'])
  for (const method of ['get', 'post'] as const)
    router[method](path, async (ctx) => {
      const auth = await authJwt(ctx);
      if (auth.code !== 200 || !auth.userInfo?.id)
        throw new CustomError(
          auth.message || '请重新登录',
          auth.code,
          auth.code
        );
      for (const id of [
        ctx.params.liveId,
        ...(ctx.params.id ? [ctx.params.id] : []),
      ])
        if (!/^[1-9][0-9]{0,19}$/.test(id))
          throw new CustomError('连麦编号无效', 400, 400);
      try {
        const result: any = await axios.request({
          url: `${REVENUE_API_URL}/v1/cohosts/${ctx.params.liveId}${
            ctx.params.id ? '/' + ctx.params.id : ''
          }`,
          method,
          headers: { Authorization: ctx.headers.authorization || '' },
          data: method === 'post' ? ctx.request.body : undefined,
          timeout: 10000,
        });
        if (method === 'get' && Array.isArray(result?.requests)) {
          const users = await User.findAll({ where: { id: { [Op.in]: result.requests.map(row => Number(row.user_id)) } }, attributes: ['id', 'username', 'avatar'] });
          const profiles = new Map(users.map(row => [Number(row.get('id')), row.get()] as [number, any]));
          result.requests = result.requests.map(row => ({ ...row, username: profiles.get(Number(row.user_id))?.username || `用户${row.user_id}`, avatar: profiles.get(Number(row.user_id))?.avatar || null }));
        }
        successHandler({ ctx, data: result });
      } catch (e) {
        if (!axiosClient.isAxiosError(e)) throw e;
        const status = e.response?.status;
        throw new CustomError(
          status && status < 500
            ? e.response?.data?.message || '连麦操作被拒绝'
            : '连麦状态未确认，请保留原单号重试',
          status && status < 500 ? status : 503,
          status && status < 500 ? status : 503
        );
      }
    });
export default router;
