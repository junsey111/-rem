const assert=require('node:assert/strict');
const {test}=require('node:test');
const path=require('node:path');
const http=require('node:http');
const {once}=require('node:events');
const mysql=require('mysql2/promise');
const {Sequelize}=require('sequelize');
const jwt=require('jsonwebtoken');
const Koa=require('koa');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}
async function listen(server){server.listen(0,'127.0.0.1');await once(server,'listening');return server.address().port;}

test('MySQL durable IM session intent survives lost replies, rollback, rotation, disable, expiry and concurrent issuance',{
 skip:!process.env.MYSQL_TEST_URL,timeout:30000,
},async()=>{
 const url=new URL(process.env.MYSQL_TEST_URL),name='live_test_im_'+Date.now(),secret='isolated-im-app-key';
 const admin=await mysql.createConnection({host:url.hostname,port:Number(url.port||3306),user:decodeURIComponent(url.username),password:decodeURIComponent(url.password)});
 let db,server,im;const tokens=new Map(),mediaStates=new Map();let issued=0,revoked=0,registered=[];
 let failRevoke=false,dropIssue=false,pauseIssue,issueStarted,registerMode='success';
 try{
  await admin.query(`CREATE DATABASE \`${name}\``);await admin.query(`USE \`${name}\``);
  db=new Sequelize(name,decodeURIComponent(url.username),decodeURIComponent(url.password),{host:url.hostname,port:Number(url.port||3306),dialect:'mysql',logging:false});
  // OpenIM RPC responses are an isolated HTTP contract fixture; this is not real OpenIM validation.
  im=http.createServer(async(req,res)=>{
   const chunks=[];for await(const chunk of req)chunks.push(chunk);const raw=Buffer.concat(chunks).toString();const body=raw?JSON.parse(raw):{};
   let data={};
   if(req.url.startsWith('/v1/internal/media-rooms/')){
    assert.equal(req.headers['x-internal-key'],'isolated-revenue-key');
    const room=decodeURIComponent(req.url.slice('/v1/internal/media-rooms/'.length));
    res.setHeader('Content-Type','application/json');res.end(JSON.stringify(mediaStates.get(room)||{room,open:false,directory_open:false,publishers:[]}));return;
   }else if(req.url==='/auth/get_admin_token')data={token:'isolated-admin'};
   else if(req.url==='/auth/parse_token'){
    const state=tokens.get(body.token);if(!state?.valid){res.writeHead(401);res.end(JSON.stringify({errCode:1506}));return;}
    data={userID:state.userID,platformID:state.platformID};
   }else{
    assert.equal(req.headers.token,'isolated-admin');
    if(req.url==='/auth/force_logout'){
     if(failRevoke){res.writeHead(503);res.end(JSON.stringify({errCode:500}));return;}
     revoked++;
     for(const state of tokens.values())if(state.userID===body.userID&&state.platformID===body.platformID)state.valid=false;
    }else if(req.url==='/user/user_register'){
     registered.push(...body.users);
     if(registerMode!=='success'){
      res.writeHead(registerMode==='duplicate'?400:503,{'Content-Type':'application/json'});
      res.end(JSON.stringify({errCode:registerMode==='duplicate'?1102:500}));return;
     }
    }else if(req.url==='/auth/get_user_token'){
     issued++;const token=jwt.sign({userID:body.userID,platformID:body.platformID},'isolated-im-token',{expiresIn:3600,jwtid:String(issued)});
     tokens.set(token,{...body,valid:true});data={token};
     if(pauseIssue){issueStarted();await pauseIssue;}
     if(dropIssue){res.destroy();return;}
    }
   }
   res.setHeader('Content-Type','application/json');res.end(JSON.stringify({errCode:0,data}));
  });
  const imPort=await listen(im);
  const imUrl=`http://127.0.0.1:${imPort}`;
  stub('@/config/mysql',{__esModule:true,default:db});stub('@/config/secret',{JWT_SECRET:secret,OPENIM_API_URL:imUrl,OPENIM_PLATFORM_ID:1,OPENIM_SECRET:'isolated-secret',OPENIM_ADMIN_USER_ID:'imAdmin',OPENIM_PUBLIC_API_URL:'http://isolated',OPENIM_WS_URL:'ws://isolated',REVENUE_API_URL:imUrl,INTERNAL_SERVICE_KEY:'isolated-revenue-key'});
  stub('@/utils',{initTable:()=>{}});
  const user=require('../dist/model/user.model').default,session=require('../dist/model/openimSession.model').default,live=require('../dist/model/live.model').default;
  await user.sync();await live.sync();
  const migration=require('node:fs').readFileSync(path.resolve(__dirname,'../migrations/20261102_openim_sessions.sql'),'utf8');
  await admin.query(migration);await admin.query(migration);await session.sync();
  await user.bulkCreate([1,2].map(id=>({id,username:'Real stored '+id,password:'ExactPassword',status:1})));
  for(const [id,creator,open] of [['11',2,true],['12',1,true],['13',2,false]]){
   const data={roomId:id,roomName:`Room ${id}`,isAdmin:true,data:{userId:creator,username:`Real stored ${creator}`}};
   await live.create({socketId:`socket-${id}`,roomId:id,data:JSON.stringify(data)});
   mediaStates.set(id,{room:id,open,directory_open:open,publishers:open?[String(creator)]:[]});
  }
  stub('@/service/user.service',{__esModule:true,default:{findAndToken:id=>user.findByPk(id,{attributes:{exclude:['password']}})}});
  const {signJwt}=require('../dist/app/auth/authJwt');let token=signJwt({userInfo:{id:1},exp:24});await user.update({token},{where:{id:1}});
  stub('@/controller/user.controller',{__esModule:true,default:{create:()=>{},login:require('../dist/app/auth/password-login').passwordLogin}});
  const app=new Koa();app.on('error',require('../dist/app/handler/error-handle').default);
  app.use(require('../dist/app/app.middleware').catchErrorMiddle);app.use(require('koa-bodyparser')());
  app.use(async(ctx,next)=>{await next();if(ctx.get('drop-reply')==='yes'){ctx.respond=false;ctx.res.destroy();}});
  app.use(require('../dist/router/openim.router').default.routes());app.use(require('../dist/router/user.router').default.routes());
  server=http.createServer(app.callback());const port=await listen(server);
  const call=async(route='/openim/session',auth=token,body={},drop=false)=>{
   const response=await fetch(`http://127.0.0.1:${port}${route}`,{method:'POST',headers:{'Content-Type':'application/json',...(auth?{Authorization:'Bearer '+auth}:{}),...(drop?{'drop-reply':'yes'}:{})},body:JSON.stringify(body)});
   return {status:response.status,data:await response.json()};
  };
  assert.equal((await call('/openim/rooms/11/recipient','')).status,401);
  assert.equal((await call('/openim/rooms/not-a-room/recipient')).status,400);
  let recipient=await call('/openim/rooms/11/recipient');
  assert.equal(recipient.status,200);assert.deepEqual(recipient.data.data,{user_id:'2',name:'Real stored 2'});
  assert.deepEqual(registered,[{userID:'2',nickname:'Real stored 2',faceURL:''}]);
  assert.equal(issued,0);assert.equal(revoked,0);assert.equal(await session.count(),0);
  assert.equal((await call('/openim/rooms/12/recipient')).status,400);
  assert.equal((await call('/openim/rooms/13/recipient')).status,404);
  mediaStates.set('11',{room:'11',open:true,directory_open:false,publishers:['2']});
  assert.equal((await call('/openim/rooms/11/recipient')).status,404);
  mediaStates.set('11',{room:'11',open:true,directory_open:true,publishers:['1']});
  assert.equal((await call('/openim/rooms/11/recipient')).status,404);
  mediaStates.set('11',{room:'11',open:true,directory_open:true,publishers:['2']});
  mediaStates.set('11',{room:'wrong',open:true,directory_open:true,publishers:['2']});
  assert.equal((await call('/openim/rooms/11/recipient')).status,503);
  assert.equal(registered.length,1);
  mediaStates.set('11',{room:'11',open:true,directory_open:true,publishers:['2']});
  await user.update({status:2},{where:{id:2}});
  assert.equal((await call('/openim/rooms/11/recipient')).status,404);
  assert.equal(registered.length,1);assert.equal(issued,0);assert.equal(revoked,0);
  await user.update({status:1},{where:{id:2}});
  let service=require('../dist/service/openim-session');
  assert.equal((await call('/openim/session','')).status,401);assert.equal(issued,0);
  const simultaneous=await Promise.all(Array.from({length:4},()=>call()));assert.ok(simultaneous.every(r=>r.status===200));
  const first=simultaneous[0].data.data.token;assert.equal(new Set(simultaneous.map(r=>r.data.data.token)).size,1);assert.equal(issued,1);
  const stored=await session.findOne();assert.notEqual(stored.session_hash,token);assert.equal(stored.session_hash.length,64);
  // Resolving an already-registered host in PK must not rotate or revoke their existing IM session.
  const secondAccountToken=signJwt({userInfo:{id:2},exp:24});await user.update({token:secondAccountToken},{where:{id:2}});
  mediaStates.set('12',{room:'12',open:false,directory_open:true,publishers:['1']});
  const revokeCount=revoked,issuedCount=issued,beforeResolve=stored.toJSON();registerMode='duplicate';
  recipient=await call('/openim/rooms/12/recipient',secondAccountToken);
  assert.equal(recipient.status,200);assert.deepEqual(recipient.data.data,{user_id:'1',name:'Real stored 1'});
  assert.equal(issued,issuedCount);assert.equal(revoked,revokeCount);assert.equal(tokens.get(first).valid,true);
  assert.deepEqual((await session.findOne()).toJSON(),beforeResolve);
  registerMode='failure';assert.equal((await call('/openim/rooms/12/recipient',secondAccountToken)).status,503);
  assert.equal(tokens.get(first).valid,true);assert.equal(issued,issuedCount);assert.equal(revoked,revokeCount);registerMode='success';
  await service.reconcileOpenImSessions();assert.equal(tokens.get(first).valid,true);
  // Upstream revocation must not trap clients in a cached revoked token.
  tokens.get(first).valid=false;const refreshed=await call();assert.equal(refreshed.status,200);assert.notEqual(refreshed.data.data.token,first);
  const second=refreshed.data.data.token;
  token=signJwt({userInfo:{id:1},exp:24});await user.update({token},{where:{id:1}});
  failRevoke=true;await assert.rejects(service.reconcileOpenImSessions());assert.equal(await session.count(),1);assert.equal(tokens.get(second).valid,true);
  failRevoke=false;
  // Reload worker module to prove retries use database state, not an in-memory queue.
  delete require.cache[require.resolve('../dist/service/openim-session')];service=require('../dist/service/openim-session');
  await service.reconcileOpenImSessions();assert.equal(tokens.get(second).valid,false);assert.equal(await session.count(),0);
  const third=(await call()).data.data.token;
  await user.update({status:2},{where:{id:1}});assert.equal((await call()).status,401);
  await service.reconcileOpenImSessions();assert.equal(tokens.get(third).valid,false);await user.update({status:1},{where:{id:1}});
  // A remotely issued token with a lost HTTP reply must leave a committed cleanup intent.
  dropIssue=true;assert.equal((await call()).status,503);assert.equal((await session.findOne()).pending,true);
  const leaked=[...tokens.keys()].at(-1);assert.equal(tokens.get(leaked).valid,true);dropIssue=false;
  await service.reconcileOpenImSessions();assert.equal(tokens.get(leaked).valid,false);
  // A local commit failure after successful remote issuance must have the same recovery.
  await admin.query("CREATE TRIGGER reject_im_commit BEFORE UPDATE ON openim_session FOR EACH ROW BEGIN IF NEW.pending=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated IM commit failure'; END IF; END");
  assert.equal((await call()).status,503);assert.equal((await session.findOne()).pending,true);
  const rolledBack=[...tokens.keys()].at(-1);assert.equal(tokens.get(rolledBack).valid,true);
  await admin.query('DROP TRIGGER reject_im_commit');await service.reconcileOpenImSessions();assert.equal(tokens.get(rolledBack).valid,false);
  // Revocation racing with an in-flight issue waits for the user row lock, then sees rotation.
  let release;pauseIssue=new Promise(resolve=>{release=resolve;});const started=new Promise(resolve=>{issueStarted=resolve;});
  const inFlight=call();await started;
  const nextToken=signJwt({userInfo:{id:1},exp:24});const rotation=user.update({token:nextToken},{where:{id:1}});
  release();await inFlight;await rotation;pauseIssue=null;token=nextToken;await service.reconcileOpenImSessions();assert.equal([...tokens.values()].some(s=>s.valid),false);
  const beforeChange=(await call()).data.data.token;
  assert.equal((await call('/user/password',token,{oldpwd:'ExactPassword',newpwd:'ChangedPassword'})).status,200);
  await service.reconcileOpenImSessions();assert.equal(tokens.get(beforeChange).valid,false);
  const logged=await call('/user/login','',{id:1,password:'ChangedPassword'});assert.equal(logged.status,200);token=logged.data.data;
  const beforeLogout=(await call()).data.data.token;
  await assert.rejects(call('/user/logout',token,{},true));assert.equal((await user.findByPk(1)).token,null);
  await service.reconcileOpenImSessions();assert.equal(tokens.get(beforeLogout).valid,false);assert.equal((await call('/user/logout')).status,401);
  token=jwt.sign({userInfo:{id:1}},secret,{expiresIn:-1});await user.update({token},{where:{id:1}});
  await session.create({user_id:1,platform_id:1,session_hash:require('node:crypto').createHash('sha256').update(token).digest('hex'),im_token:beforeLogout,expires_at:Math.floor(Date.now()/1000)+3600,pending:false});
  await service.reconcileOpenImSessions();assert.equal(await session.count(),0);
 }finally{
  if(server)await new Promise(resolve=>server.close(resolve));if(im)await new Promise(resolve=>im.close(resolve));
  if(db)await db.close();await admin.query(`DROP DATABASE IF EXISTS \`${name}\``);await admin.end();
 }
});
