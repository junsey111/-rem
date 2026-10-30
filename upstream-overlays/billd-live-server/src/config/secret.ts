const numberEnv = (name: string, fallback: number) => Number(process.env[name] || fallback);

export const JWT_SECRET = process.env.JWT_SECRET || 'change-me-in-development';
export const QINIU_ACCESSKEY = process.env.QINIU_ACCESSKEY || '';
export const QINIU_SECRETKEY = process.env.QINIU_SECRETKEY || '';
export const QINIU_CALLBACK_URL = process.env.QINIU_CALLBACK_URL || '';
export const WWW_QQ_CLIENT_ID = process.env.WWW_QQ_CLIENT_ID || '';
export const WWW_QQ_CLIENT_SECRET = process.env.WWW_QQ_CLIENT_SECRET || '';
export const WWW_QQ_REDIRECT_URI = process.env.WWW_QQ_REDIRECT_URI || '';
export const ADMIN_QQ_CLIENT_ID = process.env.ADMIN_QQ_CLIENT_ID || '';
export const ADMIN_QQ_CLIENT_SECRET = process.env.ADMIN_QQ_CLIENT_SECRET || '';
export const ADMIN_QQ_REDIRECT_URI = process.env.ADMIN_QQ_REDIRECT_URI || '';
export const GITHUB_CLIENT_ID = process.env.GITHUB_CLIENT_ID || '';
export const GITHUB_CLIENT_SECRET = process.env.GITHUB_CLIENT_SECRET || '';
export const GITHUB_REDIRECT_URI = process.env.GITHUB_REDIRECT_URI || '';
export const BILIBILI_LIVE_KEY = process.env.BILIBILI_LIVE_KEY || '';
export const BILIBILI_LIVE_URL = process.env.BILIBILI_LIVE_URL || '';
export const REVENUE_API_URL = process.env.REVENUE_API_URL || 'http://127.0.0.1:8080';
export const INTERNAL_SERVICE_KEY = process.env.INTERNAL_SERVICE_KEY || '';
export const PAYMENT_WEBHOOK_SECRET = process.env.PAYMENT_WEBHOOK_SECRET || '';
export const PUBLIC_BASE_URL = process.env.PUBLIC_BASE_URL || 'http://localhost:4300';
export const LIVEKIT_API_KEY = process.env.LIVEKIT_API_KEY || 'devkey';
export const LIVEKIT_API_SECRET = process.env.LIVEKIT_API_SECRET || 'secret';
export const OPENIM_API_URL = process.env.OPENIM_API_URL || 'http://openim-server:10002';
export const OPENIM_PUBLIC_API_URL = process.env.OPENIM_PUBLIC_API_URL || 'http://10.0.2.2:10002';
export const OPENIM_SECRET = process.env.OPENIM_SECRET || '';
export const OPENIM_ADMIN_USER_ID = process.env.OPENIM_ADMIN_USER_ID || 'imAdmin';
export const OPENIM_PLATFORM_ID = numberEnv('OPENIM_PLATFORM_ID', 1);
export const OPENIM_WS_URL = process.env.OPENIM_WS_URL || 'ws://10.0.2.2:10001';
export const GAODE_WEB_IP_URL = process.env.GAODE_WEB_IP_URL || '';
export const GAODE_WEB_IP_KEY = process.env.GAODE_WEB_IP_KEY || '';
export const QQ_EMAIL_USER = process.env.QQ_EMAIL_USER || '';
export const QQ_EMAIL_PASS = process.env.QQ_EMAIL_PASS || '';
export const IP_WHITE_LIST = (process.env.IP_WHITE_LIST || '127.0.0.1').split(',');

export const MYSQL_CONFIG = {
  database: process.env.MYSQL_DATABASE || 'billd_live',
  username: process.env.MYSQL_USER || 'billd',
  password: process.env.MYSQL_PASSWORD || 'billd',
  host: process.env.MYSQL_HOST || '127.0.0.1',
  port: numberEnv('MYSQL_PORT', 3306),
};
export const SSH_CONFIG = { username: '', password: '', host: '', port: 22 };
export const REDIS_CONFIG = {
  database: numberEnv('REDIS_DATABASE', 0),
  socket: { port: numberEnv('REDIS_PORT', 6379), host: process.env.REDIS_HOST || '127.0.0.1' },
  password: process.env.REDIS_PASSWORD || '',
};
export const ALIPAY_CONFIG = {
  appId: process.env.ALIPAY_APP_ID || '',
  privateKey: process.env.ALIPAY_PRIVATE_KEY || '',
  alipayPublicKey: process.env.ALIPAY_PUBLIC_KEY || '',
};
