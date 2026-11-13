import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import axiosClient from 'axios';
import sequelize from '@/config/mysql';
import { JWT_SECRET, OPENIM_ADMIN_USER_ID, OPENIM_API_URL, OPENIM_PLATFORM_ID, OPENIM_SECRET } from '@/config/secret';
import User from '@/model/user.model';
import Session from '@/model/openimSession.model';
import Live from '@/model/live.model';
import { CustomError } from '@/model/customError.model';
import { mediaAuthority } from '@/service/media-authority';
import axios from '@/utils/request';
import { assertPeerAccess } from '@/service/interaction-policy';

const hash = (token: string) => crypto.createHash('sha256').update(token).digest('hex');
function validAccount(user: any, token: string): boolean {
  if (!user || user.status === 2 || user.token !== token) return false;
  try {
    const claims: any = jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] });
    return Number(claims.userInfo?.id) === Number(user.id) && typeof claims.iat === 'number'
      && claims.iat <= Date.now() / 1000 + 30 && claims.exp > claims.iat && claims.exp - claims.iat <= 86400;
  } catch { return false; }
}
function responseData(body: any) {
  if (!body || body.errCode !== 0) throw Error('OpenIM request refused');
  return body.data;
}
async function adminHeaders() {
  if (!OPENIM_SECRET) throw Error('OpenIM unavailable');
  const headers = { operationID: crypto.randomUUID() };
  const admin = responseData(await axios.post(`${OPENIM_API_URL}/auth/get_admin_token`, {
    secret: OPENIM_SECRET, userID: OPENIM_ADMIN_USER_ID,
  }, { headers, timeout: 5000 }));
  if (typeof admin?.token !== 'string' || !admin.token) throw Error('OpenIM unavailable');
  return { ...headers, token: admin.token };
}
async function revoke(userId: number, platformId: number, headers: any) {
  responseData(await axios.post(`${OPENIM_API_URL}/auth/force_logout`, {
    userID: String(userId), platformID: platformId,
  }, { headers, timeout: 5000 }));
}
async function registerUser(user: any, headers: any) {
  const registered: any = await axios.post(`${OPENIM_API_URL}/user/user_register`, { users: [{
    userID: String(user.id), nickname: user.username, faceURL: user.avatar || '',
  }] }, { headers, timeout: 5000 }).catch(error => {
    if (error?.response?.data?.errCode === 1102) return error.response.data;
    throw error;
  });
  if (registered?.errCode !== 1102) responseData(registered);
}

/** Register a contact without issuing or resetting that user's token. */
export async function registerOpenImContact(userId: string, nickname: string, faceURL = '') {
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(userId)) throw new CustomError('消息联系人无效', 400, 400);
  const name = nickname.trim().slice(0, 32) || '用户';
  try {
    await registerUser({ id: userId, username: name, avatar: faceURL }, await adminHeaders());
  } catch (error) {
    if (error instanceof CustomError) throw error;
    throw new CustomError('消息服务暂时不可用，请稍后重试', 503, 503);
  }
}

/** Resolve only a live authorized host. This does not issue or reset recipient credentials. */
export async function resolveLiveRecipient(roomId: string, senderId: number) {
  if (!/^[1-9][0-9]{0,15}$/.test(roomId)) throw new CustomError('直播间无效，请刷新后重试', 400, 400);
  try {
    const room: any = await Live.findOne({ where: { roomId } });
    if (!room) throw new CustomError('直播间已结束，请刷新后重试', 404, 404);
    const details = typeof room.data === 'string' ? JSON.parse(room.data) : room.data;
    const id = details?.data?.userId;
    if (details?.roomId !== roomId || !/^[1-9][0-9]{0,9}$/.test(String(id))) throw Error('Invalid room identity');
    if (Number(id) === senderId) throw new CustomError('不能给自己发送私信', 400, 400);
    const authority = await mediaAuthority(roomId);
    // During PK, the single-host media room is closed while its directory entry remains valid.
    if (!authority.directory_open || !authority.publishers.includes(String(id))) {
      throw new CustomError('直播间已结束，请刷新后重试', 404, 404);
    }
    return await sequelize.transaction(async transaction => {
      const user: any = await User.findByPk(Number(id), { transaction, lock: transaction.LOCK.UPDATE });
      if (!user || user.status !== 1) throw new CustomError('该主播暂时无法接收私信', 404, 404);
      await assertPeerAccess(senderId, Number(user.id));
      await registerUser(user, await adminHeaders());
      return { user_id: String(user.id), name: user.username || '未设置昵称' };
    });
  } catch (error) {
    if (error instanceof CustomError) throw error;
    throw new CustomError('暂时无法发起私信，请稍后重试', 503, 503);
  }
}
const cached = (row: any, token: string) => row && !row.pending && row.session_hash === hash(token)
  && typeof row.im_token === 'string' && row.expires_at > Date.now() / 1000 + 60;
async function reusable(row: any, token: string): Promise<boolean> {
  if (!cached(row, token)) return false;
  try {
    const body: any = await axios.post(`${OPENIM_API_URL}/auth/parse_token`, { token: row.im_token }, {
      headers: { operationID: crypto.randomUUID() }, timeout: 5000,
    });
    if (body?.errCode >= 1501 && body.errCode <= 1507) return false;
    const parsed = responseData(body);
    return parsed?.userID === String(row.user_id) && parsed?.platformID === Number(row.platform_id);
  } catch (error) {
    const code = axiosClient.isAxiosError(error) ? error.response?.data?.errCode : undefined;
    if (typeof code === 'number' && code >= 1501 && code <= 1507) return false;
    throw new CustomError('消息服务暂时不可用，请稍后重试', 503, 503);
  }
}

export async function issueOpenImSession(userId: number, token: string) {
  const where = { user_id: userId, platform_id: OPENIM_PLATFORM_ID };
  // Commit intent before making remote changes. A crash after issuing a token leaves a retryable revocation.
  const existing = await sequelize.transaction(async transaction => {
    const user: any = await User.findByPk(userId, { transaction, lock: transaction.LOCK.UPDATE });
    if (!validAccount(user, token)) throw new CustomError('登录已失效，请重新登录', 401, 401);
    const row: any = await Session.findOne({ where, transaction, lock: transaction.LOCK.UPDATE });
    if (await reusable(row, token)) return { token: row.im_token, expires_at: Number(row.expires_at) };
    if (row) await row.update({ pending: true, im_token: null }, { transaction });
    else await Session.create({ ...where, pending: true }, { transaction });
    return null;
  });
  if (existing) return existing;
  try {
    return await sequelize.transaction(async transaction => {
      const user: any = await User.findByPk(userId, { transaction, lock: transaction.LOCK.UPDATE });
      if (!validAccount(user, token)) throw new CustomError('登录已失效，请重新登录', 401, 401);
      const row: any = await Session.findOne({ where, transaction, lock: transaction.LOCK.UPDATE });
      if (await reusable(row, token)) return { token: row.im_token, expires_at: Number(row.expires_at) };
      // A worker may have already consumed the committed intent between transactions.
      // Never issue remotely unless that durable intent still exists under this lock.
      if (!row?.pending) throw new CustomError('消息会话已重置，请重试连接', 503, 503);
      const headers = await adminHeaders();
      await revoke(userId, OPENIM_PLATFORM_ID, headers);
      await registerUser(user, headers);
      const issued = responseData(await axios.post(`${OPENIM_API_URL}/auth/get_user_token`, {
        userID: String(userId), platformID: OPENIM_PLATFORM_ID,
      }, { headers, timeout: 5000 }));
      const claims: any = typeof issued?.token === 'string' ? jwt.decode(issued.token) : null;
      const expiresAt = Number(claims?.exp);
      if (!Number.isFinite(expiresAt) || expiresAt <= Date.now() / 1000 + 60) throw Error('Invalid OpenIM session');
      await row.update({ session_hash: hash(token), im_token: issued.token, expires_at: expiresAt, pending: false }, { transaction });
      return { token: issued.token, expires_at: expiresAt };
    });
  } catch (error) {
    if (error instanceof CustomError) throw error;
    throw new CustomError('消息服务暂时不可用，请稍后重试', 503, 503);
  }
}

// User-row locks serialize issuance and revocation across requests and processes.
// ponytail: sequential scan suits one small deployment; paginate and bound concurrency at larger user counts.
export async function reconcileOpenImSessions() {
  const rows: any[] = await Session.findAll({ attributes: ['user_id', 'platform_id'] });
  let failures = 0;
  for (const key of rows) {
    try {
      await sequelize.transaction(async transaction => {
        const user: any = await User.findByPk(key.user_id, { transaction, lock: transaction.LOCK.UPDATE });
        const row: any = await Session.findOne({ where: { user_id: key.user_id, platform_id: key.platform_id }, transaction, lock: transaction.LOCK.UPDATE });
        if (!row) return;
        if (!row.pending && validAccount(user, user?.token) && cached(row, user.token)) return;
        await revoke(key.user_id, key.platform_id, await adminHeaders());
        await row.destroy({ transaction });
      });
    } catch { failures++; }
  }
  if (failures) throw Error('OpenIM reconciliation incomplete');
}

export function startOpenImReconciliation(server): () => void {
  let stopped = false;
  let timer: ReturnType<typeof setTimeout>;
  const run = async () => {
    try { await reconcileOpenImSessions(); }
    catch { console.error('OpenIM session reconciliation failed; will retry'); }
    if (!stopped) { timer = setTimeout(run, 5000); timer.unref(); }
  };
  void run();
  const stop = () => { stopped = true; clearTimeout(timer); };
  server.once('close', stop);
  return stop;
}
