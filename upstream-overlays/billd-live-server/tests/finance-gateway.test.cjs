const assert=require('node:assert/strict');
const {test}=require('node:test');
const path=require('node:path');
const Koa=require('koa');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,value){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports:value};}
let admin=false,signedIn=true,lastCall;
stub('@/app/auth/operations',{requireOperations:async()=>{if(!admin){const e=Error('Forbidden');e.statusCode=403;throw e;}return{id:7};}});
stub('@/app/auth/authJwt',{authJwt:async()=>({code:signedIn?200:401,userInfo:signedIn?{id:42}:null,message:'Sign in'})});
stub('@/config/secret',{INTERNAL_SERVICE_KEY:'gateway-internal',REVENUE_API_URL:'http://ledger'});
stub('@/utils/request',{__esModule:true,default:{request:async options=>{lastCall=options;return{configured:false,version:0,rules:null};}}});
stub('@/model/rechargeOrder.model',{__esModule:true,default:{findOne:async({where})=>where.user_id===42&&where.request_id==='owned'?{request_id:'owned',amount_fen:599}:null}});
stub('@/model/user.model',{__esModule:true,default:{findByPk:async id=>({id,status:1})}});
let attempt;
stub('@/model/providerAttempt.model',{__esModule:true,default:{findOrCreate:async({defaults})=>[attempt ||= defaults]}});
test('finance gateway protects admin routes, replaces claimed operator, and sources refunds from owned payment records',async()=>{
 const app=new Koa();app.use(async(ctx,next)=>{try{await next();}catch(e){ctx.status=e.statusCode||500;ctx.body={message:e.message};}});app.use(require('koa-bodyparser')());app.use(require('../dist/router/finance.router').default.routes());const server=app.listen(0,'127.0.0.1');await new Promise(resolve=>server.once('listening',resolve));
 const call=async(route,method='GET',body)=>{const r=await fetch(`http://127.0.0.1:${server.address().port}`+route,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer current'},...(body?{body:JSON.stringify(body)}:{})});return{status:r.status,body:await r.json()};};
 try{
 for(const route of ['/operations/finance/settings','/operations/finance/audit','/operations/finance/withdrawals','/operations/finance/refunds','/operations/gifts'])assert.equal((await call(route)).status,403);
 for(const route of ['/operations/finance/withdrawals/1','/operations/finance/refunds/1'])assert.equal((await call(route,'POST',{operator_id:1})).status,403);
 admin=true;const result=await call('/operations/finance/settings');assert.equal(result.status,200);assert.equal(result.body.data.configured,false);assert.equal(lastCall.headers['X-Internal-Key'],'gateway-internal');
 assert.equal((await call('/operations/levels/settings')).status,200);assert.equal(lastCall.url,'http://ledger/v1/internal/business/levels');
 assert.equal((await call('/operations/levels/settings','PUT',{operator_id:99,expected_version:0,viewer_thresholds:[1,2,3,4,5]})).status,200);assert.equal(lastCall.data.operator_id,7);
 await call('/operations/finance/settings','PUT',{operator_id:1,rules:{}});assert.equal(lastCall.data.operator_id,7);
 await call('/operations/gifts','PUT',{operator_id:1});assert.equal(lastCall.data.operator_id,7);
 const paid={action:'PAID',request_id:'12345678-1234-4234-8234-123456789012',reason:'核实外部回执',payment_ref:'receipt-1'};
 assert.equal((await call('/operations/finance/withdrawals/1','POST',paid)).status,200);
 assert.equal((await call('/operations/finance/withdrawals/1','POST',paid)).status,200);
 assert.equal((await call('/operations/finance/withdrawals/1','POST',{...paid,payment_ref:'receipt-2'})).status,409);
 attempt={status:'PROVIDER_CONFIRMED'};
 assert.equal((await call('/operations/finance/withdrawals/1','POST',paid)).status,409);
 assert.equal((await call('/revenue/refunds','POST',{recharge_request_id:'someone-elses',amount_fen:100000})).status,409);
 await call('/revenue/refunds','POST',{recharge_request_id:'owned',request_id:'retry-id',reason:'test',user_id:99,amount_fen:999999});assert.deepEqual(lastCall.data,{request_id:'retry-id',user_id:42,recharge_request_id:'owned',amount_fen:599,reason:'test'});
 await call('/revenue/withdrawals');assert.equal(lastCall.headers.Authorization,'Bearer current');assert.equal(lastCall.headers['X-Internal-Key'],undefined);
 signedIn=false;assert.equal((await call('/revenue/withdrawals')).status,401);assert.equal((await call('/revenue/refunds')).status,401);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
