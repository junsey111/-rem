const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const root = path.resolve(__dirname, '..');
const dependency = createRequire(path.join(root, 'third_party/billd-live-server/package.json'));

(async () => {
  const config = JSON.parse(fs.readFileSync(path.join(root, '.local/auth-local.json'), 'utf8'));
  const db = await dependency('mysql2/promise').createConnection({ host: '127.0.0.1', port: 23306, user: 'live_auth_local', password: config.mysqlPassword, database: 'live_auth_local' });
  let token;
  try { const [rows] = await db.query('SELECT token FROM user WHERE id=?', [config.adminId]); token = rows[0]?.token; }
  finally { await db.end(); }
  let access = await fetch('http://localhost:8000/backend/operations/access', { headers: { Authorization: `Bearer ${token || ''}` } });
  if (access.status === 401) {
    const response = await fetch('http://localhost:8000/backend/user/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: config.adminUsername, password: config.adminPassword }) });
    const login = await response.json(); if (login.code !== 200) throw new Error('本地管理员登录验证失败'); token = login.data;
  }
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' });
  const dateQuery = `start=${today}&end=${today}`;
  const paths = ['access', 'summary', `dashboard?${dateQuery}`, 'users', 'lives', 'content/announcements', 'content/secretary', 'content/noble', 'moments', `insights/creators?${dateQuery}`, 'insights/transactions', 'finance/recharges', 'finance/manual-recharges', 'gifts', 'referrals/settings', 'levels/settings', 'business-actions', 'admission', 'creators', 'reports', 'rooms/1/messages', 'rooms/1/chat-actions', 'finance/withdrawals', 'finance/refunds', 'finance/settings', 'qr/support', 'actions', 'account-actions', 'finance/audit'];
  let failures = 0;
  for (const endpoint of paths) {
    const response = await fetch(`http://localhost:8000/backend/operations/${endpoint}`, { headers: { Authorization: `Bearer ${token}` } });
    const result = await response.json().catch(() => null);
    const ok = response.status === 200 && result?.code === 200;
    if (!ok) failures++;
    console.log(`${ok ? 'PASS' : 'FAIL'} ${response.status} /operations/${endpoint}${ok ? '' : ' ' + String(result?.message || '').slice(0, 100)}`);
  }
  console.log(`Checked ${paths.length} admin reads; ${failures} failures.`);
  if (failures) process.exitCode = 1;
})().catch(error => { console.error(error.message); process.exitCode = 1; });
