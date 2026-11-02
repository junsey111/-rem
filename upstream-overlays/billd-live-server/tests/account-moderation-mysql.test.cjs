const assert=require('node:assert/strict');
const {test}=require('node:test');
const path=require('node:path');
const mysql=require('mysql2/promise');
const {Sequelize}=require('sequelize');
const {randomUUID}=require('node:crypto');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,value){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports:value};}
test('account disable commits revocation with durable audit; restore never resurrects token; sync converges to latest action',{skip:!process.env.MYSQL_TEST_URL,timeout:30000},async()=>{
 const url=new URL(process.env.MYSQL_TEST_URL),name='live_test_account_moderation_'+Date.now();
 const admin=await mysql.createConnection({host:url.hostname,port:Number(url.port),user:url.username,password:url.password});let db;
 try{
  await admin.query(`CREATE DATABASE \`${name}\``);
  db=new Sequelize(name,url.username,url.password,{host:url.hostname,port:Number(url.port),dialect:'mysql',logging:false});
  stub('@/config/mysql',{__esModule:true,default:db});stub('@/utils',{initTable:()=>{}});
  stub('@/config/secret',{INTERNAL_SERVICE_KEY:'test',REVENUE_API_URL:'http://test'});
  const calls=[];let unavailable=true;
  stub('@/utils/request',{__esModule:true,default:{put:async(_url,body)=>{calls.push(body);if(unavailable)throw Error('service unavailable');return{revision:body.revision,disabled:body.disabled};}}});
  const User=require('../dist/model/user.model').default,Role=require('../dist/model/role.model').default,Link=require('../dist/model/userRole.model').default,Action=require('../dist/model/accountAction.model').default;
  await db.sync();
  await User.bulkCreate([{id:1,username:'test-admin',password:'test',token:'admin-token',status:1},{id:2,username:'test-viewer',password:'test',token:'viewer-token',status:1},{id:3,username:'test-admin2',password:'test',token:'second-token',status:1}]);
  await Role.create({id:1,role_name:'admin',role_value:'SUPER_ADMIN'});await Link.bulkCreate([{user_id:1,role_id:1},{user_id:3,role_id:1}]);
  const {moderateAccount,reconcileAccountModeration}=require('../dist/service/account-moderation');
  const disable={request_id:randomUUID(),reason:'isolated test disable',disabled:true};
  await assert.rejects(()=>moderateAccount(2,'viewer-token',1,disable));
  await assert.rejects(()=>moderateAccount(1,'stale-token',2,disable));
  await assert.rejects(()=>moderateAccount(1,'admin-token',1,disable));
  await assert.rejects(()=>moderateAccount(1,'admin-token',3,disable));
  await admin.query(`USE \`${name}\``);await admin.query("CREATE TRIGGER reject_action BEFORE INSERT ON account_action FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test fail'");
  await assert.rejects(()=>moderateAccount(1,'admin-token',2,disable));assert.equal((await User.findByPk(2)).token,'viewer-token');assert.equal((await User.findByPk(2)).status,1);
  await admin.query('DROP TRIGGER reject_action');
  const first=await moderateAccount(1,'admin-token',2,disable);assert.equal((await User.findByPk(2)).status,2);assert.equal((await User.findByPk(2)).token,null);
  await moderateAccount(1,'admin-token',2,disable);assert.equal(await Action.count(),1);
  await assert.rejects(()=>moderateAccount(1,'admin-token',2,{...disable,disabled:false}));
  await assert.rejects(()=>reconcileAccountModeration());assert.equal((await Action.findByPk(first.get('action_id'))).get('synced'),false);
  const restore={request_id:randomUUID(),reason:'isolated restore',disabled:false};const latest=await moderateAccount(1,'admin-token',2,restore);
  assert.equal((await User.findByPk(2)).status,1);assert.equal((await User.findByPk(2)).token,null);
  unavailable=false;await reconcileAccountModeration();assert.equal(await Action.count({where:{synced:false}}),0);assert.ok(calls.slice(1).every(call=>call.disabled===false&&String(call.revision)===String(latest.get('action_id'))));
 }finally{if(db)await db.close();await admin.query(`DROP DATABASE \`${name}\``);await admin.end();}
});
