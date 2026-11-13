import { EventEmitter } from 'events';

export const giftEvents = new EventEmitter();

const roomIdPattern = /^[1-9][0-9]{0,15}$/;
const requestIdPattern =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const giftIdPattern = /^[A-Za-z0-9_-]{1,32}$/;
const REPLAY_MS = 8_000;
const REPLAY_LIMIT = 3;

export type AcceptedGiftEvent = {
  roomId: string;
  request_id: string;
  gift_id: string;
  viewer_id: number;
  quantity: number;
  sender_name: string;
};

type StoredGift = AcceptedGiftEvent & { at: number };

const recentByRoom = new Map<string, StoredGift[]>();

export function sanitizeSenderName(raw: unknown): string {
  const text = String(raw ?? '').replace(/[\u0000-\u001F\u007F]/g, '').trim();
  return Array.from(text).slice(0, 20).join('');
}

/** Fan out a server-accepted gift. Effect URLs stay on the catalog, not this event. */
export function publishAcceptedGift(input: {
  roomId: unknown;
  requestId: unknown;
  giftId: unknown;
  viewerId: unknown;
  quantity: unknown;
  senderName?: unknown;
  now?: number;
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
  const event: AcceptedGiftEvent = {
    roomId,
    request_id: requestId,
    gift_id: giftId,
    viewer_id: viewerId,
    quantity,
    sender_name: sanitizeSenderName(input.senderName),
  };
  const at = input.now ?? Date.now();
  rememberGift(event, at);
  giftEvents.emit('accepted', event);
}

/** Gifts accepted in the last few seconds, so a client that just joined still sees the same effect. */
export function recentAcceptedGifts(roomId: string, now = Date.now()): AcceptedGiftEvent[] {
  const fresh = freshGifts(String(roomId ?? ''), now);
  if (fresh.length === 0) recentByRoom.delete(String(roomId ?? ''));
  else recentByRoom.set(String(roomId ?? ''), fresh);
  return fresh.map((item) => ({
    roomId: item.roomId,
    request_id: item.request_id,
    gift_id: item.gift_id,
    viewer_id: item.viewer_id,
    quantity: item.quantity,
    sender_name: item.sender_name,
  }));
}

function rememberGift(event: AcceptedGiftEvent, at: number) {
  const fresh = freshGifts(event.roomId, at).filter((item) => item.request_id !== event.request_id);
  fresh.push({ ...event, at });
  while (fresh.length > REPLAY_LIMIT) fresh.shift();
  recentByRoom.set(event.roomId, fresh);
  if (recentByRoom.size > 200) {
    for (const [id, items] of recentByRoom) {
      const kept = items.filter((item) => at - item.at <= REPLAY_MS);
      if (kept.length === 0) recentByRoom.delete(id);
      else recentByRoom.set(id, kept);
    }
  }
}

function freshGifts(roomId: string, now: number): StoredGift[] {
  return (recentByRoom.get(roomId) ?? []).filter((item) => now - item.at <= REPLAY_MS && now >= item.at);
}
