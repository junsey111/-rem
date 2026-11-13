const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { describe, test } = require('node:test');
const path = require('node:path');
const jwt = require('jsonwebtoken');

require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
const {
  DM_CALL_TTL_MS,
  endDmCall,
  confirmDmCall,
  joinDmCall,
  managedMediaRoom,
  openDmCall,
  resetDmCalls,
  readDmCall,
  incomingDmCall,
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
test('incoming calls, rejection, timeout and connected duration are participant scoped', async () => {
  resetDmCalls();
  const invite = await open(1,2,'voice');
  assert.equal(incomingDmCall('2',base.now).call_id,invite.call_id);
  assert.equal(incomingDmCall('1',base.now),null);
  assert.throws(()=>readDmCall({userId:'3',callId:invite.call_id,now:base.now}),e=>e.status===403);
  await endDmCall({userId:'2',callId:invite.call_id,reason:'rejected',now:base.now+1000});
  assert.equal(readDmCall({userId:'1',callId:invite.call_id,now:base.now+2000}).status,'rejected');
  resetDmCalls();
  const unanswered = await open(1,2,'voice');
  assert.equal(readDmCall({userId:'1',callId:unanswered.call_id,now:base.now+60001}).status,'no_answer');
  assert.equal(incomingDmCall('2',base.now+60001),null);
  const connected = await open(1,2,'voice',{now:base.now+61000});
  await confirmDmCall({userId:'2',callId:connected.call_id,participants:async()=>['1','2'],settle:async()=>{},now:base.now+62000});
  await endDmCall({userId:'1',callId:connected.call_id,now:base.now+67000});
  const receipt=readDmCall({userId:'2',callId:connected.call_id,now:base.now+70000});
  assert.equal(receipt.connected,true);assert.equal(receipt.duration_seconds,5);assert.equal(receipt.status,'ended');
});
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

test('paid call settlement requires both participants in the media room', async () => {
  resetDmCalls();
  const created = await open(1, 2, 'video', { priceCoins: 25, acceptedPriceCoins: 25 });
  const joined = await joinDmCall({ ...base, userId: '2', callId: created.call_id });
  assert.equal(joined.price_coins, 25);
  let charges = 0;
  const settle = async () => { charges += 1; };
  await assert.rejects(confirmDmCall({ userId: '2', callId: created.call_id, now: base.now,
    participants: async () => ['2'], settle }), error => error.status === 409);
  assert.equal(charges, 0);
  const participants = async () => ['1', '2'];
  const confirmed = await confirmDmCall({ userId: '2', callId: created.call_id, participants, settle, now: base.now });
  assert.equal(confirmed.charged_coins, 25);
  await assert.rejects(confirmDmCall({ userId: '9', callId: created.call_id, participants, settle, now: base.now }), error => error.status === 403);
  await endDmCall({ userId: '1', callId: created.call_id });
  await assert.rejects(confirmDmCall({ userId: '2', callId: created.call_id, participants, settle, now: base.now }), error => error.status === 404);
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
test('a host offer requires explicit payer consent; changed offers cannot reuse a grant; settlement runs once', async () => {
  resetDmCalls();
  const offer = await open(1, 2, 'video', { priceCoins: 60, priceOwnerId: '1', acceptedPriceCoins: 60 });
  assert.equal(offer.payer_id, '2');
  assert.equal(offer.price_owner_id, '1');
  await assert.rejects(joinDmCall({ ...base, userId: '2', callId: offer.call_id }), e => e.status === 409);
  await assert.rejects(joinDmCall({ ...base, userId: '2', callId: offer.call_id, acceptedPriceCoins: 59 }), e => e.status === 409);
  await assert.rejects(open(2, 1, 'video', { acceptedPriceCoins: 0 }), e => e.status === 409);
  await assert.rejects(open(1, 2, 'video', { priceCoins: 90, priceOwnerId: '1', acceptedPriceCoins: 90 }), e => e.status === 409);
  let charges = 0;
  const settlement = {userId: '1', callId: offer.call_id, now: base.now + 1000,
    participants: async () => ['1','2'], settle: async (id, payer, owner, coins) => {
      assert.equal(id, offer.call_id); assert.equal(payer, '2'); assert.equal(owner, '1'); assert.equal(coins, 60); charges++;
    }};
  await assert.rejects(confirmDmCall(settlement), e => e.status === 409);
  assert.equal(charges, 0);
  const joined = await joinDmCall({ ...base, userId: '2', callId: offer.call_id, acceptedPriceCoins: 60 });
  assert.equal(joined.price_coins, 60);
  await Promise.all([confirmDmCall(settlement), confirmDmCall(settlement)]);
  assert.equal(charges, 1);
});
});
