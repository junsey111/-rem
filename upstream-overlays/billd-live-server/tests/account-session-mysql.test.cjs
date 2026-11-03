const assert=require('node:assert/strict');
const {test}=require('node:test');
const path=require('node:path');
const mysql=require('mysql2/promise');
const {Sequelize}=require('sequelize');
const Koa=require('koa');
const jwt=require('jsonwebtoken');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}

test('real password route and MySQL enforce disabled accounts, server expiry, unique sessions, truthful identity and atomic login',{
 skip:!process.env.MYSQL_TEST_URL,timeout:30000,
},async()=>{
 const url=new URL(process.env.MYSQL_TEST_URL),name='live_test_auth_'+Date.now(),secret='isolated-account-session-key';
 const admin=await mysql.createConnection({host:url.hostname,port:Number(url.port||3306),user:decodeURIComponent(url.username),password:decodeURIComponent(url.password)});
 let db,server;
 try{
  await admin.query(`CREATE DATABASE \`${name}\``);await admin.query(`USE \`${name}\``);
  db=new Sequelize(name,decodeURIComponent(url.username),decodeURIComponent(url.password),{host:url.hostname,port:Number(url.port||3306),dialect:'mysql',logging:false});
  stub('@/config/mysql',{__esModule:true,default:db});stub('@/config/secret',{JWT_SECRET:secret});stub('@/utils',{initTable:()=>{}});
  const user=require('../dist/model/user.model').default;await user.sync();
  await admin.query('ALTER TABLE user MODIFY COLUMN password VARCHAR(50) NOT NULL');
  await admin.query("INSERT INTO user(id,username,password,status,created_at,updated_at) VALUES(9,'Legacy migration','PreservedLegacy',1,UTC_TIMESTAMP(),UTC_TIMESTAMP())");
  const migration=require('node:fs').readFileSync(path.resolve(__dirname,'../migrations/20261102_password_hash.sql'),'utf8');
  await admin.query(migration);await admin.query(migration);assert.equal((await user.findByPk(9)).password,'PreservedLegacy');
  await user.bulkCreate([{id:1,username:'真实昵称',password:'ExactPassword',status:1,desc:'private profile'},{id:2,username:'Disabled',password:'ExactPassword',status:2,token:'must-stay-unchanged'},{id:3,username:'Deleted',password:'ExactPassword',status:1}]);await user.destroy({where:{id:3}});
  const {verifyPassword,isPasswordHash}=require('../dist/app/auth/password');
  assert.ok(isPasswordHash((await user.findByPk(1)).password));
  assert.notEqual((await user.findByPk(1)).password,(await user.findByPk(2)).password);
  // Emulate the exact old 50-character column; the widening migration preserves legacy values.
  await admin.query("UPDATE user SET password='ExactPassword' WHERE id=1");
  let unavailable=false;
  stub('@/service/user.service',{__esModule:true,default:{findAndToken:async id=>{if(unavailable)throw Error('private SQL failure');return user.findByPk(id,{attributes:{exclude:['password']}});},
   create:props=>user.create(props),find:id=>user.findByPk(id,{attributes:{exclude:['password','token']}}),isSameName:username=>user.findOne({where:{username},attributes:['id']})}});
  for(const file of ['@/service/emailUser.service','@/service/role.service','@/service/thirdUser.service','@/controller/redis.controller'])stub(file,{__esModule:true,default:{}});
  stub('@/controller/log.controller',{__esModule:true,default:{common:{create:async()=>{}}}});
  const app=new Koa();app.on('error',require('../dist/app/handler/error-handle').default);app.use(require('../dist/app/app.middleware').catchErrorMiddle);app.use(require('koa-bodyparser')());
  const router=require('../dist/router/user.router').default;app.use(router.routes());
  server=app.listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));
  const call=async(path,body,token)=>{const r=await fetch(`http://127.0.0.1:${server.address().port}`+path,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,data:await r.json()};};
  const login=(body={})=>call('/user/login',{id:1,password:'ExactPassword',...body});
  for(const body of [{id:{ne:0}},{password:{ne:null}},{id:0},{id:'1 OR 1=1'},{password:''},{password:'exactpassword'},{id:3}])assert.equal((await login(body)).status,400);
  assert.equal((await login({id:2})).status,403);assert.equal((await user.findByPk(2)).token,'must-stay-unchanged');
  assert.equal((await user.findByPk(1)).password,'ExactPassword');
  await admin.query("CREATE TRIGGER fail_legacy_session BEFORE UPDATE ON user FOR EACH ROW BEGIN IF NEW.token <> OLD.token OR (NEW.token IS NOT NULL AND OLD.token IS NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated session write failure'; END IF; END");
  assert.equal((await login()).status,500);assert.equal((await user.findByPk(1)).password,'ExactPassword');await admin.query('DROP TRIGGER fail_legacy_session');
  const first=await login({exp:1e12,roles:['SUPER_ADMIN'],status:1});assert.equal(first.status,200);const firstToken=first.data.data;
  const upgraded=(await user.findByPk(1)).password;assert.ok(isPasswordHash(upgraded));assert.ok(await verifyPassword('ExactPassword',upgraded));
  assert.equal((await login({password:upgraded})).status,400);
  const claims=jwt.verify(firstToken,secret,{algorithms:['HS256']});assert.equal(claims.exp-claims.iat,86400);assert.deepEqual(claims.userInfo,{id:1});assert.ok(claims.jti);assert.equal(claims.password,undefined);
  const profile=await call('/user/me',undefined,firstToken);assert.equal(profile.status,200);assert.deepEqual(profile.data.data,{id:1,username:'真实昵称',status:1});
  const second=await login({exp:0});assert.equal(second.status,200);assert.notEqual(second.data.data,firstToken);assert.equal((await call('/user/me',undefined,firstToken)).status,401);
  const concurrent=await Promise.all(Array.from({length:4},()=>login()));assert.ok(concurrent.every(r=>r.status===200));assert.equal(new Set(concurrent.map(r=>r.data.data)).size,4);
  const current=(await user.findByPk(1)).token;let accepted=0;for(const result of concurrent)if((await call('/user/me',undefined,result.data.data)).status===200)accepted++;assert.equal(accepted,1);
  await user.update({username:'服务端已更新昵称'},{where:{id:1}});assert.equal((await call('/user/me',undefined,current)).data.data.username,'服务端已更新昵称');
  await user.update({status:2},{where:{id:1}});assert.equal((await call('/user/me',undefined,current)).status,401);await user.update({status:1},{where:{id:1}});
  // Locking serializes a concurrently committed disable before password login.
  const tx=await db.transaction();await user.update({status:2},{where:{id:1},transaction:tx});
  const waiting=login();await tx.commit();assert.equal((await waiting).status,403);assert.equal((await user.findByPk(1)).token,current);await user.update({status:1},{where:{id:1}});
  await admin.query("CREATE TRIGGER reject_token BEFORE UPDATE ON user FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private write failure'");
  const failure=await login();assert.equal(failure.status,500);assert.equal(failure.data.data,undefined);assert.ok(!JSON.stringify(failure).includes('private write failure'));assert.equal((await user.findByPk(1)).token,current);await admin.query('DROP TRIGGER reject_token');
  for(const token of [jwt.sign({userInfo:{id:1}},secret),jwt.sign({userInfo:{id:1}},secret,{expiresIn:86401}),jwt.sign({userInfo:{id:true}},secret,{expiresIn:60}),jwt.sign({userInfo:{id:1}},secret,{algorithm:'HS512',expiresIn:60}),jwt.sign({exp:Math.floor(Date.now()/1000)+60},secret),jwt.sign({userInfo:{id:{bad:1}}},secret,{expiresIn:60})]){
   await user.update({token},{where:{id:1}});assert.equal((await call('/user/me',undefined,token)).status,401);
  }
  await user.update({token:current},{where:{id:1}});unavailable=true;
  const outage=await call('/user/me',undefined,current);assert.equal(outage.status,503);assert.ok(!JSON.stringify(outage).includes('private SQL failure'));unavailable=false;
  const {signJwt}=require('../dist/app/auth/authJwt');const other=jwt.decode(signJwt({userInfo:{id:1,password:'must-not-leak',roles:['SUPER_ADMIN']},exp:NaN}));assert.equal(other.exp-other.iat,86400);assert.deepEqual(other.userInfo,{id:1});
  for(const body of [{username:'NoPassword'},{username:{ne:null},password:'abc'},{username:'Empty',password:''}])assert.equal((await call('/user/create',body)).status,400);
  const registered=await call('/user/create',{username:'新注册',password:'NewAccountPass'});assert.equal(registered.status,200);
  assert.ok(!('password'in registered.data.data));assert.ok(!('token'in registered.data.data));
  const newId=registered.data.data.id;assert.ok(isPasswordHash((await user.findByPk(newId)).password));
  assert.equal((await call('/user/login',{id:newId,password:'NewAccountPass'})).status,200);
  assert.equal((await call('/user/password',{oldpwd:'ExactPassword',newpwd:'ChangedPassword'})).status,401);
  assert.equal((await call('/user/password',{oldpwd:'wrong',newpwd:'ChangedPassword'},current)).status,400);
  assert.equal((await call('/user/password',{oldpwd:'ExactPassword',newpwd:{bad:1}},current)).status,400);
  const beforeChange=(await user.findByPk(1)).password;
  await admin.query("CREATE TRIGGER fail_password_change BEFORE UPDATE ON user FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated change failure'");
  assert.equal((await call('/user/password',{oldpwd:'ExactPassword',newpwd:'ChangedPassword'},current)).status,500);
  assert.equal((await user.findByPk(1)).password,beforeChange);assert.equal((await user.findByPk(1)).token,current);await admin.query('DROP TRIGGER fail_password_change');
  assert.equal((await call('/user/password',{oldpwd:'ExactPassword',newpwd:'ChangedPassword'},current)).status,200);
  const changed=await user.findByPk(1);assert.ok(isPasswordHash(changed.password));assert.equal(changed.token,null);
  assert.equal((await call('/user/me',undefined,current)).status,401);assert.equal((await login()).status,400);
  assert.equal((await login({password:'ChangedPassword'})).status,200);
  await changed.update({password:'InstancePassword'});assert.ok(await verifyPassword('InstancePassword',(await user.findByPk(1)).password));assert.equal((await user.findByPk(1)).token,null);
  await user.update({password:'BulkPassword'},{where:{id:1}});assert.ok(await verifyPassword('BulkPassword',(await user.findByPk(1)).password));
  await user.update({password:'IndividualPassword'},{where:{id:1},individualHooks:true});assert.ok(await verifyPassword('IndividualPassword',(await user.findByPk(1)).password));
  const individually=await user.bulkCreate([{username:'IndividualNew',password:'IndividualCreate'}],{individualHooks:true});assert.ok(await verifyPassword('IndividualCreate',individually[0].password));
  const legacyToken=signJwt({userInfo:{id:9},exp:24});await user.update({token:legacyToken},{where:{id:9}});
  assert.equal((await call('/user/password',{oldpwd:'PreservedLegacy',newpwd:'PreservedLegacy'},legacyToken)).status,200);
  assert.ok(isPasswordHash((await user.findByPk(9)).password));assert.equal((await user.findByPk(9)).token,null);
  assert.equal(await verifyPassword('x','$scrypt$v1$999999999$8$3$bad$bad'),false);
  await user.destroy({where:{id:1}});assert.equal((await call('/user/me',undefined,current)).status,401);
 }finally{if(server)await new Promise(r=>{server.close(r);server.closeAllConnections?.();});if(db)await db.close();await admin.query(`DROP DATABASE IF EXISTS \`${name}\``);await admin.end();}
});
