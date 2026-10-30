import crypto from 'crypto';
import Router from 'koa-router';

import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { OPENIM_ADMIN_USER_ID, OPENIM_API_URL, OPENIM_PLATFORM_ID, OPENIM_PUBLIC_API_URL, OPENIM_SECRET, OPENIM_WS_URL } from '@/config/secret';
import { ALLOW_HTTP_CODE } from '@/constant';
import { CustomError } from '@/model/customError.model';
import axios from '@/utils/request';

const openimRouter = new Router({ prefix: '/openim' });

function responseData(response: any) {
  const body = response?.data || response;
  if (body?.errCode && body.errCode !== 0) {
    throw new CustomError(body.errMsg || 'OpenIM 请求失败', ALLOW_HTTP_CODE.serverError, body.errCode);
  }
  return body?.data || body;
}

openimRouter.post('/session', async (ctx, next) => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== ALLOW_HTTP_CODE.ok || !userInfo?.id) throw new CustomError(message || '未登录！', code, code);
  if (!OPENIM_SECRET) throw new CustomError('OpenIM 未配置 OPENIM_SECRET', 500, 500);

  const userId = String(userInfo.id);
  const headers = { 'Content-Type': 'application/json', operationID: `billd-${userId}-${crypto.randomUUID()}` };
  const admin = responseData(await axios.post(`${OPENIM_API_URL}/auth/get_admin_token`, {
    secret: OPENIM_SECRET, userID: OPENIM_ADMIN_USER_ID,
  }, { headers, timeout: 10000 }));
  if (!admin?.token) throw new CustomError('OpenIM 管理员 token 获取失败', 500, 500);

  await axios.post(`${OPENIM_API_URL}/user/user_register`, { users: [{
    userID: userId, nickname: userInfo.username || `用户${userId}`, faceURL: userInfo.avatar || '',
  }] }, { headers: { ...headers, token: admin.token }, timeout: 10000 }).catch((error) => {
    const body = error?.response?.data;
    if (!body || body.errCode !== 1102) throw error;
  });
  const token = responseData(await axios.post(`${OPENIM_API_URL}/auth/get_user_token`, {
    userID: userId, platformID: OPENIM_PLATFORM_ID,
  }, { headers: { ...headers, token: admin.token }, timeout: 10000 }));
  if (!token?.token) throw new CustomError('OpenIM 用户 token 获取失败', 500, 500);
  successHandler({ ctx, data: { user_id: userId, token: token.token, expires_at: token.expireTime || token.expireTimeSeconds, api_url: OPENIM_PUBLIC_API_URL, ws_url: OPENIM_WS_URL } });
  await next();
});

export default openimRouter;
