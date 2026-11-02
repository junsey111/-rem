const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const Koa = require('koa');
require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, exports) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename:id, loaded:true, exports };
}
stub('@/config/secret', {REVENUE_API_URL:'http://fixture',INTERNAL_SERVICE_KEY:'fixture'});
stub('@/app/auth/authJwt', {authJwt:async()=>({code:200,userInfo:{id:1}})});
stub('@/app/handler/success-handle', {__esModule:true,default:({ctx,data})=>{ctx.body=data;}});
let failure;
stub('@/utils/request', {__esModule:true,default:{get:async()=>({age_years:null,region_code:null,source:'UNKNOWN'}),put:async()=>{throw failure;},post:async url=>{if(url.endsWith('/users'))return {}; throw failure;}}});
test('profile and live refusals reach the app as actionable 4xx; unknown failures remain errors',async()=>{
 const app=new Koa();
 app.use(async(ctx,next)=>{try{await next();}catch(e){ctx.status=e.statusCode||500;ctx.body={message:e.message};}});
 app.use(require('koa-bodyparser')());
 app.use(require('../dist/router/revenue.router').default.routes());
 const server=app.listen(0,'127.0.0.1');
 await new Promise(resolve=>server.once('listening',resolve));
 try{
  const call=async(method,path)=>fetch(`http://127.0.0.1:${server.address().port}/revenue/${path}`,{method,headers:{authorization:'Bearer fixture','Content-Type':'application/json'},...(method==='GET'?{}:{body:'{}'})});
  assert.deepEqual(await(await call('GET','profile')).json(),{age_years:null,region_code:null,source:'UNKNOWN'});
  for(const [method,path,status,code,text] of [['PUT','profile',400,'INVALID_REGION','地区'],['POST','lives/start',403,'LIVE_PROFILE_REQUIRED','开播权限']]){
   failure={isAxiosError:true,response:{status,data:{code}}};
   const response=await call(method,path);
   assert.equal(response.status,status);assert.ok((await response.json()).message.includes(text));
  }
  failure=new Error('unavailable');assert.equal((await call('PUT','profile')).status,500);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
