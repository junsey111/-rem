const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('fs');
const path = require('path');

const compiled = process.env.DM_CONTRACT_MODULE
  || path.resolve(__dirname, '../dist/service/openim-dm-contract.js');
if (!fs.existsSync(compiled)) {
  test('dm contract module', { skip: 'compile openim-dm-contract.ts or set DM_CONTRACT_MODULE' }, () => {});
} else {
  const contract = require(compiled);
  const request = { requestId: '11111111-1111-4111-8111-111111111111', giftId: 'HEART', quantity: 1 };
  const accepted = {
    accepted: true, request_id: request.requestId, gift_order_id: 'order-1', gift_id: 'HEART', gift_name: '小心心',
    quantity: 1, charged_coins: 5, new_balance: 95, status: 'ACCEPTED', idempotency: 'created',
  };

  test('dm gift uses the revenue receipt and ignores a client price', () => {
    const result = contract.interpretDmGift({ kind: 'ok', body: { ...accepted, client_price: 1 } }, request, '别用目录名');
    assert.equal(result.outcome, 'accepted');
    assert.equal(result.chargedCoins, 5);
    assert.deepEqual(Object.keys(result.imCustom).sort(), ['charged_coins', 'gift_id', 'gift_name', 'gift_order_id', 'kind', 'quantity', 'request_id', 'status', 'v']);
    assert.equal(result.imCustom.gift_name, '小心心');
    assert.equal(JSON.stringify(result.imCustom).includes('media'), false);
  });

  test('dm gift falls back to the catalog name only when the receipt omits one', () => {
    const body = { ...accepted };
    delete body.gift_name;
    const result = contract.interpretDmGift({ kind: 'ok', body }, request, '小心心');
    assert.equal(result.imCustom.gift_name, '小心心');
    assert.equal(contract.interpretDmGift({ kind: 'ok', body }, request).outcome, 'retry');
  });

  test('dm gift does not emit a message when settlement is missing or refused', () => {
    const missing = contract.interpretDmGift({ kind: 'http', status: 404, body: '404 page not found' }, request);
    assert.equal(missing.outcome, 'unavailable');
    assert.equal(missing.code, 'DM_GIFT_SETTLEMENT_UNAVAILABLE');
    const refused = contract.interpretDmGift({ kind: 'http', status: 409, body: { code: 'INSUFFICIENT_BALANCE' } }, request);
    assert.equal(refused.outcome, 'rejected');
    assert.equal(contract.interpretDmGift({ kind: 'network' }, request).outcome, 'retry');
    assert.equal(contract.interpretDmGift({ kind: 'ok', body: { ...accepted, charged_coins: '5' } }, request).outcome, 'retry');
    assert.equal(contract.interpretDmGift({ kind: 'ok', body: { ...accepted, gift_id: 'OTHER' } }, request).outcome, 'retry');
    assert.equal(contract.interpretDmGift({ kind: 'http', status: 404, body: { code: 'GIFT_NOT_FOUND' } }, request).outcome, 'rejected');
  });

  test('dm gift read distinguishes a missing route from a missing order', () => {
    assert.equal(contract.interpretDmGiftRead({ kind: 'http', status: 404, body: 'no route' }, request.requestId).outcome, 'unavailable');
    assert.equal(contract.interpretDmGiftRead({ kind: 'http', status: 404, body: { code: 'GIFT_NOT_FOUND' } }, request.requestId).outcome, 'missing');
    const verified = contract.interpretDmGiftRead({ kind: 'ok', body: accepted }, request.requestId);
    assert.equal(verified.outcome, 'verified');
    assert.equal(verified.chargedCoins, 5);
  });

  test('video payload carries unlock state and never a media address', () => {
    const paid = contract.videoCustomPayload({ videoId: request.requestId, title: '片段', priceCoins: 12, durationSec: 8 });
    const free = contract.videoCustomPayload({ videoId: request.requestId, title: '片段', priceCoins: 0, durationSec: 8 });
    assert.equal(paid.paywalled, true);
    assert.equal(free.paywalled, false);
    for (const payload of [paid, free]) {
      assert.deepEqual(Object.keys(payload).sort(), ['duration_sec', 'kind', 'paywalled', 'price_coins', 'title', 'v', 'video_id']);
      assert.equal(JSON.stringify(payload).includes('http'), false);
    }
    assert.equal(contract.sniffVideo(Buffer.concat([Buffer.alloc(4), Buffer.from('ftypisom')])), 'video/mp4');
    assert.equal(contract.sniffVideo(Buffer.from([0x1a, 0x45, 0xdf, 0xa3, 0, 0])), 'video/webm');
    assert.equal(contract.sniffVideo(Buffer.from('not-a-video')), null);
  });

  test('unlock grants playback only from a matching revenue acceptance', () => {
    const video = { videoId: 'vid-1', priceCoins: 12 };
    assert.equal(contract.interpretUnlock({ kind: 'ok', body: {} }, { ...video, priceCoins: 0 }, request.requestId).outcome, 'free');
    const unlocked = contract.interpretUnlock({ kind: 'ok', body: {
      accepted: true, unlocked: true, video_id: 'vid-1', request_id: request.requestId, charged_coins: 12, new_balance: 80, idempotency: 'created',
    } }, video, request.requestId);
    assert.equal(unlocked.outcome, 'unlocked');
    assert.equal(unlocked.chargedCoins, 12);
    assert.equal(contract.interpretUnlock({ kind: 'ok', body: {
      accepted: true, unlocked: true, video_id: 'vid-1', request_id: request.requestId, charged_coins: 1, idempotency: 'created',
    } }, video, request.requestId).outcome, 'retry');
    assert.equal(contract.interpretUnlock({ kind: 'ok', body: {
      accepted: true, unlocked: true, video_id: 'vid-1', request_id: request.requestId, charged_coins: 0, idempotency: 'replayed',
    } }, video, request.requestId).outcome, 'unlocked');
    assert.equal(contract.interpretUnlock({ kind: 'http', status: 501, body: {} }, video, request.requestId).outcome, 'unavailable');
    assert.equal(contract.interpretUnlock({ kind: 'http', status: 409, body: { code: 'INSUFFICIENT_BALANCE' } }, video, request.requestId).outcome, 'rejected');
    assert.equal(contract.interpretUnlock({ kind: 'ok', body: { unlocked: false } }, video, request.requestId).outcome, 'revoked');
  });

  test('access prefers an explicit revenue denial over a cached receipt', () => {
    assert.equal(contract.interpretAccess({ kind: 'ok', body: { unlocked: false } }, true), 'locked');
    assert.equal(contract.interpretAccess({ kind: 'ok', body: { unlocked: true } }, false), 'play');
    assert.equal(contract.interpretAccess({ kind: 'http', status: 404, body: 'missing route' }, false), 'locked');
    assert.equal(contract.interpretAccess({ kind: 'http', status: 404, body: 'missing route' }, true), 'play');
    assert.equal(contract.interpretAccess({ kind: 'network' }, false), 'locked');
    assert.equal(contract.interpretAccess({ kind: 'network' }, true), 'play');
  });

  test('support entry needs a configured id and is not a canned inbox', () => {
    assert.deepEqual(contract.supportContact('', '4', '客服'), { outcome: 'hidden' });
    assert.deepEqual(contract.supportContact('not valid', '4', '客服'), { outcome: 'hidden' });
    assert.equal(contract.supportContact('4', '4', '客服').outcome, 'self');
    assert.deepEqual(contract.supportContact('support', '4', ''), { outcome: 'ready', userId: 'support', name: '客服' });
  });

  test('gift and video requests reject self and malformed ids before any settlement call', () => {
    assert.equal(contract.validateDmGiftRequest({ request_id: request.requestId, recipient_id: '2', gift_id: 'HEART', quantity: 1 }, 1).ok, true);
    assert.equal(contract.validateDmGiftRequest({ request_id: request.requestId, recipient_id: '1', gift_id: 'HEART', quantity: 1 }, 1).ok, false);
    assert.equal(contract.validateDmGiftRequest({ request_id: 'nope', recipient_id: '2', gift_id: 'HEART', quantity: 1 }, 1).ok, false);
    const video = contract.validateVideoMeta({ request_id: request.requestId, recipient_id: '2', title: '片段', price_coins: 0, duration_sec: 3 }, 1);
    assert.equal(video.ok, true);
    assert.equal(contract.validateVideoMeta({ request_id: request.requestId, recipient_id: '2', title: '片段', price_coins: -1, duration_sec: 3 }, 1).ok, false);
  });
}
