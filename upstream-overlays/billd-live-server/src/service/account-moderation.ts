import { Op, QueryTypes } from 'sequelize';
import sequelize from '@/config/mysql';
import User from '@/model/user.model';
import Action from '@/model/accountAction.model';
import { CustomError } from '@/model/customError.model';
import axios from '@/utils/request';
import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';

export async function moderateAccount(operatorId:number,token:string,userId:number,command:any){
 if(!Number.isSafeInteger(userId)||userId<1||typeof command.disabled!=='boolean'||typeof command.request_id!=='string'||!(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/).test(command.request_id)||typeof command.reason!=='string'||!command.reason.trim()||Array.from(command.reason).length>200)throw new CustomError('操作单号、账号状态和原因无效',400,400);
 if(operatorId===userId)throw new CustomError('不能禁用当前管理账号',409,409);
 return sequelize.transaction(async transaction=>{
  const accounts:any[]=await User.findAll({where:{id:{[Op.in]:[operatorId,userId]}},order:[['id','ASC']],transaction,lock:transaction.LOCK.UPDATE});
  const operator=accounts.find(row=>Number(row.id)===operatorId),target=accounts.find(row=>Number(row.id)===userId);
  if(!operator||operator.status!==1||operator.token!==token)throw new CustomError('登录已失效',401,401);
  const roles:any[]=await sequelize.query(`SELECT ur.user_id,r.role_value FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id IN (?,?) AND ur.deleted_at IS NULL AND r.deleted_at IS NULL LOCK IN SHARE MODE`,{replacements:[operatorId,userId],type:QueryTypes.SELECT,transaction});
  if(!roles.some(row=>Number(row.user_id)===operatorId&&row.role_value==='SUPER_ADMIN'))throw new CustomError('无运营权限',403,403);
  if(!target)throw new CustomError('账号不存在',404,404);
  if(roles.some(row=>Number(row.user_id)===userId&&row.role_value==='SUPER_ADMIN'))throw new CustomError('不能在此禁用管理员账号',409,409);
  const prior:any=await Action.findOne({where:{request_id:command.request_id},transaction});
  if(prior){if(Number(prior.user_id)!==userId||Number(prior.operator_id)!==operatorId||prior.disabled!==command.disabled||prior.reason!==command.reason)throw new CustomError('单号已用于其他账号操作',409,409);return prior;}
  await target.update({status:command.disabled?2:1,token:null},{transaction});
  return Action.create({request_id:command.request_id,operator_id:operatorId,user_id:userId,disabled:command.disabled,reason:command.reason},{transaction});
 });
}
export async function reconcileAccountModeration(){
 const pending:any[]=await Action.findAll({where:{synced:false},order:[['action_id','ASC']],limit:25});
 for(const row of pending){
  const latest:any=await Action.findOne({where:{user_id:row.user_id},order:[['action_id','DESC']]});
  const result:any=await axios.put(`${REVENUE_API_URL}/v1/internal/platform-account`,{user_id:Number(latest.user_id),revision:Number(latest.action_id),disabled:latest.disabled},{headers:{'X-Internal-Key':INTERNAL_SERVICE_KEY},timeout:5000});
  if(String(result.revision)!==String(latest.action_id)||result.disabled!==latest.disabled)throw Error('Account moderation not confirmed');
  await Action.update({synced:true},{where:{user_id:latest.user_id,action_id:{[Op.lte]:latest.action_id}}});
 }
}
export function startAccountModeration(server){
 let stopped=false,timer:ReturnType<typeof setTimeout>;
 const run=async()=>{try{await reconcileAccountModeration();}catch{console.error('Account moderation reconciliation incomplete; will retry');}if(!stopped){timer=setTimeout(run,5000);timer.unref();}};
 void run();const stop=()=>{stopped=true;clearTimeout(timer);};server.once('close',stop);return stop;
}
