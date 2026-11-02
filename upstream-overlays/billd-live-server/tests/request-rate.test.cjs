const assert=require('node:assert/strict');
const {test}=require('node:test');
const path=require('node:path');
const Koa=require('koa');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}
test('temporary windows expire, bound memory and separate principals',()=>{
 const {RequestRateLimiter,peerAddress}=require('../dist/app/request-rate');
 const limiter=new RequestRateLimiter(2);
 assert.equal(limiter.consume('a',2,0),0);assert.equal(limiter.consume('a',2,1),0);
 assert.equal(limiter.consume('a',2,2),60);assert.equal(limiter.consume('b',2,2),0);
 assert.equal(limiter.consume('c',2,2),60); // cannot evict an active limit by flooding new identities
 assert.equal(limiter.consume('c',2,60000),0);assert.equal(limiter.consume('a',2,60002),0);
 assert.equal(peerAddress({req:{socket:{remoteAddress:'::ffff:192.0.2.5'}}}),'192.0.2.5');
});
test('real middleware permits normal polling and throttles bursts without persistent IP bans',async()=>{
 let writes=0;const ips=[];
 stub('@/app/auth/authJwt',{authJwt:async ctx=>{const id=Number(ctx.req.headers.authorization);return id>0?{code:200,userInfo:{id}}:{code:401,message:'未登录'};}});
 stub('@/app/auth/operations',{requireOperations:async()=>{}});
 stub('@/controller/blacklist.controller',{__esModule:true,default:{findByIp:async ip=>{ips.push(ip);return null;},common:{create:async()=>{writes++;}}}});
 const app=new Koa();app.on('error',require('../dist/app/handler/error-handle').default);
 app.use(async(ctx,next)=>{try{await next();}catch(e){app.emit('error',e,ctx);}});
 app.use(require('../dist/app/verify.middleware').apiBeforeVerify);
 app.use(ctx=>{ctx.body={ok:true};});
 const savedLog=console.log,savedError=console.error;console.log=()=>{};console.error=()=>{};
 const server=app.listen(0,'127.0.0.1');await new Promise(resolve=>server.once('listening',resolve));
 try{
  const call=(path='/revenue/wallet',user=1,method='GET',forged='203.0.113.1')=>fetch(`http://127.0.0.1:${server.address().port}${path}`,{method,headers:{...(user?{authorization:String(user)}:{}),'x-real-ip':forged,'x-forwarded-for':'127.0.0.1'}});
  for(let i=0;i<600;i++)assert.equal((await call()).status,200);
  let r=await call();assert.equal(r.status,429);assert.ok(Number(r.headers.get('Retry-After'))>0);
  assert.equal((await call('/revenue/wallet',2)).status,200); // same network, different verified account
  for(let i=0;i<20;i++)assert.equal((await call('/user/login',null,'POST')).status,200);
  r=await call('/admin/user/login',null,'POST','127.0.0.1');assert.equal(r.status,429); // alias shares the login window
  for(let i=0;i<5;i++)assert.equal((await call('/user/create',null,'POST')).status,200);
  assert.equal((await call('/user/create',null,'POST','198.51.100.6')).status,429);
  assert.equal(writes,0);assert.ok(ips.every(ip=>ip==='127.0.0.1'));
 }finally{console.log=savedLog;console.error=savedError;await new Promise(resolve=>server.close(resolve));}
});
