import sequelize from '@/config/mysql';
import User from '@/model/user.model';
import { CustomError } from '@/model/customError.model';
import successHandler from '@/app/handler/success-handle';
import { authJwt, signJwt } from './authJwt';
import { hashPassword, isPasswordHash, validPassword, verifyPassword } from './password';

export async function passwordLogin(ctx, next) {
  const { id, password } = ctx.request.body || {};
  if ((typeof id !== 'number' && typeof id !== 'string') || !/^[1-9][0-9]{0,9}$/.test(String(id))
    || !Number.isSafeInteger(Number(id)) || typeof password !== 'string' || password.length < 1 || password.length > 1024) {
    throw new CustomError('账号或密码格式有误', 400, 400);
  }
  const token = await sequelize.transaction(async transaction => {
    const user: any = await User.findOne({ where: { id: Number(id) }, attributes: ['id', 'password', 'status'], transaction, lock: transaction.LOCK.UPDATE });
    if (!user || !await verifyPassword(password, user.password)) throw new CustomError('账号或密码错误', 400, 400);
    if (user.status === 2) throw new CustomError('账号已禁用，请联系平台处理', 403, 403);
    const issued = signJwt({ userInfo: { id: Number(user.id) }, exp: 24 });
    if (!isPasswordHash(user.password)) {
      const hash = await hashPassword(password);
      // Raw parameterized update avoids treating this internally produced hash as a new raw password.
      await sequelize.query('UPDATE user SET password=? WHERE id=?', { replacements: [hash, user.id], transaction });
    }
    await user.update({ token: issued }, { transaction });
    return issued;
  });
  successHandler({ ctx, data: token, message: '登录成功' });
  await next();
}

export async function changePassword(ctx, next) {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id) throw new CustomError(auth.message, auth.code, auth.code);
  const { oldpwd, newpwd } = ctx.request.body || {};
  if (!validPassword(oldpwd) || !validPassword(newpwd)) throw new CustomError('请填写 1 至 1024 字符的原密码和新密码', 400, 400);
  const token = ctx.req.headers.authorization.slice(7);
  await sequelize.transaction(async transaction => {
    const user: any = await User.findByPk(auth.userInfo!.id, { attributes: ['id', 'password', 'token', 'status'], transaction, lock: transaction.LOCK.UPDATE });
    if (!user || user.status === 2 || user.token !== token) throw new CustomError('登录已失效，请重新登录', 401, 401);
    if (!await verifyPassword(oldpwd, user.password)) throw new CustomError('原密码错误', 400, 400);
    const hash = await hashPassword(newpwd);
    await sequelize.query('UPDATE user SET password=?,token=NULL,updated_at=UTC_TIMESTAMP() WHERE id=?', {
      replacements: [hash, user.id], transaction,
    });
  });
  successHandler({ ctx, message: '密码已修改，请重新登录' });
  await next();
}
