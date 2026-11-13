// Only run against start-admin-local.cjs. No provider calls, payments or ledger credits.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const config = JSON.parse(fs.readFileSync(path.join(__dirname, '../.local/admin-local.json')));
const base = 'http://127.0.0.1:24300';

(async () => {
  const call = async (route, token, method = 'GET', body, status = 200) => {
    const response = await fetch(base + route, { method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(15000) });
    const result = await response.json();
    assert.equal(response.status, status, `${method} ${route}: ${response.status}`);
    return result.data;
  };
  assert.deepEqual(await call('/user/auth-methods'), { password: true, sms: false });
  await call('/user/sms/send-code', null, 'POST', { phone: '13900000000' }, 503);
  const token = await call('/user/login', null, 'POST', { id: config.id, password: config.password });
  assert.equal((await call('/operations/access', token)).can_manage, true);
  const routes = ['/operations/summary', '/operations/users', '/operations/tasks', '/operations/moments',
    '/operations/announcements', '/operations/guilds', '/operations/nobles', '/operations/costumes', '/operations/comments',
    '/operations/qualification', '/operations/integrations', '/operations/finance/settings',
    '/operations/finance/withdrawals', '/operations/finance/refunds', '/operations/referrals/settings',
    '/operations/gifts', '/qualification', '/moments', '/user/announcements', '/user/notifications',
    '/guilds', '/guilds/me', '/nobles/levels', '/nobles/me', '/costumes/catalog', '/costumes/me',
    '/moments/tasks/today'];
  for (const route of routes) { await call(route, token); console.log('PASS GET ' + route); }
  const password = crypto.randomBytes(18).toString('base64url');
  const registered = await call('/user/create', null, 'POST', {
    username: 'local-check-' + crypto.randomUUID().slice(0, 8), password });
  const ordinary = await call('/user/login', null, 'POST', { id: registered.id, password });
  assert.equal((await call('/operations/access', ordinary)).can_manage, false);
  await call('/operations/summary', ordinary, 'GET', undefined, 403);
  await call('/operations/tasks', ordinary, 'GET', undefined, 403);
  const moment = await call('/moments', ordinary, 'POST', { content: '本地审核链路验证' });
  assert.equal(moment.review_status, 'PENDING');
  const pendingFeed = await call('/moments', token);
  assert.ok(!pendingFeed.moments.some(row => String(row.id) === moment.id));
  await call('/operations/moments/' + moment.id, token, 'POST', { action: 'APPROVED' });
  const publicFeed = await call('/moments', token);
  assert.ok(publicFeed.moments.some(row => String(row.id) === moment.id));
  const comment = (await call('/moments/' + moment.id + '/comments', ordinary, 'POST', { content: '本地评论审核验证' })).comment;
  assert.equal(comment.review_status, 'PENDING');
  await call('/operations/comments', ordinary, 'GET', undefined, 403);
  assert.ok(!(await call('/moments/' + moment.id + '/comments', token)).comments.some(row => row.id === comment.id));
  await call('/operations/comments/' + comment.id, token, 'POST', { action: 'APPROVED' });
  assert.ok((await call('/moments/' + moment.id + '/comments', token)).comments.some(row => row.id === comment.id));
  await call('/operations/comments/' + comment.id, token, 'POST', { action: 'HIDE', reason: '本地审核验证' });
  assert.ok(!(await call('/moments/' + moment.id + '/comments', token)).comments.some(row => row.id === comment.id));
  assert.equal((await call('/moments/' + moment.id + '/comments', ordinary)).comments.find(row => row.id === comment.id).review_status, 'REJECTED');
  await call('/operations/comments/' + comment.id, token, 'POST', { action: 'RESTORE' });
  await call('/moments/' + moment.id + '/comments/' + comment.id, ordinary, 'DELETE');
  await call('/operations/comments/' + comment.id, token, 'POST', { action: 'RESTORE' }, 409);
  await call('/moments/' + moment.id, ordinary, 'DELETE');
  await call('/user/logout', ordinary, 'POST', {});
  await call('/user/me', ordinary, 'GET', undefined, 401);
  await call('/user/logout', token, 'POST', {});
  console.log('PASS ordinary registration, admin isolation, moment/comment moderation, owned deletion and logout revocation');
  console.log('Local smoke checks passed. Providers and financial activity were not enabled.');
})().catch(error => { console.error(error.message); process.exitCode = 1; });
