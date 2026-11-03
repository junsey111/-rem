import Router from 'koa-router';
import axiosClient from 'axios';
import { requireOperations } from '@/app/auth/operations';
import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';
import axios from '@/utils/request';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';

const router = new Router({ prefix: '/operations' });
for (const path of [
  '/referrals/settings',
  '/lives/:id/watch-price',
  '/business-actions',
]) {
  for (const method of ['get', 'put'] as const) {
    if (path === '/business-actions' && method === 'put') continue;
    router[method](path, async (ctx) => {
      const operator = await requireOperations(ctx);
      let target = 'referrals';
      if (path.includes(':id')) {
        if (
          !/^[1-9][0-9]{0,19}$/.test(ctx.params.id) ||
          BigInt(ctx.params.id) > 18446744073709551615n
        )
          throw new CustomError('直播编号无效', 400, 400);
        target = `watch-prices/${ctx.params.id}`;
      } else if (path === '/business-actions') {
        const before = ctx.query.before_id;
        if (
          before !== undefined &&
          (typeof before !== 'string' || !/^[1-9][0-9]{0,19}$/.test(before))
        )
          throw new CustomError('分页无效', 400, 400);
        target = 'actions' + (before ? `?before_id=${before}` : '');
      }
      const body = ctx.request.body || {};
      if ('operator_id' in body)
        throw new CustomError('操作身份由服务器确定', 400, 400);
      try {
        const data = await axios.request({
          url: `${REVENUE_API_URL}/v1/internal/business/${target}`,
          method,
          headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY },
          timeout: 10000,
          ...(method === 'put'
            ? { data: { ...body, operator_id: Number(operator.id) } }
            : {}),
        });
        successHandler({ ctx, data });
      } catch (error) {
        if (!axiosClient.isAxiosError(error)) throw error;
        const status = error.response?.status;
        throw new CustomError(
          status && status < 500
            ? error.response?.data?.message || '设置未保存'
            : '设置结果尚未确认，请保留原单号重试',
          status && status < 500 ? status : 503,
          status && status < 500 ? status : 503
        );
      }
    });
  }
}
export default router;
