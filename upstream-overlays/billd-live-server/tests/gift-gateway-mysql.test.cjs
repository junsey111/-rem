const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const { randomUUID } = require('node:crypto');
const mysql = require('mysql2/promise');
const jwt = require('jsonwebtoken');
const Koa = require('koa');
require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, exports) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename:id, loaded:true, exports };
}
async function listen(server) { server.listen(0,'127.0.0.1'); await once(server,'listening'); return server.address().port; }
async function close(server) {
 if(server?.listening) await new Promise(resolve=>{
  server.close(resolve);
  // Every assertion has consumed its response; retained HTTP clients must not stall teardown.
  server.closeAllConnections?.();
 });
}

test('real gateway + Revenue + MySQL: lost reply, concurrent retries, restart and durable rejection', {
 skip: !process.env.MYSQL_TEST_URL || !process.env.REVENUE_TEST_API_BIN, timeout: 30000,
}, async()=>{
 const base = new URL(process.env.MYSQL_TEST_URL);
 const admin = await mysql.createConnection({host:base.hostname,port:Number(base.port||3306),user:decodeURIComponent(base.username),password:decodeURIComponent(base.password),multipleStatements:true});
 const name = `live_test_gateway_${Date.now()}`;
 const secret = 'isolated-gift-gateway-jwt';
 let api, gateway, accountAuthority, logs='';
 try {
  await admin.query(`CREATE DATABASE \`${name}\``);await admin.query(`USE \`${name}\``);
  const revenueRoot = path.resolve(__dirname,'../../live-revenue-integrity-lab');
  for(const file of fs.readdirSync(path.join(revenueRoot,'migrations')).filter(f=>f.endsWith('.sql')&&!f.startsWith('00002')).sort()) {
   await admin.query(fs.readFileSync(path.join(revenueRoot,'migrations',file),'utf8').split('-- +goose Down')[0]);
  }
  await admin.query(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES (1,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'),(2,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'),(3,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'); INSERT INTO wallet_accounts(user_id,currency,available_balance) VALUES(1,'COIN',100),(3,'COIN',0); INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(22,2,'OPEN',UTC_TIMESTAMP(6));`);
  const reservation = net.createServer();const port = await listen(reservation);await close(reservation);
  // Contract-only account authority for gift idempotency tests; account-state coverage lives in account-authority-mysql.test.cjs.
  accountAuthority=require('node:http').createServer((req,res)=>{
   try{const id=jwt.verify(req.headers.authorization.slice(7),secret).userInfo.id;res.end(JSON.stringify({code:200,data:{id,status:1}}));}
   catch{res.writeHead(401);res.end('{}');}
  });const accountPort=await listen(accountAuthority);
  api=spawn(path.resolve(process.env.REVENUE_TEST_API_BIN),[],{env:{...process.env,BILLD_ACCOUNT_URL:`http://127.0.0.1:${accountPort}/user/me`,HTTP_ADDR:`127.0.0.1:${port}`,MYSQL_DSN:`${decodeURIComponent(base.username)}:${decodeURIComponent(base.password)}@tcp(${base.hostname}:${base.port||3306})/${name}?parseTime=true&multiStatements=true`,JWT_SECRET:secret,INTERNAL_SERVICE_KEY:'isolated-key'},stdio:['ignore','pipe','pipe']});
  api.stdout.on('data',b=>{logs+=b;});api.stderr.on('data',b=>{logs+=b;});
  api.on('error',e=>{logs+=e.message;});
  const revenueUrl=`http://127.0.0.1:${port}`;
  let ready=false;
  for(let i=0;i<80;i++) {try {if((await fetch(revenueUrl+'/healthz')).ok){ready=true;break;}}catch{} await new Promise(resolve=>setTimeout(resolve,50));}
  assert.ok(ready,logs);
  const authority=async(room,key='isolated-key',method='GET')=>{
   const response=await fetch(revenueUrl+'/v1/internal/media-rooms/'+room,{method,headers:{'X-Internal-Key':key}});
   return {status:response.status,data:await response.json()};
  };
  assert.equal((await authority('22','wrong')).status,401);
  assert.equal((await authority('22','isolated-key','POST')).status,405);
  assert.equal((await authority('022')).status,400);
  assert.deepEqual((await authority('22')).data,{room:'22',open:true,directory_open:true,publishers:['2']});
  assert.equal((await authority('999')).data.open,false);
  await admin.query("UPDATE users SET can_go_live=0 WHERE user_id=2");
  assert.equal((await authority('22')).data.open,false);
  await admin.query("UPDATE users SET can_go_live=1,profile_source='UNKNOWN' WHERE user_id=2");
  assert.equal((await authority('22')).data.open,false);
  await admin.query("UPDATE users SET profile_source='SELF_REPORTED' WHERE user_id=2");
  await admin.query("INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(11,1,'OPEN',UTC_TIMESTAMP(6)); INSERT INTO live_matches(match_id,live_session_id,opponent_live_session_id,creator_id,opponent_creator_id,mode,status) VALUES(991,11,22,1,2,'ALL_GIFTS','OPEN')");
  assert.deepEqual((await authority('pk-991')).data,{room:'pk-991',open:true,directory_open:false,publishers:['1','2']});
  assert.deepEqual((await authority('22')).data,{room:'22',open:false,directory_open:true,publishers:['2']});
  await admin.query("UPDATE live_sessions SET status='CLOSED' WHERE live_session_id=11");
  assert.equal((await authority('pk-991')).data.open,false);
  await admin.query("UPDATE live_sessions SET status='OPEN' WHERE live_session_id=11");
  await admin.query("UPDATE users SET can_go_live=0 WHERE user_id=2");
  assert.equal((await authority('pk-991')).data.open,false);
  await admin.query("UPDATE users SET can_go_live=1 WHERE user_id=2");
  await admin.query("UPDATE live_matches SET status='CLOSED' WHERE match_id=991");
  assert.equal((await authority('pk-991')).data.open,false);
  assert.equal((await authority('22')).data.open,true);
  stub('@/config/secret',{REVENUE_API_URL:revenueUrl,INTERNAL_SERVICE_KEY:'isolated-key'});
  // Billd account lookup is outside this isolated DB; both gateway and Revenue verify the signed identity.
  stub('@/app/auth/authJwt',{authJwt:async ctx=>{try{return {code:200,userInfo:jwt.verify(ctx.headers.authorization.slice(7),secret).userInfo};}catch{return {code:401};}}});
  stub('@/controller/log.controller',{__esModule:true,default:{common:{create:async()=>{}}}});
  stub('@/app/handler/success-handle',{__esModule:true,default:({ctx,data})=>{ctx.body=data;}});
  const router=require('../dist/router/revenue.router').default;
  const startGateway=async()=>{
   const app=new Koa();
   app.on('error',require('../dist/app/handler/error-handle').default);
   app.use(async(ctx,next)=>{await next();if(ctx.get('drop-reply')==='yes'){ctx.respond=false;ctx.res.destroy();}});
   app.use(require('../dist/app/app.middleware').catchErrorMiddle);
   app.use(require('koa-bodyparser')());app.use(router.routes());
   gateway=app.callback();const server=require('node:http').createServer(gateway);await listen(server);gateway=server;
  };
  await startGateway();
  const call=async(body,user=1,drop=false)=>{
   const token=jwt.sign({userInfo:{id:user}},secret,{expiresIn:60});
   const response=await fetch(`http://127.0.0.1:${gateway.address().port}/revenue/gifts/send`,{method:'POST',headers:{authorization:`Bearer ${token}`,'Content-Type':'application/json',...(drop?{'drop-reply':'yes'}:{})},body:JSON.stringify(body)});
   return {status:response.status,data:await response.json()};
  };
  const body={request_id:randomUUID(),creator_id:2,live_session_id:22,gift_id:'HEART',quantity:1};
  await assert.rejects(call(body,1,true));
  let [rows]=await admin.query('SELECT available_balance FROM wallet_accounts WHERE user_id=1');assert.equal(rows[0].available_balance,95);
  await close(gateway);await startGateway();
  const retries=await Promise.all(Array.from({length:10},()=>call(body)));
  for(const result of retries) {assert.equal(result.status,200,JSON.stringify(result));assert.equal(result.data.accepted,true);assert.equal(result.data.idempotency,'replayed');assert.equal(result.data.new_balance,95);}
  [rows]=await admin.query('SELECT COUNT(*) AS n FROM wallet_transactions WHERE actor_user_id=1 AND tx_type=\'GIFT_DEBIT\'');assert.equal(rows[0].n,1);
  [rows]=await admin.query('SELECT COUNT(*) AS n FROM gift_orders WHERE viewer_id=1');assert.equal(rows[0].n,1);
  assert.equal((await call({...body,quantity:2})).status,500); // mismatch must not be labeled a safe rejection
  [rows]=await admin.query('SELECT available_balance FROM wallet_accounts WHERE user_id=1');assert.equal(rows[0].available_balance,95);
  assert.equal((await call({...body,request_id:undefined})).status,400);
  const rejected={...body,request_id:randomUUID()};
  let result=await call(rejected,3);assert.equal(result.status,409);assert.equal(result.data.gift_outcome,'REJECTED');
  await admin.query('UPDATE wallet_accounts SET available_balance=100 WHERE user_id=3');
  result=await call(rejected,3);assert.equal(result.data.gift_outcome,'REJECTED');
  [rows]=await admin.query('SELECT available_balance FROM wallet_accounts WHERE user_id=3');assert.equal(rows[0].available_balance,100);
  // Same external key from another authenticated user has an independent ledger scope.
  result=await call(body,3);assert.equal(result.status,200);assert.equal(result.data.new_balance,95);
  [rows]=await admin.query('SELECT COUNT(*) AS n FROM gift_orders WHERE request_id=?',[body.request_id]);assert.equal(rows[0].n,2);
  const read=async(path,user=1,direct=false,method='GET')=>{
   const response=await fetch((direct?revenueUrl:`http://127.0.0.1:${gateway.address().port}`)+path,{method,headers:user?{authorization:`Bearer ${jwt.sign({userInfo:{id:user}},secret,{expiresIn:60})}`}:{}});
   return {status:response.status,data:await response.json()};
  };
  assert.equal((await read('/revenue/gift-orders',null)).status,401);
  assert.equal((await read('/v1/users/me/gifts',null,true)).status,401);
  const accepted=await read('/revenue/gift-orders/'+body.request_id);
  assert.equal(accepted.status,200);assert.equal(accepted.data.status,'ACCEPTED');assert.equal(accepted.data.charged_coins,5);
  assert.equal(typeof accepted.data.gift_order_id,'string');assert.equal(accepted.data.creator_id,'2');assert.equal(accepted.data.match_id,'0');
  assert.ok(!('body_hash'in accepted.data));assert.ok(!('viewer_id'in accepted.data));
  const refused=await read('/revenue/gift-orders/'+rejected.request_id,3);
  assert.equal(refused.data.status,'REJECTED');assert.equal(refused.data.charged_coins,0);
  assert.equal((await read('/revenue/gift-orders/'+rejected.request_id)).status,404);
  assert.equal((await read('/revenue/gift-orders/'+randomUUID())).status,404);
  const sameKey=await read('/revenue/gift-orders/'+body.request_id,3);
  assert.notEqual(sameKey.data.gift_order_id,accepted.data.gift_order_id);
  for(const cursor of ['0','bad','1&before_id=2']) {
   assert.equal((await read('/v1/users/me/gifts?before_id='+cursor,1,true)).status,400);
   assert.equal((await read('/revenue/gift-orders?before_id='+cursor)).status,400);
  }
  assert.equal((await read('/v1/users/me/gifts/bad!',1,true)).status,400);
  assert.equal((await read('/revenue/gift-orders/bad!')).status,400);
  assert.equal((await read('/v1/users/me/gifts',1,true,'POST')).status,405);
  assert.equal((await read('/v1/users/me/gifts/'+body.request_id,1,true,'POST')).status,405);
  // Generate actual isolated gift transactions, rather than fake history rows.
  await admin.query('UPDATE wallet_accounts SET available_balance=1000 WHERE user_id=1');
  for(let i=0;i<27;i++)assert.equal((await call({...body,request_id:randomUUID()})).status,200);
  const first=await read('/revenue/gift-orders');assert.equal(first.data.orders.length,25);assert.ok(first.data.next_before_id);
  assert.equal((await call({...body,request_id:randomUUID()})).status,200);
  const second=await read('/revenue/gift-orders?before_id='+first.data.next_before_id);
  assert.equal(second.data.orders.length,3);assert.equal(second.data.next_before_id,'');
  const ids=new Set(first.data.orders.map(o=>o.gift_order_id));
  for(const order of second.data.orders)assert.ok(!ids.has(order.gift_order_id));
  assert.equal(second.data.orders.at(-1).request_id,body.request_id);
  await admin.query("UPDATE live_sessions SET status='CLOSED' WHERE live_session_id=22");
  assert.equal((await read('/revenue/gift-orders/'+body.request_id)).data.status,'ACCEPTED');
  assert.deepEqual((await authority('22')).data,{room:'22',open:false,directory_open:false,publishers:[]});
 } finally {
  await close(gateway);
  await close(accountAuthority);
  if(api && api.exitCode===null){const done=once(api,'exit');api.kill();await done;}
  await admin.query(`DROP DATABASE IF EXISTS \`${name}\``);await admin.end();
 }
});
