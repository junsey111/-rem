// Local development entry point for the Android account flow.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { createRequire } = require('node:module');
const { spawn, execFileSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const backend = path.join(root, 'third_party', 'billd-live-server');
const revenue = path.join(root, 'third_party', 'live-revenue-integrity-lab');
const local = path.join(root, '.local');
fs.mkdirSync(local, { recursive: true });
const secretFile = path.join(local, 'auth-local.json');
const config = fs.existsSync(secretFile)
  ? JSON.parse(fs.readFileSync(secretFile, 'utf8'))
  : { jwtSecret: crypto.randomBytes(32).toString('hex') };
config.mysqlPassword ||= crypto.randomBytes(24).toString('hex');
config.internalKey ||= crypto.randomBytes(32).toString('hex');
config.adminUsername ||= '本地运营管理员';
config.adminPassword ||= crypto.randomBytes(18).toString('base64url');
fs.writeFileSync(secretFile, JSON.stringify(config, null, 2));

Object.assign(process.env, {
  NODE_ENV: 'production',
  NODE_APP_RELEASE_PROJECT_ENV: 'prod',
  NODE_APP_RELEASE_PROJECT_NAME: 'live-auth-local',
  MYSQL_HOST: '127.0.0.1',
  MYSQL_PORT: '23306',
  MYSQL_DATABASE: 'live_auth_local',
  MYSQL_USER: 'live_auth_local',
  MYSQL_PASSWORD: config.mysqlPassword,
  JWT_SECRET: config.jwtSecret,
  INTERNAL_SERVICE_KEY: config.internalKey,
  REVENUE_API_URL: 'http://127.0.0.1:24808',
  ALIPAY_DISBURSEMENTS_ENABLED: 'false',
  ALIPAY_RECHARGE_ENABLED: 'false',
  IDENTITY_ENABLED: 'false',
  REFERRALS_ENABLED: 'true',
  REFERRAL_INVITER_COINS: '0',
  REFERRAL_INVITEE_COINS: '0',
  REFERRAL_RECHARGE_BPS: '500',
  REFERRAL_WITHDRAWAL_BPS: '800',
  EMAIL_CODE_LOCAL_STORE: 'true',
  APP_DOWNLOAD_STATS_FILE: path.join(local, 'app-update', 'downloads.json'),
});

const dependency = createRequire(path.join(backend, 'package.json'));
dependency('module-alias').addAlias('@', path.join(backend, 'dist'));

(async () => {
  const admin = await dependency('mysql2/promise').createConnection({
    host: '127.0.0.1', port: 23306, user: 'root',
  });
  await admin.query('CREATE DATABASE IF NOT EXISTS live_auth_local');
  await admin.query('CREATE DATABASE IF NOT EXISTS live_auth_local_revenue');
  await admin.query("CREATE USER IF NOT EXISTS 'live_auth_local'@'localhost' IDENTIFIED BY ?", [config.mysqlPassword]);
  await admin.query("ALTER USER 'live_auth_local'@'localhost' IDENTIFIED BY ?", [config.mysqlPassword]);
  await admin.query("GRANT ALL ON live_auth_local.* TO 'live_auth_local'@'localhost'");
  await admin.query("GRANT ALL ON live_auth_local_revenue.* TO 'live_auth_local'@'localhost'");
  await admin.end();

  const db = dependency('@/config/mysql').default;
  const modelDir = path.join(backend, 'dist', 'model');
  for (const file of fs.readdirSync(modelDir).filter(file => file.endsWith('.model.js'))) {
    require(path.join(modelDir, file));
  }
  dependency('@/model/relation');
  const tables = await db.getQueryInterface().showAllTables();
  if (tables.includes('user')) {
    const columns = await db.getQueryInterface().describeTable('user');
    if (!columns.public_id) await db.query('ALTER TABLE `user` ADD COLUMN `public_id` INT UNSIGNED NULL, ADD UNIQUE KEY `uq_user_public_id` (`public_id`)');
  }
  if (tables.includes('user_moment_comment')) {
    const columns = await db.getQueryInterface().describeTable('user_moment_comment');
    if (!columns.parent_id) await db.query('ALTER TABLE `user_moment_comment` ADD COLUMN `parent_id` BIGINT UNSIGNED NULL AFTER `user_id`');
    if (!columns.reply_to_user_id) await db.query('ALTER TABLE `user_moment_comment` ADD COLUMN `reply_to_user_id` INT UNSIGNED NULL AFTER `parent_id`');
    const [indexes] = await db.query('SHOW INDEX FROM `user_moment_comment` WHERE Key_name = ?', {
      replacements: ['moment_reply_page'],
    });
    if (!indexes.length) await db.query('ALTER TABLE `user_moment_comment` ADD KEY `moment_reply_page` (`moment_id`, `parent_id`, `visible`, `id`)');
  }
  if (tables.includes('user_moment')) {
    const columns = await db.getQueryInterface().describeTable('user_moment');
    if (!columns.hidden) await db.query('ALTER TABLE `user_moment` ADD COLUMN `hidden` BOOLEAN NOT NULL DEFAULT FALSE AFTER `visible`');
  }
  await db.sync();

  const User = dependency('@/model/user.model').default;
  await require('./demo-users.cjs').seedDemoUsers(User, dependency('@/model/moment.model'), dependency('@/model/momentReview.model').default);
  const Role = dependency('@/model/role.model').default;
  const UserRole = dependency('@/model/userRole.model').default;
  let operator = await User.findOne({ where: { username: config.adminUsername } });
  if (!operator) operator = await User.create({ username: config.adminUsername, password: config.adminPassword, status: 1 });
  const [role] = await Role.findOrCreate({ where: { role_value: 'SUPER_ADMIN' }, defaults: { role_name: '本地运营管理员', type: 2 } });
  await UserRole.findOrCreate({ where: { user_id: operator.id, role_id: role.id } });
  config.adminId = Number(operator.id);
  fs.writeFileSync(secretFile, JSON.stringify(config, null, 2));

  const revenueEnv = {
    ...process.env,
    MYSQL_DSN: `live_auth_local:${config.mysqlPassword}@tcp(127.0.0.1:23306)/live_auth_local_revenue?parseTime=true&multiStatements=true`,
    MIGRATIONS_DIR: path.join(revenue, 'migrations'),
    HTTP_ADDR: '127.0.0.1:24808',
    BILLD_ACCOUNT_URL: 'http://127.0.0.1:4300/user/me',
  };
  const toolsDir = path.join(root, '.tools');
  fs.mkdirSync(toolsDir, { recursive: true });
  const apiBin = path.join(toolsDir, 'auth-local-revenue.exe');
  const migrateBin = path.join(toolsDir, 'auth-local-migrate.exe');
  for (const [binary, source] of [[apiBin, './cmd/api'], [migrateBin, './cmd/migrate']])
    execFileSync('go', ['build', '-o', binary, source], { cwd: revenue, env: revenueEnv, stdio: 'inherit' });
  execFileSync(migrateBin, [], { cwd: revenue, env: revenueEnv, stdio: 'inherit' });

  const Koa = dependency('koa');
  const app = new Koa();
  app.use(require('./demo-users.cjs').demoMedia(root));
  app.on('error', dependency('@/app/handler/error-handle').default);
  app.use(dependency('@/app/app.middleware').catchErrorMiddle);
  app.use(dependency('koa-bodyparser')({ jsonLimit: '128kb' }));
  app.use(dependency('@/app/verify.middleware').apiBeforeVerify);
  const userRouter = dependency('@/router/user.router').default;
  const emailRouter = dependency('@/router/email-login.router').default;
  const momentsRouter = dependency('@/router/moments.router').default;
  app.use(userRouter.routes()).use(userRouter.allowedMethods());
  app.use(emailRouter.routes()).use(emailRouter.allowedMethods());
  app.use(momentsRouter.routes()).use(momentsRouter.allowedMethods());
  for (const file of ['live', 'cover', 'operations', 'moment-review', 'finance', 'revenue', 'room-chat', 'platform-content', 'business-settings', 'account-moderation', 'cohost', 'openim', 'livekit']) {
    const router = dependency(`@/router/${file}.router`).default;
    app.use(router.routes()).use(router.allowedMethods());
  }
  app.use(ctx => {
    if (ctx.path === '/health') ctx.body = { status: 'ok' };
  });
  const server = app.listen(4300, '127.0.0.1', () => {
    console.log('Local app API ready at http://127.0.0.1:4300');
    console.log('Local operations credentials: ' + secretFile);
  });
  dependency('@/config/websocket').connectWebSocket(server);
  const revenueApi = spawn(apiBin, [], { cwd: revenue, env: revenueEnv, stdio: 'inherit', windowsHide: true });
  revenueApi.once('exit', (code) => {
    console.error('Local revenue service exited:', code);
    process.exit(code || 1);
  });
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
