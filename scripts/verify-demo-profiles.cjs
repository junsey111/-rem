const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const dependency = createRequire(path.resolve(__dirname, '../third_party/billd-live-server/package.json'));
(async () => {
  const config = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../.local/auth-local.json'), 'utf8'));
  const db = await dependency('mysql2/promise').createConnection({ host:'127.0.0.1', port:23306, user:'live_auth_local', password:config.mysqlPassword, database:'live_auth_local' });
  let token;
  try { const [rows] = await db.query('SELECT token FROM user WHERE username=? AND deleted_at IS NULL', ['zhang']); token = rows[0]?.token; }
  finally { await db.end(); }
  assert.ok(token, 'test account must already be logged in');
  async function api(route, method='GET', body) {
    const response = await fetch(`http://127.0.0.1:4300${route}`, { method, headers:{Authorization:`Bearer ${token}`, 'Content-Type':'application/json'}, ...(body ? {body:JSON.stringify(body)} : {}) });
    const value = await response.json(); assert.equal(response.status,200,route); assert.equal(value.code,200,value.message); return value.data;
  }
  for (const id of [651287,648036,647971,641181]) {
    const page = await api('/user/search','POST',{query:String(id)});
    const users = Array.isArray(page) ? page : page.users;
    const user = users.find(row => Number(row.public_id)===id); assert.ok(user,`profile ${id}`);
    const media = await fetch(`http://127.0.0.1:4300${user.avatar}`); assert.equal(media.status,200); assert.ok((await media.arrayBuffer()).byteLength>1000);
    const byId = await api('/user/search','POST',{query:String(user.id)});
    assert.ok((Array.isArray(byId)?byId:byId.users).some(row=>row.id===user.id));
    console.log(`PASS demo profile, avatar and internal-ID lookup: ${user.username} (${id})`);
  }
  if (process.argv.includes('--restore-requested-follow')) {
    const users = await api('/user/search','POST',{query:'648036'});
    const peer = users.users.find(row=>Number(row.public_id)===648036);
    await api('/user/follow','POST',{user_id:peer.id});
    const relations = await api('/user/relations?type=following');
    assert.ok(relations.users.some(row=>row.id===peer.id));
    const fresh = await api('/user/search','POST',{query:String(peer.id)});
    assert.equal(fresh.users.find(row=>row.id===peer.id).is_following,true);
    console.log('PASS requested follow is persisted and visible in following list');
  }
  const counts = await api('/user/relation-counts');
  for (const type of ['following', 'followers']) {
    let cursor = '', total = 0;
    do {
      const page = await api(`/user/relations?type=${type}${cursor ? `&before_id=${cursor}` : ''}`);
      for (const user of page.users) {
        assert.equal(typeof user.is_following, 'boolean');
        assert.equal(typeof user.is_follower, 'boolean');
        assert.equal(type === 'following' ? user.is_following : user.is_follower, true);
      }
      total += page.users.length; cursor = page.next_before_id;
    } while (cursor);
    assert.equal(counts[type], total, `${type} count matches visible list`);
    console.log(`PASS ${type} count (${total}) matches persisted list and relationship state`);
  }
  const unauthenticated = await fetch('http://127.0.0.1:4300/user/relation-counts');
  assert.equal(unauthenticated.status, 401);
  console.log('PASS relation counts require login');
})().catch(error=>{ console.error(error.message); process.exitCode=1; });
