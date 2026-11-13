import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import axiosClient from 'axios';
import {
  OPENIM_DM_MEDIA_DIR,
  OPENIM_SUPPORT_NICKNAME,
  OPENIM_SUPPORT_USER_ID,
  REVENUE_API_URL,
  REVENUE_DM_GIFTS_PATH,
  REVENUE_DM_VIDEO_ACCESS_PATH,
  REVENUE_DM_VIDEO_UNLOCK_PATH,
} from '@/config/secret';
import sequelize from '@/config/mysql';
import { CustomError } from '@/model/customError.model';
import User from '@/model/user.model';
import { DmVideo, DmVideoUnlock } from '@/model/openimDmVideo.model';
import axios from '@/utils/request';
import { registerOpenImContact } from '@/service/openim-session';
import { assertPeerAccess } from '@/service/interaction-policy';
import { createUserNotification } from '@/service/push-notifications';
import {
  interpretAccess,
  interpretDmGift,
  interpretDmGiftRead,
  interpretUnlock,
  RevenueCall,
  sniffVideo,
  supportContact,
  validateDmGiftRequest,
  validateVideoMeta,
  videoCustomPayload,
  videoDescription,
} from '@/service/openim-dm-contract';

const VIDEO_LIMIT = 12 * 1024 * 1024;

/** Carries an HTTP result that must keep settlement markers. Do not let the generic handler rewrite it. */
export class DmHookResult extends Error {
  constructor(readonly httpStatus: number, readonly payload: Record<string, unknown>) {
    super(String(payload.message || ''));
  }
}

function unavailable(message: string, code: string) {
  return new DmHookResult(501, { code: 501, errorCode: 501, message, settlement: 'UNAVAILABLE', settlement_code: code });
}
function rejected(status: number, code: string, message: string) {
  return new DmHookResult(status, { code: status, errorCode: code, message, gift_outcome: 'REJECTED' });
}
function locked() {
  return new DmHookResult(402, { code: 402, errorCode: 402, message: '请先解锁后再观看', settlement: 'LOCKED' });
}

function unwrap(body: any) {
  if (body && typeof body === 'object' && body.accepted === undefined && body.unlocked === undefined && body.gift_order_id === undefined
    && body.data && typeof body.data === 'object') return body.data;
  return body;
}

async function callRevenue(run: () => Promise<any>): Promise<RevenueCall> {
  try {
    return { kind: 'ok', body: unwrap(await run()) };
  } catch (error) {
    if (axiosClient.isAxiosError(error)) {
      if (!error.response) return { kind: 'network' };
      return { kind: 'http', status: error.response.status, body: error.response.data };
    }
    return { kind: 'network' };
  }
}

function throwInterpreted(result: { outcome: string; message?: string; status?: number; code?: string }) {
  if (result.outcome === 'unavailable') throw unavailable(result.message || '结算尚未接通', result.code || 'NOT_IMPLEMENTED');
  if (result.outcome === 'rejected') throw rejected(result.status || 409, result.code || 'REJECTED', result.message || '本次未扣币');
  if (result.outcome === 'retry' || result.outcome === 'missing') {
    const status = result.outcome === 'missing' ? 404 : 503;
    throw new CustomError(result.message || '请稍后重试', status, status);
  }
}

async function requirePeer(senderId: number, recipientId: string) {
  const user: any = await User.findByPk(Number(recipientId));
  if (!user || user.status !== 1) throw new CustomError('对方暂时无法接收这条消息', 404, 404);
  await assertPeerAccess(senderId, Number(user.id));
  await registerOpenImContact(String(user.id), user.username || '未设置昵称', user.avatar || '');
  return user;
}

async function catalogName(giftId: string): Promise<string | undefined> {
  try {
    const body: any = await axios.get(`${REVENUE_API_URL}/v1/gifts/catalog`, { timeout: 5000 });
    const rows = body?.gifts || body?.data?.gifts;
    if (!Array.isArray(rows)) return undefined;
    const row = rows.find(item => item?.gift_id === giftId);
    return typeof row?.display_name === 'string' ? row.display_name : undefined;
  } catch {
    return undefined;
  }
}

function mediaRoot() {
  return path.resolve(OPENIM_DM_MEDIA_DIR || path.join(process.cwd(), 'data', 'openim-dm-video'));
}

function mediaFile(storageName: string) {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(storageName)) {
    throw new CustomError('视频不存在', 404, 404);
  }
  const root = mediaRoot();
  const target = path.resolve(root, storageName);
  if (target !== path.join(root, storageName)) throw new CustomError('视频不存在', 404, 404);
  return target;
}

async function writeMedia(storageName: string, bytes: Buffer) {
  const target = mediaFile(storageName);
  await fs.promises.mkdir(path.dirname(target), { recursive: true });
  const partial = `${target}.partial`;
  await fs.promises.rm(partial, { force: true });
  await fs.promises.writeFile(partial, bytes, { flag: 'wx', mode: 0o600 });
  await fs.promises.rename(partial, target);
}

function presentVideo(row: any) {
  const custom = videoCustomPayload({
    videoId: row.video_id, title: row.title, priceCoins: Number(row.price_coins), durationSec: Number(row.duration_sec),
  });
  return {
    video_id: row.video_id,
    request_id: row.request_id,
    price_coins: Number(row.price_coins),
    duration_sec: Number(row.duration_sec),
    paywalled: Number(row.price_coins) > 0,
    im_extension: 'dm_video',
    im_description: videoDescription(row.title, Number(row.price_coins)),
    im_custom: custom,
  };
}

export async function resolveSupportContact(callerId: number) {
  const decision = supportContact(OPENIM_SUPPORT_USER_ID, String(callerId), OPENIM_SUPPORT_NICKNAME);
  if (decision.outcome === 'hidden') throw new CustomError('客服暂未开通', 404, 404);
  if (decision.outcome === 'self') throw new CustomError('不能向自己发起客服会话', 400, 400);
  let name = decision.name;
  let face = '';
  if (/^[1-9][0-9]{0,9}$/.test(decision.userId)) {
    const user: any = await User.findByPk(Number(decision.userId));
    if (user && user.status !== 1) throw new CustomError('客服暂未开通', 404, 404);
    if (user && user.status === 1) {
      name = user.username || name;
      face = user.avatar || '';
    }
  }
  await registerOpenImContact(decision.userId, name, face);
  return { user_id: decision.userId, name };
}

export async function submitDmGift(senderId: number, body: any, token: string) {
  const request = validateDmGiftRequest(body, senderId);
  if (!request.ok) throw new CustomError(request.message, 400, 400);
  await requirePeer(senderId, request.recipientId);
  const call = await callRevenue(() => axios.post(`${REVENUE_API_URL}${REVENUE_DM_GIFTS_PATH}`, {
    request_id: request.requestId,
    viewer_id: senderId,
    recipient_id: Number(request.recipientId),
    gift_id: request.giftId,
    quantity: request.quantity,
    sent_at_ms: 0,
  }, { headers: { Authorization: `Bearer ${token}` }, timeout: 10000 }));
  const name = call.kind === 'ok' && !(typeof call.body?.gift_name === 'string' && call.body.gift_name.trim())
    ? await catalogName(request.giftId) : undefined;
  const result = interpretDmGift(call, request, name);
  throwInterpreted(result);
  if (result.outcome !== 'accepted') throw new CustomError('结算结果尚未确认，请用同一单号重试', 503, 503);
  void createUserNotification(Number(request.recipientId), {
    kind: 'MESSAGE',
    actor_id: senderId,
    title: '收到私信礼物',
    body: `${result.imCustom.gift_name || '礼物'} ×${request.quantity}`,
  }).catch(error => console.error('DM gift notification failed', error?.message || error));
  return {
    accepted: true,
    request_id: request.requestId,
    gift_order_id: result.imCustom.gift_order_id,
    gift_id: request.giftId,
    gift_name: result.imCustom.gift_name,
    quantity: request.quantity,
    charged_coins: result.chargedCoins,
    ...(result.newBalance === undefined ? {} : { new_balance: result.newBalance }),
    ...(result.idempotency ? { idempotency: result.idempotency } : {}),
    im_extension: result.imExtension,
    im_description: result.imDescription,
    im_custom: result.imCustom,
  };
}

export async function readDmGift(requestId: string, token: string) {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(requestId)) {
    throw new CustomError('送礼单号格式有误，请核对后重试', 400, 400);
  }
  const id = requestId.toLowerCase();
  const call = await callRevenue(() => axios.get(`${REVENUE_API_URL}${REVENUE_DM_GIFTS_PATH}/${encodeURIComponent(id)}`, {
    headers: { Authorization: `Bearer ${token}` }, timeout: 10000,
  }));
  const result = interpretDmGiftRead(call, id);
  if (result.outcome === 'unavailable') throw unavailable(result.message, result.code);
  throwInterpreted(result);
  if (result.outcome !== 'verified') throw new CustomError('结算核对尚未完成，请稍后重试', 503, 503);
  return { request_id: result.requestId, gift_order_id: result.giftOrderId, gift_name: result.giftName, charged_coins: result.chargedCoins, status: result.status };
}

export async function readVideoBytes(ctx): Promise<Buffer> {
  if (ctx.get('content-type') !== 'application/octet-stream') throw new CustomError('请以二进制方式上传视频', 415, 415);
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of ctx.req) {
    const buffer = Buffer.from(chunk);
    size += buffer.length;
    if (size > VIDEO_LIMIT) throw new CustomError('视频不能超过 12MB', 413, 413);
    chunks.push(buffer);
  }
  const bytes = Buffer.concat(chunks);
  if (!bytes.length) throw new CustomError('视频内容为空', 400, 400);
  return bytes;
}

export function parseVideoHeader(header: string) {
  try {
    return JSON.parse(Buffer.from(header, 'base64').toString('utf8'));
  } catch {
    throw new CustomError('视频信息不完整', 400, 400);
  }
}

export async function storeDmVideo(senderId: number, meta: any, bytes: Buffer, token: string) {
  const request = validateVideoMeta(meta, senderId);
  if (!request.ok) throw new CustomError(request.message, 400, 400);
  if (request.priceCoins > 0) {
    const applications: any = await axios.get(`${REVENUE_API_URL}/v1/users/me/creator-applications`, {
      headers: { Authorization: `Bearer ${token}` }, timeout: 10000,
    });
    if (!applications?.reviews?.some((application) => application.status === 'APPROVED'))
      throw new CustomError('付费视频需要主播认证审核通过', 403, 403);
  }
  const contentType = sniffVideo(bytes);
  if (!contentType) throw new CustomError('仅支持 MP4 或 WebM 视频', 400, 400);
  await requirePeer(senderId, request.recipientId);
  const hash = crypto.createHash('sha256').update(bytes).digest('hex');
  const existing: any = await DmVideo.findOne({ where: { owner_user_id: senderId, request_id: request.requestId } });
  if (existing) {
    if (existing.sha256 !== hash || String(existing.recipient_user_id) !== request.recipientId || Number(existing.price_coins) !== request.priceCoins || existing.title !== request.title) {
      throw new CustomError('同一单号的视频内容不一致', 409, 409);
    }
    return presentVideo(existing);
  }
  const videoId = crypto.randomUUID();
  try {
    await sequelize.transaction(async transaction => {
      await DmVideo.create({
        video_id: videoId,
        request_id: request.requestId,
        owner_user_id: senderId,
        recipient_user_id: Number(request.recipientId),
        title: request.title,
        price_coins: request.priceCoins,
        duration_sec: request.durationSec,
        content_type: contentType,
        byte_length: bytes.length,
        sha256: hash,
        storage_name: videoId,
      }, { transaction });
      await writeMedia(videoId, bytes);
    });
  } catch (error) {
    if (error instanceof CustomError || error instanceof DmHookResult) throw error;
    const again: any = await DmVideo.findOne({ where: { owner_user_id: senderId, request_id: request.requestId } });
    if (!again) throw new CustomError('视频暂时无法保存，请用同一单号重试', 503, 503);
    if (again.sha256 !== hash || String(again.recipient_user_id) !== request.recipientId || Number(again.price_coins) !== request.priceCoins || again.title !== request.title) {
      throw new CustomError('同一单号的视频内容不一致', 409, 409);
    }
    return presentVideo(again);
  }
  const stored = await DmVideo.findByPk(videoId);
  if (!stored) throw new CustomError('视频暂时无法保存，请用同一单号重试', 503, 503);
  void createUserNotification(Number(request.recipientId), {
    kind: 'MESSAGE',
    actor_id: senderId,
    title: '收到私信视频',
    body: request.title,
  }).catch(error => console.error('DM video notification failed', error?.message || error));
  return presentVideo(stored);
}

async function loadVideo(videoId: string) {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(videoId)) throw new CustomError('视频不存在', 404, 404);
  const row: any = await DmVideo.findByPk(videoId);
  if (!row) throw new CustomError('视频不存在', 404, 404);
  return row;
}

function participant(row: any, viewerId: number) {
  return Number(row.owner_user_id) === viewerId || Number(row.recipient_user_id) === viewerId;
}

async function revenueAccess(videoId: string, token: string): Promise<RevenueCall> {
  const base = REVENUE_DM_VIDEO_ACCESS_PATH.replace(/\/$/, '');
  return callRevenue(() => axios.get(`${REVENUE_API_URL}${base}/${encodeURIComponent(videoId)}/access`, {
    headers: { Authorization: `Bearer ${token}` }, timeout: 10000,
  }));
}

async function playable(row: any, viewerId: number, token: string) {
  if (Number(row.owner_user_id) === viewerId || Number(row.price_coins) === 0) return true;
  const receipt = await DmVideoUnlock.findOne({ where: { video_id: row.video_id, viewer_user_id: viewerId } });
  return interpretAccess(await revenueAccess(row.video_id, token), !!receipt) === 'play';
}

export async function describeDmVideo(viewerId: number, videoId: string, token: string) {
  const row = await loadVideo(videoId);
  if (!participant(row, viewerId)) throw new CustomError('视频不存在', 404, 404);
  await assertPeerAccess(viewerId, Number(row.owner_user_id) === viewerId ? Number(row.recipient_user_id) : Number(row.owner_user_id));
  const canPlay = await playable(row, viewerId, token);
  return {
    video_id: row.video_id,
    title: row.title,
    price_coins: Number(row.price_coins),
    duration_sec: Number(row.duration_sec),
    paywalled: Number(row.price_coins) > 0,
    playable: canPlay,
  };
}

export async function openDmVideoMedia(viewerId: number, videoId: string, token: string) {
  const row = await loadVideo(videoId);
  if (!participant(row, viewerId)) throw new CustomError('视频不存在', 404, 404);
  await assertPeerAccess(viewerId, Number(row.owner_user_id) === viewerId ? Number(row.recipient_user_id) : Number(row.owner_user_id));
  if (!await playable(row, viewerId, token)) throw locked();
  const file = mediaFile(row.storage_name);
  if (!fs.existsSync(file)) throw new CustomError('视频文件暂时不可用，请稍后重试', 503, 503);
  return { path: file, contentType: row.content_type };
}

export async function unlockDmVideo(viewerId: number, videoId: string, body: any, token: string) {
  const row = await loadVideo(videoId);
  if (!participant(row, viewerId)) throw new CustomError('视频不存在', 404, 404);
  await assertPeerAccess(viewerId, Number(row.owner_user_id) === viewerId ? Number(row.recipient_user_id) : Number(row.owner_user_id));
  if (Number(row.owner_user_id) === viewerId) throw new CustomError('发送者无需解锁自己的视频', 400, 400);
  if (!body || typeof body.request_id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.request_id)) {
    throw new CustomError('解锁请求缺少有效单号', 400, 400);
  }
  const requestId = body.request_id.toLowerCase();
  if (Number(row.price_coins) === 0) return { unlocked: true, video_id: row.video_id, request_id: requestId, charged_coins: 0, idempotency: 'free' };
  const existing: any = await DmVideoUnlock.findOne({ where: { video_id: row.video_id, viewer_user_id: viewerId } });
  const call = await callRevenue(() => axios.post(`${REVENUE_API_URL}${REVENUE_DM_VIDEO_UNLOCK_PATH}`, {
    request_id: requestId,
    viewer_id: viewerId,
    video_id: row.video_id,
    owner_id: Number(row.owner_user_id),
    price_coins: Number(row.price_coins),
    sent_at_ms: 0,
  }, { headers: { Authorization: `Bearer ${token}` }, timeout: 10000 }));
  const result = interpretUnlock(call, { videoId: row.video_id, priceCoins: Number(row.price_coins) }, requestId);
  if (result.outcome === 'revoked') {
    if (existing) await existing.destroy();
    throw new CustomError(result.message, 409, 409);
  }
  if (result.outcome === 'unavailable' && existing) {
    return { unlocked: true, video_id: row.video_id, request_id: existing.request_id, charged_coins: Number(existing.charged_coins), idempotency: 'cached_receipt' };
  }
  throwInterpreted(result);
  if (result.outcome !== 'unlocked') throw new CustomError('解锁结果尚未确认，请用同一单号重试', 503, 503);
  await DmVideoUnlock.upsert({
    video_id: row.video_id, viewer_user_id: viewerId, request_id: requestId, charged_coins: result.chargedCoins,
  });
  return {
    unlocked: true,
    video_id: row.video_id,
    request_id: requestId,
    charged_coins: result.chargedCoins,
    ...(result.newBalance === undefined ? {} : { new_balance: result.newBalance }),
    ...(result.idempotency ? { idempotency: result.idempotency } : {}),
  };
}
