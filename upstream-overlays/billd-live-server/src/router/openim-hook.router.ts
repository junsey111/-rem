import Router from 'koa-router';

import { OPENIM_CALLBACK_PATH, OPENIM_CALLBACK_SECRET } from '@/config/secret';
import { assertPeerAccess } from '@/service/interaction-policy';

const router = new Router();
const callbackPattern = /^\/openim\/hooks\/([A-Za-z0-9_-]{32,128})\/callbackBeforeSendSingleMsgCommand$/;
const userIdPattern = /^[1-9][0-9]{0,9}$/;

if (OPENIM_CALLBACK_SECRET && callbackPattern.test(OPENIM_CALLBACK_PATH)) {
  router.post(OPENIM_CALLBACK_PATH, async ctx => {
    const body = ctx.request.body || {};
    const denied = (message: string) => {
      ctx.body = { actionCode: 0, nextCode: 1, errCode: 403, errMsg: message };
    };
    const allowed = () => {
      ctx.body = { actionCode: 0, nextCode: 0 };
    };

    if (body.callbackCommand !== 'callbackBeforeSendSingleMsgCommand'
      || typeof body.sendID !== 'string' || !userIdPattern.test(body.sendID)
      || typeof body.recvID !== 'string' || !userIdPattern.test(body.recvID)) {
      denied('消息请求无效');
      return;
    }

    try {
      await assertPeerAccess(Number(body.sendID), Number(body.recvID));
      allowed();
    } catch (error: any) {
      if (error?.statusCode >= 400 && error?.statusCode < 500) denied(error.message || '该用户暂不可联系');
      else throw error;
    }
  });
}

export default router;
