const assert=require('node:assert/strict');
const {test}=require('node:test');
const path=require('node:path');
const Koa=require('koa');
const jwt=require('jsonwebtoken');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}
process.env.MEDIA_AUTH_KEY='isolated-media-key-12345678901234567890';
let userId=1,revoked=false,open=true,paid=true,unavailable=false;
const requests=[];
let clients=[];
stub('@/config/secret',{JWT_SECRET:'isolated-signing-key',INTERNAL_SERVICE_KEY:'unused',REVENUE_API_URL:'http://ledger'});
stub('@/app/auth/authJwt',{authJwt:async ctx=>ctx.headers.authorization==='Bearer valid'?{code:200,userInfo:{id:userId}}:{code:401,message:'login'}});
stub('@/app/handler/success-handle',{__esModule:true,default:({ctx,data})=>{ctx.body=data;}});
stub('@/model/user.model',{__esModule:true,default:{findByPk:async id=>revoked?null:{id:Number(id),status:1,token:jwt.sign({userInfo:{id:Number(id)}},'isolated-signing-key',{expiresIn:3600})}}});
// Match the session independently of issue time; the real implementation hashes the full token.
stub('@/service/media-authority',{mediaSessionHash:()=> 'session-hash',mediaAuthority:async room=>{if(unavailable)throw Error('down');return{room,open,publishers:['1','2']};}});
const {CustomError}=require('../dist/model/customError.model');
stub('@/service/media-access',{requireMediaAccess:async(user,live)=>{requests.push({user,live});if(!paid)throw new CustomError('locked',402,402);if(unavailable)throw Error('down');}});
stub('axios',{__esModule:true,default:{get:async()=>({data:{code:0,clients}}),delete:async url=>{requests.push({deleted:url});return{};}}});
test('SRS hook binds signed publish tickets; HLS checks both playlists and segments; revoked publishers are disconnected',async()=>{
 const media=require('../dist/router/srs.router');
 const app=new Koa();app.use(async(ctx,next)=>{try{await next();}catch(e){ctx.status=e.statusCode||503;ctx.body={message:e.message};}});app.use(require('koa-bodyparser')());app.use(media.default.routes());
 const server=app.listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));
 const call=async(method,path,body,headers={})=>{const response=await fetch(`http://127.0.0.1:${server.address().port}${path}`,{method,headers:{authorization:'Bearer valid','Content-Type':'application/json',...headers},...(body?{body:JSON.stringify(body)}:{})});return{status:response.status,body:response.status===204?null:await response.json()};};
 try{
  const ticket=await call('POST','/srs/publish-token',{live_session_id:501});assert.equal(ticket.status,200);
  userId=2;assert.equal((await call('POST','/srs/publish-token',{live_session_id:501})).status,403);userId=1;
  const hook={action:'on_publish',client_id:'c1',app:'live',stream:'501',param:'?ticket='+ticket.body.token};
  assert.equal((await call('POST','/srs/hook',hook)).status,403);
  const endpoint='/srs/hook?key='+process.env.MEDIA_AUTH_KEY;
  assert.equal((await call('POST',endpoint,{...hook,stream:'502'})).status,403);
  assert.equal((await call('POST',endpoint,{...hook,param:'?ticket=forged'})).status,403);
  assert.equal((await call('POST',endpoint,hook)).body.code,0);
  const authHeaders={'x-media-key':process.env.MEDIA_AUTH_KEY,'x-original-uri':'/live/501.m3u8'};
  assert.equal((await call('GET','/srs/authorize',null,authHeaders)).status,204);
  assert.equal((await call('GET','/srs/authorize',null,{...authHeaders,'x-original-uri':'/live/501-123.ts'})).status,204);
  assert.equal((await call('GET','/srs/authorize',null,{...authHeaders,'x-original-uri':'/live/501/../502.ts'})).status,403);
  assert.equal((await call('GET','/srs/authorize',null,{...authHeaders,authorization:''})).status,401);
  paid=false;assert.equal((await call('GET','/srs/authorize',null,authHeaders)).status,403);paid=true;
  unavailable=true;assert.equal((await call('GET','/srs/authorize',null,authHeaders)).status,503);unavailable=false;
  clients=[{id:'c1',name:'501',publish:true},{id:'rogue',name:'501',publish:true}];await media.reconcileSrsPublishers();
  assert.ok(requests.some(x=>x.deleted?.endsWith('/rogue')));assert.ok(!requests.some(x=>x.deleted?.endsWith('/c1')));
  revoked=true;await media.reconcileSrsPublishers();assert.ok(requests.some(x=>x.deleted?.endsWith('/c1')));
  revoked=false;open=false;assert.equal((await call('POST',endpoint,hook)).status,403);
 }finally{server.closeAllConnections?.();await new Promise(r=>server.close(r));}
});
