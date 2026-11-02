import Router from 'koa-router';
import { requireOperations } from '@/app/auth/operations';
import { moderateAccount } from '@/service/account-moderation';
import Action from '@/model/accountAction.model';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
const router=new Router({prefix:'/operations'});
router.post('/users/:id/status',async ctx=>{
 const operator=await requireOperations(ctx);
 if(!/^[1-9][0-9]{0,9}$/.test(ctx.params.id))throw new CustomError('账号无效',400,400);
 const result=await moderateAccount(Number(operator.id),ctx.headers.authorization!.slice(7),Number(ctx.params.id),ctx.request.body||{});
 successHandler({ctx,data:{request_id:result.get('request_id'),action_id:String(result.get('action_id')),synced:result.get('synced'),message:'账号状态已生效，媒体和消息会话正在清退'}});
});
router.get('/account-actions',async ctx=>{
 await requireOperations(ctx);const raw=String(ctx.query.page||'1');if(!/^[1-9][0-9]{0,5}$/.test(raw))throw new CustomError('页码无效',400,400);const page=Number(raw);
 const result=await Action.findAndCountAll({order:[['action_id','DESC']],limit:25,offset:(page-1)*25});successHandler({ctx,data:{account_actions:result.rows,total:result.count,page,page_size:25}});
});
export default router;
