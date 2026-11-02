const assert = require('node:assert/strict');
const { test } = require('node:test');
const crypto = require('node:crypto');
const path = require('node:path');
const Koa = require('koa');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports) {const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}
const merchant=crypto.generateKeyPairSync('rsa',{modulusLength:2048,privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});
const provider=crypto.generateKeyPairSync('rsa',{modulusLength:2048,privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});
const config={appId:'202611020000001',privateKey:merchant.privateKey,alipayPublicKey:provider.publicKey};
const orders=new Map(), credits=new Map();
let failLedger=false,failUpdate=false;
let queryMode='outage',queryCalls=0;
config.urllib={request:async(url,options)=>{
 queryCalls++;
 assert.equal(new URL(url).searchParams.get('method'),'alipay.trade.query');
 const id=JSON.parse(options.data.biz_content).out_trade_no;
 if(queryMode==='outage')throw Error('fixture timeout');
 const result={code:'10000',out_trade_no:id,trade_status:'TRADE_SUCCESS',trade_no:'202611029999000000',total_amount:'1.00'};
 if(queryMode==='pending')result.trade_status='WAIT_BUYER_PAY';
 if(queryMode==='amount')result.total_amount='0.01';
 if(queryMode==='order')result.out_trade_no='other-order';
 if(queryMode==='missing')result.code='40004';
 const raw=JSON.stringify(result);
 const sign=crypto.sign('RSA-SHA256',Buffer.from(raw),provider.privateKey).toString('base64');
 return {status:200,headers:{},data:JSON.stringify({alipay_trade_query_response:result,sign:queryMode==='signature'?'broken':sign})};
}};
stub('@/config/secret',{ALIPAY_CONFIG:config,PAYMENT_WEBHOOK_SECRET:'payment-test-key',PUBLIC_BASE_URL:'https://merchant.example',REVENUE_API_URL:'http://ledger'});
stub('@/app/auth/authJwt',{authJwt:async ctx=>({code:ctx.headers.authorization?200:401,userInfo:ctx.headers.authorization?{id:Number(ctx.headers.authorization.slice(7))}:undefined})});
stub('@/app/handler/success-handle',{__esModule:true,default:({ctx,data})=>{ctx.body=data;}});
stub('@/model/rechargeOrder.model',{__esModule:true,default:{
 create:async row=>{const order={...row,payment_ref:null,reload:async()=>order,update:async values=>{if(failUpdate)throw Error('fixture db down');Object.assign(order,values);}};orders.set(row.request_id,order);return order;},
 update:async(values,{where})=>{
  const order=orders.get(where.request_id);
  if(!order||order.status!=='PENDING'||(order.last_checked_at&&Date.now()-order.last_checked_at.getTime()<30000))return [0];
  Object.assign(order,values);return [1];
 },
 findByPk:async id=>orders.get(id),
 findOne:async({where})=>{const o=orders.get(where.request_id);return o?.user_id===where.user_id?o:null;},
 findAll:async({where})=>[...orders.values()].filter(o=>o.user_id===where.user_id),
}});
stub('@/utils/request',{__esModule:true,default:{post:async(url,body,opts)=>{
 if(failLedger)throw Error('fixture ledger down');
 const signature=crypto.createHmac('sha256','payment-test-key').update(`${body.request_id}:${body.viewer_id}:${body.coins}:${body.payment_ref}`).digest('base64url');
 assert.equal(opts.headers['X-Payment-Signature'],signature);
 if(credits.has(body.request_id))assert.deepEqual(credits.get(body.request_id),body);else credits.set(body.request_id,body);
 return {};
}}});
function signed(data) {
 const body={app_id:config.appId,trade_status:'TRADE_SUCCESS',trade_no:'202611021234567890',total_amount:'1.00',...data};
 const content=Object.keys(body).sort().map(k=>`${k}=${body[k]}`).join('&');
 return {...body,sign_type:'RSA2',sign:crypto.sign('RSA-SHA256',Buffer.from(content),provider.privateKey).toString('base64')};
}
test('real RSA callbacks bind to stored orders, preserve pending states, and recover after partial failure',async()=>{
 const app=new Koa();app.use(async(ctx,next)=>{try{await next();}catch(e){ctx.status=e.statusCode||500;ctx.body={message:e.message};}});app.use(require('koa-bodyparser')());app.use(require('../dist/router/alipay.router').default.routes());
 const server=app.listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));
 const call=async(user,method,url,body)=>{
  const response=await fetch(`http://127.0.0.1:${server.address().port}${url}`,{method,headers:{...(user?{Authorization:`Bearer ${user}`} :{}),'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
  return {status:response.status,text:await response.text()};
 };
 try {
  assert.equal((await call(0,'GET','/alipay/recharge?coins=100')).status,401);
  process.env.ALIPAY_RECHARGE_ENABLED='false';
  assert.equal((await call(1,'GET','/alipay/recharge?coins=100')).status,503);
  assert.equal(orders.size,0);assert.equal(queryCalls,0);
  process.env.ALIPAY_RECHARGE_ENABLED='true';
  assert.equal((await call(1,'GET','/alipay/recharge?coins=101')).status,400);
  const created=await call(1,'GET','/alipay/recharge?coins=100');assert.equal(created.status,200,created.text);
  const order=JSON.parse(created.text);assert.equal(order.status,'PENDING');assert.equal(order.amountFen,100);assert.match(order.payUrl,/alipay.trade.wap.pay/);
  const notification=signed({out_trade_no:order.requestId});
  for(const body of [ {...notification,total_amount:'5.00'},signed({out_trade_no:order.requestId,total_amount:'1.001'}),signed({out_trade_no:order.requestId,total_amount:'5.00'}),signed({out_trade_no:order.requestId,app_id:'wrong'}),signed({out_trade_no:'missing'}) ]) {
   assert.equal((await call(0,'POST','/alipay/notify',body)).text,'fail');assert.equal(credits.size,0);
  }
  assert.equal((await call(2,'GET',`/alipay/orders/${order.requestId}`)).status,404);
  assert.equal((await call(1,'GET','/alipay/success')).status,200);assert.equal(credits.size,0);
  assert.equal((await call(0,'POST','/alipay/notify',signed({out_trade_no:order.requestId,trade_status:'WAIT_BUYER_PAY'}))).text,'success');assert.equal(credits.size,0);
  failLedger=true;assert.equal((await call(0,'POST','/alipay/notify',notification)).text,'fail');assert.equal(orders.get(order.requestId).status,'PENDING');failLedger=false;
  failUpdate=true;assert.equal((await call(0,'POST','/alipay/notify',notification)).text,'fail');assert.equal(credits.size,1);failUpdate=false;
  const results=await Promise.all(Array.from({length:10},()=>call(0,'POST','/alipay/notify',notification)));
  assert.ok(results.every(r=>r.text==='success'));assert.equal(credits.size,1);
  assert.equal(JSON.parse((await call(1,'GET',`/alipay/orders/${order.requestId}`)).text).status,'CREDITED');
  assert.equal((await call(0,'POST','/alipay/notify',signed({out_trade_no:order.requestId,trade_no:'99999999'}))).text,'fail');
  const recovery=JSON.parse((await call(1,'GET','/alipay/recharge?coins=100')).text);
  const record=orders.get(recovery.requestId);
  const query=()=>call(1,'GET',`/alipay/orders/${recovery.requestId}`);
  for(const mode of ['signature','amount','order','missing','pending','outage']) {
   queryMode=mode;record.last_checked_at=null;
   assert.equal(JSON.parse((await query()).text).status,'PENDING',mode);
   assert.equal(credits.size,1,mode);
  }
  const prior=queryCalls;
  await query();assert.equal(queryCalls,prior,'persisted throttle must skip provider request');
  queryMode='success';record.last_checked_at=null;
  failLedger=true;assert.equal(JSON.parse((await query()).text).status,'PENDING');failLedger=false;
  record.last_checked_at=null;failUpdate=true;
  assert.equal(JSON.parse((await query()).text).status,'PENDING');assert.equal(credits.size,2);failUpdate=false;
  record.last_checked_at=null;
  const concurrentStart=queryCalls;
  const recovered=await Promise.all(Array.from({length:10},query));
  assert.equal(queryCalls-concurrentStart,1,'only one provider query may claim a pending order');
  assert.equal(record.status,'CREDITED');assert.equal(credits.size,2);
  assert.ok(recovered.some(r=>JSON.parse(r.text).status==='CREDITED'));
  assert.equal((await call(0,'POST','/alipay/notify',signed({out_trade_no:recovery.requestId,trade_no:'202611029999000000'}))).text,'success');
  assert.equal(credits.size,2,'query plus callback must remain idempotent');
  config.privateKey='';assert.equal((await call(1,'GET','/alipay/recharge?coins=100')).status,503);assert.equal(orders.size,2);
 }finally{await new Promise(r=>server.close(r));}
});
