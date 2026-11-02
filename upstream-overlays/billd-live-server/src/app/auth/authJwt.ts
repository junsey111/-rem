import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import { JWT_SECRET } from '@/config/secret';
import { COMMON_ERR_MSG } from '@/constant';
import { IUser } from '@/interface';
import userService from '@/service/user.service';

const authJwt = async (ctx): Promise<{ code: number; message: string; userInfo?: IUser }> => {
  const header = ctx.req?.headers?.authorization;
  if (typeof header !== 'string' || !/^Bearer \S+$/i.test(header)) return { code: 401, message: '请重新登录' };
  const token = header.slice(7);
  let claims: any;
  try {
    claims = jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] });
    if (typeof claims !== 'object' || typeof claims.exp !== 'number' || !Number.isFinite(claims.exp)
      || typeof claims.iat !== 'number' || !Number.isFinite(claims.iat) || claims.iat > Date.now() / 1000 + 30
      || claims.exp <= claims.iat || claims.exp - claims.iat > 86400
      || claims.exp <= Date.now() / 1000 || !['number', 'string'].includes(typeof claims.userInfo?.id)
      || !/^[1-9][0-9]{0,9}$/.test(String(claims.userInfo?.id))) return { code: 401, message: '请重新登录' };
  } catch { return { code: 401, message: '登录已失效，请重新登录' }; }
  try {
    const user = await userService.findAndToken(Number(claims.userInfo.id));
    if (!user || user.token !== token) return { code: 401, message: '登录已失效，请重新登录' };
    if (user.status === 2) return { code: 401, message: COMMON_ERR_MSG.adminDisableUser };
    return { code: 200, message: '验证通过', userInfo: user };
  } catch { return { code: 503, message: '账号服务暂时不可用，请稍后重试' }; }
};

// Keep legacy callers' signature; session lifetime and identity are server-owned.
const signJwt = (value: { userInfo: any; exp: number }): string => {
  const id = Number(value.userInfo?.id);
  if (!Number.isSafeInteger(id) || id <= 0) throw Error('Invalid account identity');
  return jwt.sign({ userInfo: { id } }, JWT_SECRET, {
    algorithm: 'HS256', expiresIn: '24h', jwtid: crypto.randomUUID(),
  });
};

export { authJwt, signJwt };
