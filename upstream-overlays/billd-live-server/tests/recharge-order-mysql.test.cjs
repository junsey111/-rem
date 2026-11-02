const assert=require('node:assert/strict');
const {test}=require('node:test');
const fs=require('node:fs');
const path=require('node:path');
const mysql=require('mysql2/promise');
const {Sequelize,Op}=require('sequelize');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
test('MySQL migration, persisted order ownership, and atomic reconciliation claim',{skip:!process.env.MYSQL_TEST_URL},async()=>{
 const base=new URL(process.env.MYSQL_TEST_URL);
 const admin=await mysql.createConnection({host:base.hostname,port:Number(base.port||3306),user:decodeURIComponent(base.username),password:decodeURIComponent(base.password),multipleStatements:true});
 const name=`live_test_orders_${Date.now()}`;
 let db;
 try{
  await admin.query(`CREATE DATABASE \`${name}\``);
  await admin.query(`USE \`${name}\``);
  await admin.query(fs.readFileSync(path.resolve(__dirname,'../migrations/20261102_recharge_orders.sql'),'utf8'));
  await admin.query(fs.readFileSync(path.resolve(__dirname,'../migrations/20261102_recharge_reconciliation.sql'),'utf8'));
  db=new Sequelize(name,decodeURIComponent(base.username),decodeURIComponent(base.password),{host:base.hostname,port:Number(base.port||3306),dialect:'mysql',logging:false});
  const id=require.resolve('@/config/mysql');require.cache[id]={id,filename:id,loaded:true,exports:{__esModule:true,default:db}};
  const model=require('../dist/model/rechargeOrder.model').default;
  await model.create({request_id:'isolated-recovery',user_id:1,coins:100,amount_fen:100,status:'PENDING'});
  assert.equal(await model.findOne({where:{request_id:'isolated-recovery',user_id:2}}),null);
  const claim=()=>model.update({last_checked_at:new Date()},{where:{request_id:'isolated-recovery',status:'PENDING',[Op.or]:[{last_checked_at:null},{last_checked_at:{[Op.lt]:new Date(Date.now()-30000)}}]}});
  const results=await Promise.all(Array.from({length:20},claim));
  assert.equal(results.reduce((n,[count])=>n+count,0),1);
  await db.close();db=null;
  // A fresh connection proves persisted state rather than a process-local mutex.
  const [rows]=await admin.query('SELECT * FROM recharge_order WHERE request_id=?',['isolated-recovery']);
  assert.equal(rows[0].status,'PENDING');assert.ok(rows[0].last_checked_at);assert.equal(rows[0].amount_fen,100);
  const [update]=await admin.query("UPDATE recharge_order SET last_checked_at=UTC_TIMESTAMP() WHERE request_id=? AND status='PENDING' AND (last_checked_at IS NULL OR last_checked_at < UTC_TIMESTAMP()-INTERVAL 30 SECOND)",['isolated-recovery']);
  assert.equal(update.affectedRows,0);
 }finally{
  if(db)await db.close();
  await admin.query(`DROP DATABASE IF EXISTS \`${name}\``);await admin.end();
 }
});
