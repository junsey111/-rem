import Router from 'koa-router';

import userController from '@/controller/user.controller';
import { authJwt } from '@/app/auth/authJwt';
import { CustomError } from '@/model/customError.model';
import successHandler from '@/app/handler/success-handle';
import { changePassword } from '@/app/auth/password-login';
import sequelize from '@/config/mysql';
import User from '@/model/user.model';

const userRouter = new Router({ prefix: '/user' });

userRouter.post('/create', userController.create);
userRouter.post('/login', userController.login);
userRouter.post('/password', changePassword);
userRouter.post('/logout', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo?.id) throw new CustomError(message, code, code);
  // A delayed logout must never invalidate a newer login.
  await sequelize.transaction(async transaction => {
    const user: any = await User.findByPk(userInfo.id, { transaction, lock: transaction.LOCK.UPDATE });
    if (user?.token === ctx.req.headers.authorization!.slice(7)) await user.update({ token: null }, { transaction });
  });
  successHandler({ ctx, message: '已退出登录' });
});
userRouter.get('/me', async ctx => {
  const { code, userInfo, message } = await authJwt(ctx);
  if (code !== 200 || !userInfo) throw new CustomError(message, code, code);
  successHandler({ ctx, data: { id: userInfo.id, username: userInfo.username, status: userInfo.status } });
});

export default userRouter;
