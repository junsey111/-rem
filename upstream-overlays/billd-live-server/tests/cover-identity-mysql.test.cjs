const {test}=require('node:test');
const assert=require('node:assert/strict');
const {randomUUID,createHmac}=require('node:crypto');
const path=require('node:path');
const mysql=require('mysql2/promise');
const {Sequelize}=require('sequelize');
const Koa=require('koa');
const sharp=require('sharp');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}
test('cover decoding persists only re-encoded images; disabled identity/payments have no side effects; signed identity callbacks are immutable',{skip:!process.env.MYSQL_TEST_URL},async()=>{
 const url=new URL(process.env.MYSQL_TEST_URL),name=`live_test_cover_${Date.now()}`;
 const admin=await mysql.createConnection({host:url.hostname,port:Number(url.port),user:url.username,password:url.password});
 let db,server;let signedIn=true;let providerCalls=0;
 try{
  await admin.query(`CREATE DATABASE \`${name}\``);db=new Sequelize(name,url.username,url.password,{host:url.hostname,port:Number(url.port),dialect:'mysql',logging:false});
  stub('@/config/mysql',{__esModule:true,default:db});stub('@/config/secret',{ALIPAY_CONFIG:{appId:'test',privateKey:'test',alipayPublicKey:'test'},INTERNAL_SERVICE_KEY:'test',REVENUE_API_URL:'http://ledger'});
  stub('@/app/auth/authJwt',{authJwt:async()=>signedIn?{code:200,userInfo:{id:1}}:{code:401}});
  stub('@/app/auth/operations',{requireOperations:async()=>{if(!signedIn)throw Error('unauthorized');return{id:1};}});
  stub('axios',{__esModule:true,default:{post:async()=>{providerCalls++;throw Error('must not call');}}});
  stub('@/utils/request',{__esModule:true,default:{get:async()=>{providerCalls++;throw Error('must not call');},post:async()=>{providerCalls++;throw Error('must not call');}}});
  const Cover=require('../dist/model/liveCover.model').default,Identity=require('../dist/model/identityVerification.model').default,Attempt=require('../dist/model/providerAttempt.model').default;
  await db.sync();
  const app=new Koa();app.use(async(ctx,next)=>{try{await next()}catch(e){ctx.status=e.statusCode||500;ctx.body={message:e.message}}});app.use(require('koa-body')({jsonLimit:'32kb'}));
  for(const file of ['cover','identity','provider'])app.use(require(`../dist/router/${file}.router`).default.routes());
  server=app.listen(0,'127.0.0.1');await new Promise(resolve=>server.once('listening',resolve));
  const call=async(route,method='GET',body,headers={})=>{const response=await fetch(`http://127.0.0.1:${server.address().port}`+route,{method,headers:{...(body&&!(body instanceof Buffer)?{'Content-Type':'application/json'}:{}),...headers},...(body?{body:body instanceof Buffer?body:JSON.stringify(body)}:{})});return{status:response.status,bytes:Buffer.from(await response.arrayBuffer()),type:response.headers.get('content-type')};};
  const image=await sharp({create:{width:60,height:80,channels:3,background:'red'}}).png().toBuffer();
  signedIn=false;assert.equal((await call('/live/cover','PUT',image,{'Content-Type':'application/octet-stream'})).status,401);signedIn=true;
  assert.equal((await call('/live/cover','PUT',Buffer.from('<svg onload="evil"/>'),{'Content-Type':'application/octet-stream'})).status,400);
  assert.equal((await call('/live/cover','PUT',image,{'Content-Type':'application/octet-stream'})).status,200);
  const saved=await call('/live/covers/1');assert.equal(saved.status,200);assert.match(saved.type,/image\/jpeg/);assert.equal((await sharp(saved.bytes).metadata()).format,'jpeg');assert.equal(await Cover.count(),1);
  process.env.IDENTITY_ENABLED='false';process.env.ALIPAY_DISBURSEMENTS_ENABLED='false';
  assert.equal((await call('/verification/start','POST',{request_id:randomUUID()})).status,503);
  assert.equal((await call('/operations/finance/refunds/1/execute','POST',{request_id:randomUUID(),reason:'test'})).status,503);
  assert.equal(providerCalls,0);assert.equal(await Identity.count(),0);assert.equal(await Attempt.count(),0);
  process.env.IDENTITY_ENABLED='true';process.env.IDENTITY_PROVIDER_KEY='isolated-key';process.env.IDENTITY_PROVIDER_URL='https://adapter.example/start';
  const requestId=randomUUID();await Identity.create({request_id:requestId,user_id:1});
  const body={request_id:requestId,user_id:1,status:'VERIFIED',age_years:25,region_code:'CN',provider_reference:'proof1'};
  const signature=createHmac('sha256','isolated-key').update(`${requestId}:1:VERIFIED:25:CN:proof1`).digest('hex');
  assert.equal((await call('/verification/callback','POST',body)).status,403);
  assert.equal((await Identity.findByPk(requestId)).status,'PENDING');
  for(let i=0;i<2;i++)assert.equal((await call('/verification/callback','POST',body,{'X-Identity-Signature':signature})).status,200);
  assert.equal((await Identity.findByPk(requestId)).status,'VERIFIED');
  body.status='REJECTED';const changed=createHmac('sha256','isolated-key').update(`${requestId}:1:REJECTED:25:CN:proof1`).digest('hex');
  assert.equal((await call('/verification/callback','POST',body,{'X-Identity-Signature':changed})).status,409);
 }finally{if(server)await new Promise(resolve=>server.close(resolve));if(db)await db.close();await admin.query(`DROP DATABASE IF EXISTS \`${name}\``);await admin.end();}
});
