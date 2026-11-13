import Router from 'koa-router';
import { Op } from 'sequelize';
import { requireOperations } from '@/app/auth/operations';
import { authJwt } from '@/app/auth/authJwt';
import { ChatMessage, ChatAction } from '@/model/roomChat.model';
import { chatRoom, moderateChat, persistChat, chatEvents } from '@/service/room-chat';
import { CustomError } from '@/model/customError.model';
import successHandler from '@/app/handler/success-handle';
import { roomPresence } from '@/service/room-presence';

const router = new Router();
router.get('/rooms/:id/activity', async ctx => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id) throw new CustomError('请重新登录', 401, 401);
  const roomId = chatRoom(ctx.params.id);
  const rows = await ChatMessage.findAll({ where: { room_id: roomId, hidden: false }, order: [['id', 'DESC']], limit: 30 });
  successHandler({ ctx, data: { ...roomPresence.activity(roomId), messages: rows.map(row => ({ ...row.get(), id: String(row.get('id')) })) } });
});
router.post('/rooms/:id/messages',async ctx=>{
 const auth=await authJwt(ctx);if(auth.code!==200||!auth.userInfo?.id)throw new CustomError('请重新登录',401,401);
 const result=await persistChat(ctx.params.id,Number(auth.userInfo.id),ctx.headers.authorization!.slice(7),ctx.request.body||{});
 const row:any=result.message.get();
 if(!result.duplicate&&!row.hidden)chatEvents.emit('message',{roomId:row.room_id,messageId:String(row.id),userId:Number(row.user_id),username:row.username,data:{text:row.text,msg:row.text,request_id:row.request_id,userId:Number(row.user_id),username:row.username},created_at:row.created_at});
 successHandler({ctx,data:{message_id:String(row.id),request_id:row.request_id,hidden:row.hidden}});
});
for (const admin of [false, true])
  router.get(
    `${admin ? '/operations' : ''}/rooms/:id/messages`,
    async (ctx) => {
      if (admin) await requireOperations(ctx);
      else {
        const auth = await authJwt(ctx);
        if (auth.code !== 200)
          throw new CustomError(auth.message, auth.code, auth.code);
      }
      const roomId = chatRoom(ctx.params.id),
        before = String(ctx.query.before || '');
      if (before && !/^[1-9][0-9]{0,19}$/.test(before))
        throw new CustomError('分页游标无效', 400, 400);
      const rows = await ChatMessage.findAll({
        where: {
          room_id: roomId,
          ...(!admin ? { hidden: false } : {}),
          ...(before ? { id: { [Op.lt]: before } } : {}),
        },
        order: [['id', 'DESC']],
        limit: 50,
      });
      successHandler({
        ctx,
        data: {
          messages: rows.map((row) => ({
            ...row.get(),
            id: String(row.get('id')),
          })),
          next_before:
            rows.length === 50 ? String(rows[rows.length - 1].get('id')) : null,
        },
      });
    }
  );
router.post('/operations/rooms/:id/chat', async (ctx) => {
  const operator = await requireOperations(ctx),
    body = ctx.request.body || {};
  const result = await moderateChat(
    Number(operator.id),
    ctx.headers.authorization!.slice(7),
    ctx.params.id,
    body.user_id,
    body
  );
  successHandler({ ctx, data: result });
});
router.get('/operations/rooms/:id/chat-actions', async (ctx) => {
  await requireOperations(ctx);
  const roomId = chatRoom(ctx.params.id);
  const rows = await ChatAction.findAll({
    where: { room_id: roomId },
    order: [['id', 'DESC']],
    limit: 50,
  });
  successHandler({ ctx, data: { actions: rows } });
});
export default router;
