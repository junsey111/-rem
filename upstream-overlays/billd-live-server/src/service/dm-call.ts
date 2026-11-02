import crypto from 'crypto';
import jwt from 'jsonwebtoken';

import { managedMediaRoom } from '@/service/media-room-name';

export { managedMediaRoom };

/** Same lifetime as live-room LiveKit grants. */
export const DM_CALL_TTL_MS = 10 * 60 * 1000;

export const DM_CALL_SIGNAL_TRANSPORT = 'openim-c2c-text';

export class DmCallError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'DmCallError';
    this.status = status;
  }
}

export type DmCallMode = 'video' | 'voice';

export interface DmCallPeer {
  id: string;
  disabled: boolean;
}

export interface DmCallGrant {
  call_id: string;
  room: string;
  mode: DmCallMode;
  caller_id: string;
  peer_id: string;
  url: string;
  token: string;
  expires_in: number;
  // OpenIM teammate hook. Delivery is not required to join with call_id.
  signal_transport: typeof DM_CALL_SIGNAL_TRANSPORT;
  signal_text: string;
}

export interface DmCallEnd {
  call_id: string;
  status: 'ended';
}

interface DmCallRecord {
  id: string;
  room: string;
  mode: DmCallMode;
  callerId: string;
  peerId: string;
  createdAt: number;
  expiresAt: number;
  ended: boolean;
}

interface TokenConfig {
  loginToken: string;
  url: string;
  apiKey: string;
  apiSecret: string;
  now?: number;
}

const calls = new Map<string, DmCallRecord>();
const pairs = new Map<string, string>();
let chain: Promise<void> = Promise.resolve();

export function resetDmCalls(): void {
  calls.clear();
  pairs.clear();
  chain = Promise.resolve();
}

export function dmCallSignalText(callId: string, mode: DmCallMode, fromUserId: string): string {
  return `dm-call:v1 call_id=${callId} mode=${mode} from=${fromUserId}`;
}

function exclusive<T>(fn: () => T): Promise<T> {
  const run = chain.then(fn, fn);
  chain = run.then(
    () => undefined,
    () => undefined,
  );
  return run;
}

function pairKey(left: string, right: string): string {
  return [left, right].sort().join(':');
}

function accountId(value: string): boolean {
  return /^[1-9][0-9]{0,9}$/.test(value);
}

function nowOf(input?: number): number {
  return input ?? Date.now();
}

function purge(now: number): void {
  for (const [id, call] of calls) {
    if (call.expiresAt > now) continue;
    calls.delete(id);
    const key = pairKey(call.callerId, call.peerId);
    if (pairs.get(key) === id) pairs.delete(key);
  }
}

function activeCallFor(userId: string, now: number): DmCallRecord | undefined {
  for (const call of calls.values()) {
    if (call.ended || call.expiresAt <= now) continue;
    if (call.callerId === userId || call.peerId === userId) return call;
  }
  return undefined;
}

function requireLiveKit(url: string, apiKey: string, apiSecret: string): void {
  if (!url || !apiKey || !apiSecret) throw new DmCallError('LiveKit 未配置', 503);
}

function requireLogin(loginToken: string): void {
  if (!loginToken) throw new DmCallError('未登录！', 401);
}

function publishSources(mode: DmCallMode): string[] {
  return mode === 'voice' ? ['microphone'] : ['microphone', 'camera'];
}

function signToken(record: DmCallRecord, userId: string, config: TokenConfig, now: number): string {
  const expiresIn = Math.max(1, Math.ceil((record.expiresAt - now) / 1000));
  return jwt.sign(
    {
      metadata: JSON.stringify({
        session_hash: crypto.createHash('sha256').update(config.loginToken).digest('hex'),
      }),
      video: {
        room: record.room,
        roomJoin: true,
        canPublish: true,
        canPublishSources: publishSources(record.mode),
        canSubscribe: true,
        canPublishData: false,
        canUpdateOwnMetadata: false,
      },
    },
    config.apiSecret,
    {
      algorithm: 'HS256',
      issuer: config.apiKey,
      subject: userId,
      expiresIn,
    },
  );
}

function grant(record: DmCallRecord, userId: string, config: TokenConfig, now: number): DmCallGrant {
  return {
    call_id: record.id,
    room: record.room,
    mode: record.mode,
    caller_id: record.callerId,
    peer_id: record.peerId,
    url: config.url,
    token: signToken(record, userId, config, now),
    expires_in: Math.max(1, Math.ceil((record.expiresAt - now) / 1000)),
    signal_transport: DM_CALL_SIGNAL_TRANSPORT,
    signal_text: dmCallSignalText(record.id, record.mode, record.callerId),
  };
}

function lookup(callId: string, now: number): DmCallRecord | undefined {
  purge(now);
  if (!/^[0-9a-f]{32}$/.test(callId)) throw new DmCallError('call_id 无效', 400);
  return calls.get(callId);
}

function openDmCallSync(input: {
  callerId: string;
  peer: DmCallPeer | null;
  mode: string;
} & TokenConfig): DmCallGrant {
  const now = nowOf(input.now);
  purge(now);
  requireLiveKit(input.url, input.apiKey, input.apiSecret);
  requireLogin(input.loginToken);
  if (!accountId(input.callerId)) throw new DmCallError('账号无效', 400);
  if (input.mode !== 'video' && input.mode !== 'voice') {
    throw new DmCallError('mode 只能是 video 或 voice', 400);
  }
  if (!input.peer) throw new DmCallError('对方账号不存在', 404);
  if (!accountId(input.peer.id)) throw new DmCallError('peer_id 无效', 400);
  if (input.peer.id === input.callerId) throw new DmCallError('不能给自己打电话', 400);
  if (input.peer.disabled) throw new DmCallError('对方账号不可用', 403);
  const mode = input.mode as DmCallMode;
  const existing = activeCallFor(input.callerId, now) || activeCallFor(input.peer.id, now);
  if (existing) {
    const samePair = pairKey(existing.callerId, existing.peerId) === pairKey(input.callerId, input.peer.id);
    if (!samePair || existing.mode !== mode) throw new DmCallError('已有进行中的通话，请先挂断', 409);
    return grant(existing, input.callerId, input, now);
  }
  const id = crypto.randomBytes(16).toString('hex');
  const room = `dm-${id}`;
  if (!room.startsWith('dm-') || managedMediaRoom.test(room)) {
    throw new DmCallError('通话房间名无效', 500);
  }
  const record: DmCallRecord = {
    id,
    room,
    mode,
    callerId: input.callerId,
    peerId: input.peer.id,
    createdAt: now,
    expiresAt: now + DM_CALL_TTL_MS,
    ended: false,
  };
  calls.set(id, record);
  pairs.set(pairKey(record.callerId, record.peerId), id);
  return grant(record, input.callerId, input, now);
}

export function openDmCall(input: {
  callerId: string;
  peer: DmCallPeer | null;
  mode: string;
} & TokenConfig): Promise<DmCallGrant> {
  return exclusive(() => openDmCallSync(input));
}

export function joinDmCall(input: { userId: string; callId: string } & TokenConfig): Promise<DmCallGrant> {
  return exclusive(() => {
    const now = nowOf(input.now);
    requireLiveKit(input.url, input.apiKey, input.apiSecret);
    requireLogin(input.loginToken);
    if (!accountId(input.userId)) throw new DmCallError('账号无效', 400);
    const record = lookup(input.callId, now);
    if (!record) throw new DmCallError('通话不存在或已过期', 404);
    if (record.ended) throw new DmCallError('通话已结束', 404);
    if (input.userId !== record.callerId && input.userId !== record.peerId) {
      throw new DmCallError('你不在这通通话中', 403);
    }
    return grant(record, input.userId, input, now);
  });
}

export function endDmCall(input: { userId: string; callId: string; now?: number }): Promise<DmCallEnd> {
  return exclusive(() => {
    const now = nowOf(input.now);
    if (!accountId(input.userId)) throw new DmCallError('账号无效', 400);
    let record: DmCallRecord | undefined;
    try {
      record = lookup(input.callId, now);
    } catch (error) {
      if (error instanceof DmCallError && error.status === 400) throw error;
      record = undefined;
    }
    if (!record) return { call_id: input.callId, status: 'ended' };
    if (input.userId !== record.callerId && input.userId !== record.peerId) {
      throw new DmCallError('你不在这通通话中', 403);
    }
    record.ended = true;
    const key = pairKey(record.callerId, record.peerId);
    if (pairs.get(key) === record.id) pairs.delete(key);
    return { call_id: record.id, status: 'ended' };
  });
}
