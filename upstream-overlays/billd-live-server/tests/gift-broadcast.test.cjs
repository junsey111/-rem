const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');

const source = path.resolve(__dirname, '../src/service/gift-broadcast.ts');

test('accepted gifts fan out once and replay only the recent window', async () => {
  const { publishAcceptedGift, recentAcceptedGifts, giftEvents, sanitizeSenderName } = await import(source);
  const room = `8${Date.now()}`;
  const seen = [];
  const onAccepted = (event) => { if (event.roomId === room) seen.push(event); };
  giftEvents.on('accepted', onAccepted);
  try {
    const base = {
      roomId: room,
      giftId: 'CASTLE',
      viewerId: 7,
      quantity: 2,
      senderName: '  小柚\n  ',
      now: 10_000,
    };
    publishAcceptedGift({ ...base, requestId: '11111111-1111-4111-8111-111111111111' });
    publishAcceptedGift({ ...base, requestId: '22222222-2222-4222-8222-222222222222', now: 11_000 });
    publishAcceptedGift({ ...base, requestId: '33333333-3333-4333-8333-333333333333', now: 12_000 });
    publishAcceptedGift({ ...base, requestId: '44444444-4444-4444-8444-444444444444', now: 13_000 });
    publishAcceptedGift({ ...base, requestId: 'not-a-uuid', now: 13_000 });
    assert.equal(seen.length, 4);
    assert.equal(seen[0].sender_name, '小柚');
    assert.equal(seen[0].quantity, 2);
    assert.equal(seen[0].effect_url, undefined);
    const recent = recentAcceptedGifts(room, 13_000);
    assert.deepEqual(recent.map((event) => event.request_id), [
      '22222222-2222-4222-8222-222222222222',
      '33333333-3333-4333-8333-333333333333',
      '44444444-4444-4444-8444-444444444444',
    ]);
    publishAcceptedGift({ ...base, requestId: '44444444-4444-4444-8444-444444444444', now: 13_500 });
    assert.equal(recentAcceptedGifts(room, 13_500).filter((event) => event.request_id.startsWith('44444444')).length, 1);
    assert.deepEqual(recentAcceptedGifts(room, 13_500 + 8_001), []);
    assert.equal(sanitizeSenderName(`  ${'名'.repeat(30)}\u0001`), '名'.repeat(20));
  } finally {
    giftEvents.off('accepted', onAccepted);
  }
});
