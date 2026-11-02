import { authJwt } from './authJwt';
import roleService from '@/service/role.service';
import { CustomError } from '@/model/customError.model';

export async function canManageOperations(userId: number): Promise<boolean> {
  const roles: any = await roleService.getMyRole(userId);
  return Array.isArray(roles) && roles.some(role => role.role_value === 'SUPER_ADMIN');
}

export async function requireOperations(ctx) {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message || '请重新登录', code, code);
  if (!await canManageOperations(Number(userInfo.id))) throw new CustomError('当前账号没有运营管理权限', 403, 403);
  return userInfo;
}
