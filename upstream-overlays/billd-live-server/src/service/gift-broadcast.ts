import { EventEmitter } from 'events';

export const giftEvents = new EventEmitter();

const roomIdPattern = /^[1-9][0-9]{0,15}$/;
const requestIdPattern =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const giftIdPattern = /^[A-Za-z0-9_-]{1,32}$/;

/** Fan out a server-accepted gift. Effect URLs stay on the catalog, not this event. */
export function publishAcceptedGift(input: {
  roomId: unknown;
  requestId: unknown;
  giftId: unknown;
  viewerId: unknown;
  quantity: unknown;
}) {
  const roomId = String(input.roomId ?? '');
  const requestId = String(input.requestId ?? '').toLowerCase();
  const giftId = String(input.giftId ?? '');
  const viewerId = Number(input.viewerId);
  const quantity = Number(input.quantity);
  if (
    !roomIdPattern.test(roomId) ||
    !Number.isSafeInteger(Number(roomId)) ||
    !requestIdPattern.test(requestId) ||
    !giftIdPattern.test(giftId) ||
    !Number.isSafeInteger(viewerId) ||
    viewerId <= 0 ||
    !Number.isInteger(quantity) ||
    quantity < 1 ||
    quantity > 99
  ) {
    return;
  }
  giftEvents.emit('accepted', {
    roomId,
    request_id: requestId,
    gift_id: giftId,
    viewer_id: viewerId,
    quantity,
  });
}
