const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { describe, test } = require('node:test');
const path = require('node:path');
const jwt = require('jsonwebtoken');

require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
const {
  DM_CALL_TTL_MS,
  endDmCall,
  joinDmCall,
  managedMediaRoom,
  openDmCall,
  resetDmCalls,
} = require('../dist/service/dm-call');

const secret = 'fixture-secret-should-not-leak';
const login = 'login-token-should-not-leak';
const base = {
  loginToken: login,
  url: 'ws://10.0.2.2:7880',
  apiKey: 'fixture-key',
  apiSecret: secret,
  now: 1_700_000_000_000,
};

function peer(id, disabled = false) {
  return { id: String(id), disabled };
}

async function open(caller, other, mode = 'video', extra = {}) {
  return openDmCall({ ...base, callerId: String(caller), peer: peer(other), mode, ...extra });
}

describe('dm call grants', { concurrency: 1 }, () => {
test('both participants can publish; voice and video grants differ; live rooms stay untouched', async () => {
  resetDmCalls();
  const video = await open(1, 2, 'video');
  assert.equal(video.caller_id, '1');
  assert.equal(video.peer_id, '2');
  assert.equal(video.mode, 'video');
  assert.equal(video.expires_in, 600);
  assert.equal(video.url, base.url);
  assert.equal(video.signal_transport, 'openim-c2c-text');
  assert.equal(video.signal_text, `dm-call:v1 call_id=${video.call_id} mode=video from=1`);
  assert.equal(managedMediaRoom.test(video.room), false);
  assert.equal(video.room, `dm-${video.call_id}`);
  assert.equal(managedMediaRoom.test('11'), true);
  assert.equal(managedMediaRoom.test('pk-991'), true);
  const caller = jwt.verify(video.token, secret, { issuer: 'fixture-key', algorithms: ['HS256'] });
  assert.equal(caller.sub, '1');
  assert.equal(caller.video.room, video.room);
  assert.equal(caller.video.roomJoin, true);
  assert.equal(caller.video.canPublish, true);
  assert.deepEqual(caller.video.canPublishSources, ['microphone', 'camera']);
  assert.equal(caller.video.canSubscribe, true);
  assert.equal(caller.video.canPublishData, false);
  assert.equal(caller.video.canUpdateOwnMetadata, false);
  assert.equal(JSON.parse(caller.metadata).session_hash, crypto.createHash('sha256').update(login).digest('hex'));
  assert.equal(video.token.includes(secret), false);
  assert.equal(video.token.includes(login), false);
  const joined = await joinDmCall({ ...base, userId: '2', callId: video.call_id });
  const callee = jwt.verify(joined.token, secret, { issuer: 'fixture-key', algorithms: ['HS256'] });
  assert.equal(callee.sub, '2');
  assert.equal(callee.video.canPublish, true);
  assert.equal(callee.video.room, video.room);
  await assert.rejects(joinDmCall({ ...base, userId: '9', callId: video.call_id }), (error) => error.status === 403);

  resetDmCalls();
  const voice = await open(4, 5, 'voice');
  const voiceGrant = jwt.verify(voice.token, secret, { issuer: 'fixture-key', algorithms: ['HS256'] });
  assert.deepEqual(voiceGrant.video.canPublishSources, ['microphone']);
  assert.equal(voice.signal_text.endsWith('mode=voice from=4'), true);
});

test('same pair reuses the open call; a different pair or mode is refused until hangup', async () => {
  resetDmCalls();
  const [first, second] = await Promise.all([open(1, 2), open(2, 1)]);
  assert.equal(first.call_id, second.call_id);
  assert.equal(second.token.length > 20, true);
  await assert.rejects(open(1, 2, 'voice'), (error) => error.status === 409);
  await assert.rejects(open(1, 8), (error) => error.status === 409);
  await endDmCall({ userId: '2', callId: first.call_id, now: base.now });
  await assert.rejects(joinDmCall({ ...base, userId: '1', callId: first.call_id }), (error) => error.status === 404 && error.message === '通话已结束');
  const again = await endDmCall({ userId: '1', callId: first.call_id, now: base.now });
  assert.equal(again.status, 'ended');
  const next = await open(1, 8, 'voice');
  assert.notEqual(next.call_id, first.call_id);
  await assert.rejects(endDmCall({ userId: '9', callId: next.call_id, now: base.now }), (error) => error.status === 403);
});

test('expired, unknown, self, disabled and missing peers do not receive a token', async () => {
  resetDmCalls();
  const created = await open(1, 2);
  await assert.rejects(
    joinDmCall({ ...base, userId: '1', callId: created.call_id, now: base.now + DM_CALL_TTL_MS }),
    (error) => error.status === 404,
  );
  const ended = await endDmCall({ userId: '1', callId: created.call_id, now: base.now + DM_CALL_TTL_MS });
  assert.equal(ended.status, 'ended');
  await assert.rejects(joinDmCall({ ...base, userId: '1', callId: 'ab'.repeat(16) }), (error) => error.status === 404);
  await assert.rejects(open(1, 1), (error) => error.status === 400);
  await assert.rejects(openDmCall({ ...base, callerId: '1', peer: null, mode: 'video' }), (error) => error.status === 404);
  await assert.rejects(openDmCall({ ...base, callerId: '1', peer: peer(2, true), mode: 'video' }), (error) => error.status === 403);
  await assert.rejects(open(1, 2, 'screen'), (error) => error.status === 400);
  await assert.rejects(openDmCall({ ...base, callerId: '1', peer: peer(2), mode: 'video', apiSecret: '' }), (error) => error.status === 503);
  await assert.rejects(joinDmCall({ ...base, userId: '1', callId: 'nope' }), (error) => error.status === 400);
});
});
