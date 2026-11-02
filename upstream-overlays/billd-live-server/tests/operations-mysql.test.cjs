const assert=require('node:assert/strict');
const {test}=require('node:test');
const path=require('node:path');
const Koa=require('koa');
const Router=require('koa-router');
const mysql=require('mysql2/promise');
const {Sequelize}=require('sequelize');
const jwt=require('jsonwebtoken');
const fs=require('node:fs');
const net=require('node:net');
const {spawn}=require('node:child_process');
const {once}=require('node:events');
const {randomUUID}=require('node:crypto');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}
test('real role associations, account sessions and MySQL protect operations and all admin paths',{skip:!process.env.MYSQL_TEST_URL,timeout:30000},async()=>{
 const url=new URL(process.env.MYSQL_TEST_URL),name=`live_test_operations_${Date.now()}`;
 const admin=await mysql.createConnection({host:url.hostname,port:Number(url.port||3306),user:decodeURIComponent(url.username),password:decodeURIComponent(url.password)});
 let db,server,revenue,restartRevenue,accountAuthority;
 try{
  await admin.query(`CREATE DATABASE \`${name}\``);
  db=new Sequelize(name,decodeURIComponent(url.username),decodeURIComponent(url.password),{host:url.hostname,port:Number(url.port||3306),dialect:'mysql',logging:false});
  const secret='isolated-operations-jwt';
  let revenueUrl;
  if(process.env.REVENUE_TEST_API_BIN){
   await admin.query(`USE \`${name}\``);
   const root=path.resolve(__dirname,'../../live-revenue-integrity-lab');
   for(const file of fs.readdirSync(path.join(root,'migrations')).filter(f=>f.endsWith('.sql')&&!f.startsWith('00002')).sort()){
    const sql=fs.readFileSync(path.join(root,'migrations',file),'utf8').split('-- +goose Down')[0];
    const migration=await mysql.createConnection({host:url.hostname,port:Number(url.port||3306),user:decodeURIComponent(url.username),password:decodeURIComponent(url.password),database:name,multipleStatements:true});
    try{await migration.query(sql);}finally{await migration.end();}
   }
   await admin.query("INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(2,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'),(4,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL')");
   await admin.query("INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(22,2,'OPEN',UTC_TIMESTAMP(6)),(44,4,'OPEN',UTC_TIMESTAMP(6))");
   await admin.query("INSERT INTO live_matches(match_id,live_session_id,opponent_live_session_id,creator_id,opponent_creator_id,mode,status) VALUES(991,22,44,2,4,'ALL_GIFTS','OPEN')");
   const reservation=net.createServer();reservation.listen(0,'127.0.0.1');await once(reservation,'listening');const port=reservation.address().port;await new Promise(r=>reservation.close(r));
   revenueUrl=`http://127.0.0.1:${port}`;
   accountAuthority=require('node:http').createServer(async(req,res)=>{
    try{
     const token=req.headers.authorization.slice(7),id=jwt.verify(token,secret).userInfo.id;
     const [rows]=await admin.query('SELECT id,status,token FROM user WHERE id=? AND deleted_at IS NULL',[id]);
     if(!rows[0]||rows[0].status===2||rows[0].token!==token){res.writeHead(401);res.end('{}');return;}
     res.end(JSON.stringify({code:200,data:{id:rows[0].id,status:rows[0].status}}));
    }catch{res.writeHead(401);res.end('{}');}
   });accountAuthority.listen(0,'127.0.0.1');await once(accountAuthority,'listening');
   restartRevenue=async()=>{
    if(revenue&&revenue.exitCode===null){const done=once(revenue,'exit');revenue.kill();await done;}
    revenue=spawn(path.resolve(process.env.REVENUE_TEST_API_BIN),[],{env:{...process.env,BILLD_ACCOUNT_URL:`http://127.0.0.1:${accountAuthority.address().port}/user/me`,HTTP_ADDR:`127.0.0.1:${port}`,MYSQL_DSN:`${decodeURIComponent(url.username)}:${decodeURIComponent(url.password)}@tcp(${url.hostname}:${url.port||3306})/${name}?parseTime=true`,JWT_SECRET:secret,INTERNAL_SERVICE_KEY:'isolated-operations-key'},stdio:'ignore'});
    let ready=false;for(let i=0;i<80;i++){try{if((await fetch(revenueUrl+'/healthz')).ok){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,50));}assert.ok(ready,'Revenue must start');
   };await restartRevenue();
  }
  stub('@/config/mysql',{__esModule:true,default:db});stub('@/config/secret',{JWT_SECRET:secret,IP_WHITE_LIST:[],REVENUE_API_URL:revenueUrl,INTERNAL_SERVICE_KEY:'isolated-operations-key'});stub('@/utils',{initTable:()=>{}});
  const user=require('../dist/model/user.model').default;
  const role=require('../dist/model/role.model').default;
  const link=require('../dist/model/userRole.model').default;
  const live=require('../dist/model/live.model').default;
  const order=require('../dist/model/rechargeOrder.model').default;
  user.belongsToMany(role,{foreignKey:'user_id',otherKey:'role_id',constraints:false,through:{model:link,unique:false}});
  await db.sync();
  await user.bulkCreate(Array.from({length:30},(_,i)=>({id:i+1,username:`isolated-${i+1}`,password:'private-password',status:i===2?2:1})));
  await user.destroy({where:{id:30}});
  await role.bulkCreate([{id:1,role_name:'Admin',role_value:'SUPER_ADMIN'},{id:2,role_name:'Viewer',role_value:'VIEWER'}]);
  await link.bulkCreate([{user_id:1,role_id:1},{user_id:2,role_id:2}]);
  await live.bulkCreate([{roomId:'fixture-1',socketId:'fixture-1',data:'{}'},{roomId:'fixture-2',socketId:'fixture-2',data:'{}'}]);
  await order.bulkCreate([{request_id:'isolated-pending',user_id:2,coins:100,amount_fen:100,status:'PENDING'},{request_id:'isolated-credited',user_id:2,coins:100,amount_fen:100,status:'CREDITED'}]);
  stub('@/service/user.service',{__esModule:true,default:{findAndToken:id=>user.findOne({where:{id},attributes:{exclude:['password']}})}});
  stub('@/controller/blacklist.controller',{__esModule:true,default:{findByIp:async()=>null,common:{create:async()=>{}}}});
  stub('@/controller/log.controller',{__esModule:true,default:{common:{getCount:async()=>0,create:async()=>{}}}});
  const app=new Koa();app.on('error',require('../dist/app/handler/error-handle').default);
  app.use(async(ctx,next)=>{await next();if(ctx.get('drop-reply')==='yes'){ctx.respond=false;ctx.res.destroy();}});
  app.use(require('../dist/app/app.middleware').catchErrorMiddle);app.use(require('koa-bodyparser')());
  app.use(require('../dist/app/verify.middleware').apiBeforeVerify);
  const operations=require('../dist/router/operations.router').default;
  app.use(operations.routes());
  const aliases=new Router();aliases.use('/admin',operations.routes());aliases.get('/admin/private',ctx=>{ctx.body={reached:true};});
  aliases.post('/admin/user/login',ctx=>{ctx.body={login:true};});app.use(aliases.routes());
  server=app.listen(0,'127.0.0.1');await new Promise(resolve=>server.once('listening',resolve));
  const tokens={};for(const id of [1,2,3,4]){tokens[id]=jwt.sign({userInfo:{id,roles:['SUPER_ADMIN']}},secret,{expiresIn:60});await user.update({token:tokens[id]},{where:{id}});}
  const call=async(path,id,method='GET',token,body,drop=false)=>{
   const r=await fetch(`http://127.0.0.1:${server.address().port}${path}`,{method,headers:{...(id?{authorization:`Bearer ${token||tokens[id]}`} : {}),'Content-Type':'application/json',...(drop?{'drop-reply':'yes'}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,data:await r.json()};
  };
  assert.equal((await call('/operations/access')).status,401);
  assert.equal((await call('/operations/access',2)).data.data.can_manage,false);
  assert.equal((await call('/operations/summary',2)).status,403);
  assert.equal((await call('/operations/users',2)).status,403);
  assert.equal((await call('/admin/operations/summary',2)).status,403);
  for(const method of ['GET','POST','DELETE'])assert.equal((await call('/admin/private',2,method)).status,403);
  assert.equal((await call('/admin/private')).status,401);
  assert.equal((await call('/admin/user/login',undefined,'POST')).status,200);
  assert.equal((await call('/operations/access?query=/admin/private',2)).data.data.can_manage,false);
  assert.equal((await call('/operations/access',1)).data.data.can_manage,true);
  const summary=(await call('/operations/summary',1)).data.data;
  assert.deepEqual({...summary,observed_at:undefined},{users:29,disabled_users:1,listed_rooms:2,pending_orders:1,credited_orders:1,observed_at:undefined});
  assert.ok(Number.isFinite(Date.parse(summary.observed_at)));
  const first=(await call('/operations/users',1)).data.data,second=(await call('/operations/users?page=2',1)).data.data;
  assert.equal(first.users.length,25);assert.equal(second.users.length,4);assert.equal(first.total,29);
  assert.equal(new Set([...first.users,...second.users].map(u=>u.id)).size,29);
  assert.deepEqual(Object.keys(first.users[0]).sort(),['created_at','id','status','username']);
  for(const page of ['0','-1','abc','1.5','1000000'])assert.equal((await call('/operations/users?page='+page,1)).status,400);
  const command={request_id:randomUUID(),reason:'Isolated policy violation',operator_id:999};
  for(const endpoint of ['/operations/lives','/operations/actions','/operations/actions/'+command.request_id]){
   assert.equal((await call(endpoint,2)).status,403);assert.equal((await call(endpoint)).status,401);
  }
  assert.equal((await call('/operations/lives/22/close',2,'POST',undefined,command)).status,403);
  assert.equal((await call('/admin/operations/lives/22/close',2,'POST',undefined,command)).status,403);
  if(revenueUrl){
   const direct=async(path,method='GET',body,key='isolated-operations-key')=>{const r=await fetch(revenueUrl+'/v1/internal/operations/'+path,{method,headers:{'X-Internal-Key':key,'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,data:await r.json()};};
   assert.equal((await direct('lives','GET',undefined,'wrong')).status,401);
   assert.equal((await direct('lives','POST')).status,405);
   assert.equal((await direct('lives/22/close','GET')).status,405);
   assert.equal((await call('/operations/actions/'+command.request_id,1)).status,404);
   for(const cursor of ['0','bad','1&before_id=2'])assert.equal((await call('/operations/lives?before_id='+cursor,1)).status,400);
   for(const body of [{...command,reason:''},{...command,reason:'a'.repeat(201)},{...command,request_id:'bad'}]){const invalid=await call('/operations/lives/22/close',1,'POST',undefined,body);assert.equal(invalid.status,400);assert.equal(invalid.data.moderation_outcome,'REJECTED');}
   const before=(await call('/operations/lives',1)).data.data;assert.deepEqual(before.lives.map(l=>l.live_session_id),['44','22']);
   await assert.rejects(call('/operations/lives/22/close',1,'POST',undefined,command,true));
   await restartRevenue();
   const replies=await Promise.all(Array.from({length:10},()=>call('/operations/lives/22/close',1,'POST',undefined,command)));
   for(const result of replies){assert.equal(result.status,200);assert.equal(result.data.data.operator_id,'1');assert.equal(result.data.data.reason,command.reason);}
   const hostClose=await fetch(revenueUrl+'/v1/lives/22/close',{method:'POST',headers:{Authorization:`Bearer ${tokens[2]}`}});assert.equal(hostClose.status,202);
   await admin.query("INSERT INTO wallet_accounts(user_id,currency,available_balance) VALUES(4,'COIN',100)");
   const gift=await fetch(revenueUrl+'/v1/gifts',{method:'POST',headers:{Authorization:`Bearer ${tokens[4]}`,'Content-Type':'application/json'},body:JSON.stringify({request_id:randomUUID(),creator_id:2,live_session_id:22,gift_id:'HEART',quantity:1,sent_at_ms:0})});
   assert.equal(gift.status,409);assert.equal((await gift.json()).code,'LIVE_SESSION_CLOSED');
   const [[balance]]=await admin.query("SELECT available_balance FROM wallet_accounts WHERE user_id=4");assert.equal(balance.available_balance,100);
   const [[counts]]=await admin.query("SELECT (SELECT COUNT(*) FROM moderation_actions) audits,(SELECT COUNT(*) FROM outbox_events WHERE topic='live.session.closed.v1') events");
   assert.equal(counts.audits,1);assert.equal(counts.events,1);
   const [[match]]=await admin.query('SELECT status FROM live_matches WHERE match_id=991');assert.equal(match.status,'CLOSED');
   assert.equal((await call('/operations/actions/'+command.request_id,1)).data.data.request_id,command.request_id);
   assert.equal((await call('/operations/lives/44/close',1,'POST',undefined,command)).status,409);
   assert.equal((await call('/operations/lives/22/close',1,'POST',undefined,{...command,reason:'changed'})).status,409);
   const missing=await call('/operations/lives/999/close',1,'POST',undefined,{...command,request_id:randomUUID()});assert.equal(missing.status,404);assert.equal(missing.data.moderation_outcome,'REJECTED');
   await admin.query("CREATE TRIGGER reject_close_event BEFORE INSERT ON outbox_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated failure'");
   const retry={request_id:randomUUID(),reason:'Retry after atomic failure'};
   assert.equal((await call('/operations/lives/44/close',1,'POST',undefined,retry)).status,503);
   const [[unchanged]]=await admin.query('SELECT status FROM live_sessions WHERE live_session_id=44');assert.equal(unchanged.status,'OPEN');
   const [[notLogged]]=await admin.query('SELECT COUNT(*) n FROM moderation_actions WHERE request_id=?',[retry.request_id]);assert.equal(notLogged.n,0);
   await admin.query('DROP TRIGGER reject_close_event');
   assert.equal((await call('/operations/lives/44/close',1,'POST',undefined,retry)).status,200);
   // An old request must not act again even if an internal migration reopened the same ID.
   await admin.query("UPDATE live_sessions SET status='OPEN' WHERE live_session_id=22");
   assert.equal((await call('/operations/lives/22/close',1,'POST',undefined,command)).status,200);
   const [[reopened]]=await admin.query('SELECT status FROM live_sessions WHERE live_session_id=22');assert.equal(reopened.status,'OPEN');
   // List cursor correctness under insertion, using real close actions in this isolated database.
   for(let i=0;i<27;i++)await call('/operations/lives/44/close',1,'POST',undefined,{request_id:randomUUID(),reason:'Isolated history pagination'});
   const page1=(await call('/operations/actions',1)).data.data;assert.equal(page1.actions.length,25);assert.ok(page1.next_before_id);
   await call('/operations/lives/44/close',1,'POST',undefined,{request_id:randomUUID(),reason:'Inserted between pages'});
   const page2=(await call('/operations/actions?before_id='+page1.next_before_id,1)).data.data;assert.equal(page2.actions.length,4);assert.equal(page2.next_before_id,'');
   const ids=new Set(page1.actions.map(a=>a.action_id));for(const action of page2.actions)assert.ok(!ids.has(action.action_id));
   assert.equal((await direct('actions?before_id=1&before_id=2')).status,400);
   for(let i=100;i<127;i++){
    const response=await fetch(revenueUrl+'/v1/internal/lives',{method:'POST',headers:{'X-Internal-Key':'isolated-operations-key','Content-Type':'application/json'},body:JSON.stringify({live_session_id:i,creator_id:2})});assert.ok(response.ok);
   }
   const lives1=(await call('/operations/lives',1)).data.data;assert.equal(lives1.lives.length,25);assert.ok(lives1.next_before_id);
   const response=await fetch(revenueUrl+'/v1/internal/lives',{method:'POST',headers:{'X-Internal-Key':'isolated-operations-key','Content-Type':'application/json'},body:JSON.stringify({live_session_id:9999,creator_id:2})});assert.ok(response.ok);
   const lives2=(await call('/operations/lives?before_id='+lives1.next_before_id,1)).data.data;assert.equal(lives2.lives.length,3);assert.equal(lives2.next_before_id,'');
   const liveIds=new Set(lives1.lives.map(l=>l.live_session_id));for(const live of lives2.lives)assert.ok(!liveIds.has(live.live_session_id));
   const unicode={request_id:randomUUID(),reason:' \uFEFF保留原因原文 😃\u00a0 '};
   const exact=(await call('/operations/lives/44/close',1,'POST',undefined,unicode)).data.data;
   assert.equal(exact.reason,unicode.reason);
  }
  assert.equal((await call('/operations/summary',3)).status,401);
  await user.update({token:'rotated'},{where:{id:1}});assert.equal((await call('/operations/summary',1)).status,401);
  await user.update({token:tokens[1]},{where:{id:1}});
  await link.destroy({where:{user_id:1}});assert.equal((await call('/operations/access',1)).data.data.can_manage,false);assert.equal((await call('/operations/users',1)).status,403);
  assert.equal((await call('/operations/lives/22/close',1,'POST',undefined,command)).status,403);
  await link.restore({where:{user_id:1}});await role.destroy({where:{id:1}});assert.equal((await call('/admin/private',1)).status,403);
  await role.restore({where:{id:1}});assert.equal((await call('/admin/private',1)).status,200);
  await user.destroy({where:{id:1}});assert.equal((await call('/operations/summary',1)).status,401);
 }finally{if(accountAuthority)await new Promise(resolve=>accountAuthority.close(resolve));if(server)await new Promise(resolve=>server.close(resolve));if(revenue&&revenue.exitCode===null){const done=once(revenue,'exit');revenue.kill();await done;}if(db)await db.close();await admin.query(`DROP DATABASE IF EXISTS \`${name}\``);await admin.end();}
});
