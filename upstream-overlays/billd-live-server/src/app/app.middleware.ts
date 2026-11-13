import { peerAddress } from '@/app/request-rate';
import { ParameterizedContext } from 'koa';

import { PROJECT_ENV } from '@/constant';
import logController from '@/controller/log.controller';
import { chalkINFO } from '@/utils/chalkTip';

import { authJwt } from './auth/authJwt';

const auditPath = (path: string) => path.replace(/^\/openim\/hooks\/[^/]+(?=\/callbackBeforeSendSingleMsgCommand$)/, '/openim/hooks/[redacted]');

// Preserve route-owned HTTP results, including durable gift rejection markers.
export const catchErrorMiddle = async (ctx: ParameterizedContext, next) => {
  const start = Date.now();
  try {
    await next();
  } catch (error) {
    ctx.app.emit('error', error, ctx);
  }
  if (PROJECT_ENV === 'beta') return;
  try {
    const { userInfo } = await authJwt(ctx);
    // Request bodies, query values, cookies and bearer tokens are never audit fields.
    await logController.common.create({
      user_id: userInfo?.id || -1,
      api_user_agent: ctx.request.headers['user-agent'],
      api_from: ctx.path === '/admin' || ctx.path.startsWith('/admin/') ? 2 : 1,
      api_method: ctx.request.method,
      api_path: auditPath(ctx.request.path),
      api_real_ip: peerAddress(ctx),
      api_status_code: ctx.status,
      api_err_code: ctx.status,
      api_duration: Date.now() - start,
    });
  } catch {
    // An audit-storage outage must not change a committed business result.
    console.error('Request audit write failed');
  }
};

// 跨域中间件
export const corsMiddle = async (ctx: ParameterizedContext, next) => {
  console.log('corsMiddle跨域中间件');
  ctx.set(
    'Access-Control-Allow-Headers',
    'Content-Type, Content-Length, Authorization, Accept, X-Requested-With'
  ); // 允许的请求头
  ctx.set('Access-Control-Allow-Methods', 'PUT, POST, GET, DELETE, OPTIONS'); // 允许的方法

  // 如果是本地环境
  if (ctx.header.origin?.indexOf('http://localhost') !== -1) {
    ctx.set('Access-Control-Allow-Credentials', 'true'); // 允许携带cookie，Access-Control-Allow-Origin为*的时候不能设置Access-Control-Allow-Credentials:true！
    ctx.set('Access-Control-Allow-Origin', ctx.header.origin!); // 允许的源
  } else {
    const allowOrigin = ['https://www.hsslive.cn', 'https://admin.hsslive.cn'];
    // @ts-ignore
    if (allowOrigin === '*') {
      ctx.set('Access-Control-Allow-Origin', '*'); // 允许所有源
    } else if (allowOrigin.includes(ctx.header.origin)) {
      ctx.set('Access-Control-Allow-Credentials', 'true'); // 允许携带cookie，Access-Control-Allow-Origin为*的时候不能设置Access-Control-Allow-Credentials:true！
      ctx.set('Access-Control-Allow-Origin', ctx.header.origin); // 允许的源
    } else {
      console.log('非法源！');
    }
  }

  if (ctx.method === 'OPTIONS') {
    // 跨域请求时，浏览器会先发送options
    ctx.body = 'ok';
  } else {
    await next();
    console.log(chalkINFO(`corsMiddle中间件通过！ `));
  }
};
