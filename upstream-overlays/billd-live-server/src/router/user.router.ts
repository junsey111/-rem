import Router from 'koa-router';

import userController from '@/controller/user.controller';

const userRouter = new Router({ prefix: '/user' });

userRouter.post('/create', userController.create);
userRouter.post('/login', userController.login);

export default userRouter;
