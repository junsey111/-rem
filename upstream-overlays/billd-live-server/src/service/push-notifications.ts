import crypto from 'crypto';
import fs from 'fs';
import { QueryTypes } from 'sequelize';

import axios from '@/utils/request';
import NotificationPreference from '@/model/notificationPreference.model';
import { UserNotification, UserPushToken } from '@/model/userNotification.model';
import { FCM_SERVICE_ACCOUNT_JSON } from '@/config/secret';

type NotificationKind = 'VISITOR' | 'MESSAGE' | 'ANNOUNCEMENT' | 'MARKETING';

type ServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
};

type PushPayload = {
  title: string;
  body: string;
  kind: NotificationKind;
  id?: string;
  notification_id?: string;
  actor_id?: number | null;
};

let serviceAccount: ServiceAccount | null | undefined;
let accessToken: { value: string; expiresAt: number } | null = null;

function encoded(value: string) {
  return Buffer.from(value).toString('base64url');
}

function loadServiceAccount(): ServiceAccount | null {
  if (serviceAccount !== undefined) return serviceAccount;
  const raw = FCM_SERVICE_ACCOUNT_JSON.trim();
  if (!raw) {
    serviceAccount = null;
    return serviceAccount;
  }
  try {
    const source = fs.existsSync(raw) ? fs.readFileSync(raw, 'utf8') : raw;
    const parsed = JSON.parse(source);
    if (typeof parsed.project_id !== 'string' || !parsed.project_id
      || typeof parsed.client_email !== 'string' || !parsed.client_email
      || typeof parsed.private_key !== 'string' || !parsed.private_key.includes('PRIVATE KEY')) {
      throw new Error('invalid FCM service account');
    }
    serviceAccount = {
      project_id: parsed.project_id,
      client_email: parsed.client_email,
      private_key: parsed.private_key.replace(/\\n/g, '\n'),
    };
  } catch (error) {
    console.error('FCM service account configuration is invalid', error);
    serviceAccount = null;
  }
  return serviceAccount;
}

async function bearerToken(account: ServiceAccount) {
  if (accessToken && accessToken.expiresAt > Date.now() + 60_000) return accessToken.value;
  const now = Math.floor(Date.now() / 1000);
  const header = encoded(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claim = encoded(JSON.stringify({
    iss: account.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }));
  const signature = crypto.createSign('RSA-SHA256').update(`${header}.${claim}`).sign(account.private_key, 'base64url');
  const assertion = `${header}.${claim}.${signature}`;
  const response: any = await axios.post('https://oauth2.googleapis.com/token',
    `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${encodeURIComponent(assertion)}`,
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, timeout: 8000 });
  const token = response?.data?.access_token;
  const expiresIn = Number(response?.data?.expires_in || 3600);
  if (typeof token !== 'string' || !token) throw new Error('FCM access token missing');
  accessToken = { value: token, expiresAt: Date.now() + Math.max(60, expiresIn - 60) * 1000 };
  return token;
}

function preferenceKey(kind: NotificationKind) {
  if (kind === 'VISITOR') return 'visitors';
  if (kind === 'ANNOUNCEMENT') return 'announcements';
  if (kind === 'MARKETING') return 'marketing';
  return 'messages';
}

async function sendToken(account: ServiceAccount, token: string, payload: PushPayload) {
  const tokenValue = await bearerToken(account);
  await axios.post(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(account.project_id)}/messages:send`, {
    message: {
      token,
      data: Object.fromEntries(Object.entries({
        kind: payload.kind,
        id: payload.id,
        notification_id: payload.notification_id,
        title: payload.title,
        body: payload.body,
        actor_id: payload.actor_id == null ? undefined : String(payload.actor_id),
      }).filter(([, value]) => value !== undefined)),
      android: { priority: 'HIGH' },
    },
  }, { headers: { Authorization: `Bearer ${tokenValue}` }, timeout: 8000 });
}

async function sendToUser(userId: number, payload: PushPayload) {
  const account = loadServiceAccount();
  if (!account) return;
  const [preference, tokens]: [any, any[]] = await Promise.all([
    NotificationPreference.findOne({ where: { user_id: userId }, raw: true }),
    UserPushToken.findAll({ where: { user_id: userId }, raw: true }),
  ]);
  if (preference && preference[preferenceKey(payload.kind)] === false) return;
  await Promise.all(tokens.map(async tokenRow => {
    try {
      await sendToken(account, String(tokenRow.token), payload);
    } catch (error: any) {
      const status = error?.response?.status;
      const code = error?.response?.data?.error?.details?.[0]?.errorCode;
      if (status === 404 || status === 410 || code === 'UNREGISTERED') {
        await UserPushToken.destroy({ where: { token_id: tokenRow.token_id } });
      } else {
        console.error('FCM notification delivery failed', error?.message || error);
      }
    }
  }));
}

export async function createUserNotification(userId: number, payload: PushPayload) {
  const preference: any = await NotificationPreference.findOne({ where: { user_id: userId }, raw: true });
  if (preference && preference[preferenceKey(payload.kind)] === false) return null;
  const [notification, created] = await UserNotification.findOrCreate({
    where: { user_id: userId, kind: payload.kind, actor_id: payload.actor_id == null ? null : payload.actor_id },
    defaults: {
      user_id: userId,
      kind: payload.kind,
      actor_id: payload.actor_id == null ? null : payload.actor_id,
      title: payload.title,
      body: payload.body,
      read_at: null,
      created_at: new Date(),
    },
  });
  if (!created) await notification.update({ title: payload.title, body: payload.body, read_at: null, created_at: new Date() });
  void sendToUser(userId, { ...payload, notification_id: String((notification as any).notification_id) })
    .catch(error => console.error('FCM notification scheduling failed', error?.message || error));
  return notification;
}

export function queueAnnouncementPush(announcement: { id: string; title: string; summary?: string }) {
  const account = loadServiceAccount();
  if (!account) return;
  void (async () => {
    const rows: any[] = await UserPushToken.sequelize!.query(`
      SELECT t.user_id, t.token
      FROM user_push_token t
      LEFT JOIN user_notification_preference p ON p.user_id = t.user_id
      WHERE COALESCE(p.announcements, 1) = 1`, { type: QueryTypes.SELECT });
    const payload: PushPayload = { kind: 'ANNOUNCEMENT', id: announcement.id, title: announcement.title, body: announcement.summary || '平台公告' };
    await Promise.all(rows.map(async row => {
      try {
        await sendToken(account, String(row.token), payload);
      } catch (error: any) {
        const status = error?.response?.status;
        const code = error?.response?.data?.error?.details?.[0]?.errorCode;
        if (status === 404 || status === 410 || code === 'UNREGISTERED') await UserPushToken.destroy({ where: { token: row.token } });
        else console.error('FCM announcement delivery failed', error?.message || error);
      }
    }));
  })().catch(error => console.error('FCM announcement scheduling failed', error?.message || error));
}
