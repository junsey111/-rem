// Local-only operational UI runtime. It never connects to the VM or seeds financial activity.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { createRequire } = require('node:module');
const { spawn, execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const backend = path.join(root, 'third_party/billd-live-server');
const revenue = path.join(root, 'third_party/live-revenue-integrity-lab');
const dependency = createRequire(path.join(backend, 'package.json'));
const localDir = path.join(root, '.local');
fs.mkdirSync(localDir, { recursive: true });
fs.mkdirSync(path.join(root, '.tools'), { recursive: true });
const credentialsFile = path.join(localDir, 'admin-local.json');
const config = fs.existsSync(credentialsFile) ? JSON.parse(fs.readFileSync(credentialsFile)) : {
  username: '本地运营管理员', password: crypto.randomBytes(18).toString('base64url'),
  jwtSecret: crypto.randomBytes(32).toString('hex'), internalKey: crypto.randomBytes(32).toString('hex'),
};
config.mysqlPassword ||= crypto.randomBytes(24).toString('hex');
fs.writeFileSync(credentialsFile,JSON.stringify(config,null,2));
Object.assign(process.env, {
  NODE_ENV:'production', NODE_APP_RELEASE_PROJECT_ENV:'prod', NODE_APP_RELEASE_PROJECT_NAME:'live-ops-local',
  MYSQL_HOST:'127.0.0.1', MYSQL_PORT:'23306', MYSQL_USER:'live_ops_local', MYSQL_PASSWORD:config.mysqlPassword,
  MYSQL_DATABASE:'live_ops_local_billd', JWT_SECRET:config.jwtSecret, INTERNAL_SERVICE_KEY:config.internalKey,
  PAYMENT_WEBHOOK_SECRET:'', REVENUE_API_URL:'http://127.0.0.1:24808',
  ALIPAY_DISBURSEMENTS_ENABLED:'false', ALIPAY_RECHARGE_ENABLED:'false', IDENTITY_ENABLED:'false',
});
dependency('module-alias').addAlias('@', path.join(backend, 'dist'));
const children = [];
let server, db;
async function stop() {
  for (const child of children) child.kill();
  if (server) server.close();
  if (db) await db.close();
}
process.once('SIGINT',()=>{stop().finally(()=>process.exit());});
process.once('SIGTERM',()=>{stop().finally(()=>process.exit());});
(async()=>{
  const admin=await dependency('mysql2/promise').createConnection({host:'127.0.0.1',port:23306,user:'root'});
  try {
    for(const name of ['live_ops_local_billd','live_ops_local_revenue']) await admin.query(`CREATE DATABASE IF NOT EXISTS \`${name}\``);
    await admin.query("CREATE USER IF NOT EXISTS 'live_ops_local'@'localhost' IDENTIFIED BY ?",[config.mysqlPassword]);
    await admin.query("ALTER USER 'live_ops_local'@'localhost' IDENTIFIED BY ?",[config.mysqlPassword]);
    await admin.query("GRANT ALL ON live_ops_local_billd.* TO 'live_ops_local'@'localhost'");
    await admin.query("GRANT ALL ON live_ops_local_revenue.* TO 'live_ops_local'@'localhost'");
  }finally{await admin.end();}
  const env={...process.env,MYSQL_DSN:`live_ops_local:${config.mysqlPassword}@tcp(127.0.0.1:23306)/live_ops_local_revenue?parseTime=true&multiStatements=true`,MIGRATIONS_DIR:path.join(revenue,'migrations'),HTTP_ADDR:'127.0.0.1:24808',BILLD_ACCOUNT_URL:'http://127.0.0.1:24300/user/me'};
  const apiBin=path.join(root,'.tools','admin-local-revenue.exe');
  const migrateBin=path.join(root,'.tools','admin-local-migrate.exe');
  for(const [binary,source] of [[apiBin,'./cmd/api'],[migrateBin,'./cmd/migrate']]) execFileSync('go',['build','-o',binary,source],{cwd:revenue,env,stdio:'inherit'});
  execFileSync(migrateBin,[],{cwd:revenue,env,stdio:'inherit'});
  db=dependency('@/config/mysql').default;
  const modelDir=path.join(backend,'dist/model');
  for(const file of fs.readdirSync(modelDir).filter(file=>file.endsWith('.model.js'))) require(path.join(modelDir,file));
  dependency('@/model/relation');
  const tables=await db.getQueryInterface().showAllTables();
  if(tables.includes('user')){
    const columns=await db.getQueryInterface().describeTable('user');
    if(!columns.public_id) await db.query('ALTER TABLE `user` ADD COLUMN `public_id` INT UNSIGNED NULL, ADD UNIQUE KEY `uq_user_public_id` (`public_id`)');
  }
  await db.sync(); // Fixed local database only; never alter or reset existing tables.
  const User=dependency('@/model/user.model').default, Role=dependency('@/model/role.model').default, UserRole=dependency('@/model/userRole.model').default;
  let user=await User.findOne({where:{username:config.username}});
  if(!user) user=await User.create({username:config.username,password:config.password,status:1});
  const [role]=await Role.findOrCreate({where:{role_value:'SUPER_ADMIN'},defaults:{role_name:'本地运营管理员',type:2}});
  await UserRole.findOrCreate({where:{user_id:user.id,role_id:role.id}});
  config.id=Number(user.id);config.url='http://localhost:8000/';
  fs.writeFileSync(credentialsFile,JSON.stringify(config,null,2));
  const Koa=dependency('koa');const app=new Koa();
  app.on('error',dependency('@/app/handler/error-handle').default);
  app.use(dependency('@/app/app.middleware').catchErrorMiddle);
  app.use(dependency('koa-bodyparser')({jsonLimit:'32kb'}));
  app.use(dependency('@/app/verify.middleware').apiBeforeVerify);
  for(const file of ['user','revenue','operations','business-settings','finance','account-moderation','room-chat','cohost','cover','live','provider','identity']){
    const router=dependency(`@/router/${file}.router`).default;app.use(router.routes()).use(router.allowedMethods());
  }
  server=app.listen(24300,'127.0.0.1');
  dependency('@/service/account-moderation').startAccountModeration(server);
  const api=spawn(apiBin,[],{cwd:revenue,env,stdio:'inherit',windowsHide:true});children.push(api);
  api.once('exit',code=>{console.error('Local revenue service exited:',code);stop().finally(()=>process.exit(code||1));});
  console.log('Local admin API: http://127.0.0.1:24300');
  console.log('Local credentials (git ignored): '+credentialsFile);
  console.log('Web command: $env:BILLD_API_URL="http://127.0.0.1:24300"; pnpm start');
})().catch(error=>{console.error(error.message);stop().finally(()=>process.exit(1));});
