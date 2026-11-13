// Read-only smoke checks against the already-running local app. Reuses the
// existing test session without changing the account, relationships or balance.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const root = path.resolve(__dirname, '..');
const dependency = createRequire(path.join(root, 'third_party/billd-live-server/package.json'));

(async () => {
  const config = JSON.parse(fs.readFileSync(path.join(root, '.local/auth-local.json'), 'utf8'));
  const db = await dependency('mysql2/promise').createConnection({
    host: '127.0.0.1', port: 23306, user: 'live_auth_local', password: config.mysqlPassword, database: 'live_auth_local',
  });
  let token;
  try {
    const [rows] = await db.query('SELECT token FROM user WHERE username=? AND deleted_at IS NULL', ['zhang']);
    token = rows[0]?.token;
  } finally { await db.end(); }
  assert.ok(token, 'The local test account must already be signed in');
  let checked = 0;
  async function api(route) {
    const response = await fetch(`http://127.0.0.1:4300${route}`, {
      headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(12000),
    });
    assert.equal(response.status, 200, route);
    const value = await response.json();
    assert.equal(response.status, 200, route);
    assert.equal(value.code, 200, route);
    assert.ok(value.data && typeof value.data === 'object', route);
    checked++; console.log(`PASS ${route}`);
    return value.data;
  }
  for (const route of [
    '/user/me', '/user/relation-counts', '/user/relations?type=following', '/user/relations?type=followers',
    '/user/recommendations', '/user/blocks', '/user/notification-preferences', '/user/visitors', '/user/email/binding',
    '/platform-content/announcements', '/platform-content/secretary', '/platform-content/noble', '/platform-content/secretary/follow',
    '/live/list', '/live/cover/me', '/verification/me',
    '/revenue/wallet', '/revenue/level', '/revenue/profile', '/revenue/gift-orders', '/revenue/recharges',
    '/revenue/finance-rules', '/revenue/withdrawals', '/revenue/refunds', '/revenue/referrals/me', '/revenue/referrals/rewards',
    '/revenue/dashboard', '/revenue/sessions', '/revenue/supporters', '/revenue/creator-applications', '/revenue/match-invitations',
    '/moments/interactions?limit=30', '/livekit/call-price', '/livekit/calls/incoming',
  ]) await api(route);
  const balance = await api('/revenue/withdrawable');
  assert.equal(balance.available_diamonds, balance.earned_diamonds - balance.reserved_diamonds);
  const catalog = await api('/revenue/gifts');
  assert.equal(new Set(catalog.gifts.map(gift => gift.gift_id || gift.id)).size, catalog.gifts.length, 'Gift IDs must be unique');
  for (const scope of ['all', 'following', 'mine', 'mine_post', 'mine_video', 'liked_post', 'liked_video', 'favorite_video', 'video']) {
    const data = await api(`/moments?scope=${scope}`);
    assert.ok(Array.isArray(data.moments));
    if (scope === 'all' && data.moments.length) {
      const post = data.moments[0];
      await api(`/moments/${post.id}/comments`);
      await api(`/moments?scope=user&user_id=${post.user_id}`);
    }
  }
  for (const source of ['', 'LIVE_GIFT', 'PRIVATE_GIFT', 'PAID_VIDEO', 'VIDEO_CALL', 'LIVE_WATCH']) {
    await api(`/revenue/income?source=${source}`);
  }
  for (const period of ['daily', 'weekly', 'monthly']) {
    await api(`/revenue/earnings?period=${period}`);
    await api(`/revenue/rankings/creators?period=${period}`);
    await api(`/revenue/rankings/contributors?period=${period}`);
  }
  console.log(`Checked ${checked} app reads; 0 failures. No content, messages, calls or payments created.`);
})().catch(error => { console.error(error.message); process.exitCode = 1; });
