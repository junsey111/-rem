const assert=require('node:assert/strict');
const {test}=require('node:test');
const path=require('node:path');
const Koa=require('koa');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}
test('real global error pipeline preserves business results and omits credentials from logs',async()=>{
 const records=[],consoleRecords=[];let failAudit=false;
 stub('@/app/auth/authJwt',{authJwt:async()=>({code:200,userInfo:{id:1}})});
 stub('@/controller/log.controller',{__esModule:true,default:{common:{create:async data=>{if(failAudit)throw Error('private storage failure');records.push(data);}}}});
 const {CustomError}=require('../dist/model/customError.model');
 const app=new Koa();app.on('error',require('../dist/app/handler/error-handle').default);
 app.use(require('../dist/app/app.middleware').catchErrorMiddle);app.use(require('koa-bodyparser')());
 app.use(ctx=>{
  if(ctx.path==='/throw-known')throw new CustomError('当前账号无权访问',403,403);
  if(ctx.path==='/throw-unknown')throw Error('SQL contains secret-sentinel');
  ctx.status=Number(ctx.path.slice(1));
  ctx.set('Retry-After','30');ctx.body={code:ctx.status,gift_outcome:'REJECTED',message:'本次未扣币'};
 });
 const originalError=console.error;console.error=(...args)=>consoleRecords.push(args);
 const server=app.listen(0,'127.0.0.1');await new Promise(resolve=>server.once('listening',resolve));
 try{
  const call=async p=>fetch(`http://127.0.0.1:${server.address().port}${p}?token=secret-sentinel`,{method:'POST',headers:{authorization:'Bearer secret-sentinel',cookie:'token=secret-sentinel','Content-Type':'application/json','x-real-ip':'forged-sentinel'},body:JSON.stringify({password:'secret-sentinel',sign:'secret-sentinel'})});
  for(const status of [200,201,204,304,400,403,404,409,429,503]){
   const r=await call('/'+status);assert.equal(r.status,status);assert.equal(r.headers.get('Retry-After'),'30');
   if(![204,304].includes(status))assert.equal((await r.json()).gift_outcome,'REJECTED');
  }
  let r=await call('/throw-known');assert.equal(r.status,403);assert.equal((await r.json()).message,'当前账号无权访问');
  r=await call('/throw-unknown');assert.equal(r.status,500);assert.ok(!JSON.stringify(await r.json()).includes('secret-sentinel'));
  failAudit=true;r=await call('/409');assert.equal(r.status,409);assert.equal((await r.json()).gift_outcome,'REJECTED');
  assert.equal(records.length,12);assert.ok(records.every(row=>!('api_body'in row)&&!('api_query'in row)&&row.api_real_ip==='127.0.0.1'));
  assert.ok(!JSON.stringify([records,consoleRecords]).includes('secret-sentinel'));
  assert.ok(!JSON.stringify([records,consoleRecords]).includes('forged-sentinel'));
 }finally{console.error=originalError;await new Promise(resolve=>server.close(resolve));}
});
