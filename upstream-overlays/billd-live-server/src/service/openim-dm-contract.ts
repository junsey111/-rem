/** Pure DM gift / video message decisions. No wallet writes live here. */

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const GIFT_ID = /^[A-Za-z0-9_-]{1,64}$/;
const USER_ID = /^[1-9][0-9]{0,9}$/;
const SUPPORT_ID = /^[A-Za-z0-9_-]{1,64}$/;

export type RevenueCall =
  | { kind: 'ok'; body: any }
  | { kind: 'http'; status: number; body: any }
  | { kind: 'network' };

export function isUnimplemented(status: number, body: any): boolean {
  if (status === 501) return true;
  const code = body && typeof body === 'object' ? body.code : undefined;
  if (code === 'NOT_IMPLEMENTED' || code === 'DM_GIFT_SETTLEMENT_UNAVAILABLE' || code === 'DM_VIDEO_UNLOCK_UNAVAILABLE') return true;
  // A framework 404 (plain text or no business code) means the route is not mounted yet.
  if (status === 404 && (body == null || body === '' || typeof body === 'string' || code === undefined || code === 404 || code === '404')) return true;
  return false;
}

const GIFT_REFUSALS: Record<string, string> = {
  CREATOR_NOT_ELIGIBLE: '对方当前无法接收礼物，本次未扣币',
  RECIPIENT_NOT_ELIGIBLE: '对方当前无法接收礼物，本次未扣币',
  GIFT_NOT_FOUND: '礼物已下架，本次未扣币',
  INSUFFICIENT_BALANCE: '余额不足，本次未扣币',
  SELF_GIFT: '不能给自己送礼，本次未扣币',
};

const UNLOCK_REFUSALS: Record<string, string> = {
  INSUFFICIENT_BALANCE: '余额不足，本次未扣币',
  VIDEO_NOT_FOUND: '视频不存在，本次未扣币',
  PRICE_MISMATCH: '解锁价格与结算不一致，本次未扣币',
};

export function validateDmGiftRequest(body: any, senderId: number): { ok: true; requestId: string; recipientId: string; giftId: string; quantity: number } | { ok: false; message: string } {
  if (!body || typeof body.request_id !== 'string' || !UUID_V4.test(body.request_id)) return { ok: false, message: '送礼请求缺少有效单号，请更新客户端后重试' };
  if (typeof body.recipient_id !== 'string' || !USER_ID.test(body.recipient_id)) return { ok: false, message: '对方账号无效' };
  if (Number(body.recipient_id) === senderId) return { ok: false, message: '不能给自己发送私信礼物' };
  if (typeof body.gift_id !== 'string' || !GIFT_ID.test(body.gift_id)) return { ok: false, message: '礼物无效' };
  const quantity = body.quantity;
  if (typeof quantity !== 'number' || !Number.isInteger(quantity) || quantity < 1 || quantity > 99) return { ok: false, message: '礼物数量无效' };
  return { ok: true, requestId: body.request_id.toLowerCase(), recipientId: body.recipient_id, giftId: body.gift_id, quantity };
}

function whole(value: any): value is number {
  return typeof value === 'number' && Number.isInteger(value);
}

export function interpretDmGift(call: RevenueCall, request: { requestId: string; giftId: string; quantity: number }, catalogName?: string) {
  if (call.kind === 'network') return { outcome: 'retry' as const, message: '结算服务暂时不可用，本次未确认扣币，请用同一单号重试' };
  if (call.kind === 'http') {
    if (isUnimplemented(call.status, call.body)) return { outcome: 'unavailable' as const, message: '私信送礼结算尚未接通，本次未扣币，也未发送礼物消息', code: 'DM_GIFT_SETTLEMENT_UNAVAILABLE' };
    const code = call.body?.code;
    if (call.status >= 400 && call.status < 500 && typeof code === 'string' && GIFT_REFUSALS[code]) {
      return { outcome: 'rejected' as const, status: call.status, code, message: GIFT_REFUSALS[code] };
    }
    return { outcome: 'retry' as const, message: '结算结果尚未确认，请用同一单号重试，不要重新赠送' };
  }
  const body = call.body || {};
  const name = typeof body.gift_name === 'string' && body.gift_name.trim() ? body.gift_name.trim() : (catalogName || '').trim();
  const orderId = typeof body.gift_order_id === 'string' ? body.gift_order_id.trim() : '';
  const status = body.status === undefined ? 'ACCEPTED' : body.status;
  if (body.accepted !== true || status !== 'ACCEPTED' || !orderId
    || body.gift_id !== request.giftId || body.quantity !== request.quantity || String(body.request_id || '').toLowerCase() !== request.requestId
    || !whole(body.charged_coins) || body.charged_coins < 0 || !name || name.length > 40) {
    return { outcome: 'retry' as const, message: '结算回执不完整，未发送礼物消息。请用同一单号重试核实，不要重新赠送' };
  }
  const custom = {
    v: 1,
    kind: 'dm_gift',
    request_id: request.requestId,
    gift_order_id: orderId,
    gift_id: request.giftId,
    gift_name: name,
    quantity: request.quantity,
    charged_coins: body.charged_coins,
    status: 'ACCEPTED',
  };
  return {
    outcome: 'accepted' as const,
    chargedCoins: body.charged_coins,
    newBalance: whole(body.new_balance) ? body.new_balance : undefined,
    idempotency: typeof body.idempotency === 'string' ? body.idempotency : undefined,
    imExtension: 'dm_gift',
    imDescription: `礼物 ${name} × ${request.quantity}`.slice(0, 80),
    imCustom: custom,
  };
}

export function validateVideoMeta(meta: any, senderId: number): { ok: true; requestId: string; recipientId: string; title: string; priceCoins: number; durationSec: number } | { ok: false; message: string } {
  if (!meta || typeof meta.request_id !== 'string' || !UUID_V4.test(meta.request_id)) return { ok: false, message: '视频请求缺少有效单号' };
  if (typeof meta.recipient_id !== 'string' || !USER_ID.test(meta.recipient_id)) return { ok: false, message: '对方账号无效' };
  if (Number(meta.recipient_id) === senderId) return { ok: false, message: '不能给自己发送视频' };
  if (typeof meta.title !== 'string' || !meta.title.trim() || meta.title.trim().length > 40 || /[\u0000-\u001F]/.test(meta.title)) return { ok: false, message: '视频标题需为 1 至 40 个字符' };
  if (!whole(meta.price_coins) || meta.price_coins < 0 || meta.price_coins > 1000000) return { ok: false, message: '视频价格无效' };
  if (!whole(meta.duration_sec) || meta.duration_sec < 1 || meta.duration_sec > 600) return { ok: false, message: '视频时长无效' };
  return { ok: true, requestId: meta.request_id.toLowerCase(), recipientId: meta.recipient_id, title: meta.title.trim(), priceCoins: meta.price_coins, durationSec: meta.duration_sec };
}

export function sniffVideo(bytes: Buffer): 'video/mp4' | 'video/webm' | null {
  if (bytes.length >= 12 && bytes.toString('ascii', 4, 8) === 'ftyp') return 'video/mp4';
  if (bytes.length >= 4 && bytes[0] === 0x1a && bytes[1] === 0x45 && bytes[2] === 0xdf && bytes[3] === 0xa3) return 'video/webm';
  return null;
}

export function videoCustomPayload(video: { videoId: string; title: string; priceCoins: number; durationSec: number }) {
  return {
    v: 1,
    kind: 'dm_video',
    video_id: video.videoId,
    title: video.title,
    price_coins: video.priceCoins,
    duration_sec: video.durationSec,
    paywalled: video.priceCoins > 0,
  };
}

export function videoDescription(title: string, priceCoins: number): string {
  return (priceCoins > 0 ? `付费视频 ${title}` : `视频 ${title}`).slice(0, 80);
}

export function interpretUnlock(call: RevenueCall, video: { videoId: string; priceCoins: number }, requestId: string) {
  if (video.priceCoins === 0) return { outcome: 'free' as const };
  if (call.kind === 'network') return { outcome: 'retry' as const, message: '结算服务暂时不可用，本次未确认扣币，请用同一单号重试' };
  if (call.kind === 'http') {
    if (isUnimplemented(call.status, call.body)) return { outcome: 'unavailable' as const, message: '付费视频解锁结算尚未接通，本次未扣币', code: 'DM_VIDEO_UNLOCK_UNAVAILABLE' };
    const code = call.body?.code;
    if (call.status >= 400 && call.status < 500 && typeof code === 'string' && UNLOCK_REFUSALS[code]) {
      return { outcome: 'rejected' as const, status: call.status, code, message: UNLOCK_REFUSALS[code] };
    }
    return { outcome: 'retry' as const, message: '解锁结果尚未确认，请用同一单号重试，不要重新支付' };
  }
  const body = call.body || {};
  const replay = body.idempotency === 'replayed';
  const chargedOk = whole(body.charged_coins) && body.charged_coins >= 0 && (replay || body.charged_coins === video.priceCoins);
  if (body.unlocked === false) return { outcome: 'revoked' as const, message: '结算记录显示该视频未解锁' };
  if (body.accepted !== true || body.unlocked !== true || body.video_id !== video.videoId || String(body.request_id || '').toLowerCase() !== requestId || !chargedOk) {
    return { outcome: 'retry' as const, message: '解锁回执不完整或与标价不一致，未开放播放。请用同一单号核对' };
  }
  return {
    outcome: 'unlocked' as const,
    chargedCoins: body.charged_coins,
    newBalance: whole(body.new_balance) ? body.new_balance : undefined,
    idempotency: typeof body.idempotency === 'string' ? body.idempotency : undefined,
  };
}

/** Explicit Revenue denial wins. A missing route may fall back to a previously accepted receipt. */
export function interpretAccess(call: RevenueCall, hasReceipt: boolean): 'play' | 'locked' {
  if (call.kind === 'ok') {
    if (call.body?.unlocked === true) return 'play';
    if (call.body?.unlocked === false) return 'locked';
    return hasReceipt ? 'play' : 'locked';
  }
  if (call.kind === 'http' && call.body?.unlocked === false) return 'locked';
  if (call.kind === 'http' && isUnimplemented(call.status, call.body)) return hasReceipt ? 'play' : 'locked';
  return hasReceipt ? 'play' : 'locked';
}

export function interpretDmGiftRead(call: RevenueCall, requestId: string) {
  if (call.kind === 'network') return { outcome: 'retry' as const, message: '结算核对暂时不可用，请稍后重试' };
  if (call.kind === 'http') {
    if (isUnimplemented(call.status, call.body)) return { outcome: 'unavailable' as const, message: '私信送礼核对尚未接通', code: 'DM_GIFT_SETTLEMENT_UNAVAILABLE' };
    if (call.status === 404) return { outcome: 'missing' as const, message: '未查到这笔私信送礼结算记录' };
    return { outcome: 'retry' as const, message: '结算核对尚未完成，请稍后重试' };
  }
  const body = call.body || {};
  if (body.accepted !== true || typeof body.gift_order_id !== 'string' || !body.gift_order_id.trim() || !whole(body.charged_coins) || String(body.request_id || '').toLowerCase() !== requestId) {
    return { outcome: 'retry' as const, message: '结算核对回执不完整，请稍后重试' };
  }
  return {
    outcome: 'verified' as const,
    requestId,
    giftOrderId: body.gift_order_id,
    giftName: typeof body.gift_name === 'string' ? body.gift_name : '',
    chargedCoins: body.charged_coins,
    status: body.status || 'ACCEPTED',
  };
}

export function supportContact(configured: string, callerId: string, nickname: string): { outcome: 'hidden' } | { outcome: 'self' } | { outcome: 'ready'; userId: string; name: string } {
  const userId = (configured || '').trim();
  if (!SUPPORT_ID.test(userId)) return { outcome: 'hidden' };
  if (userId === callerId) return { outcome: 'self' };
  const name = (nickname || '').trim().replace(/[\u0000-\u001F]/g, '').slice(0, 32) || '客服';
  return { outcome: 'ready', userId, name };
}
