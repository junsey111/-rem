import crypto from 'crypto';
import Router from 'koa-router';
import jwt from 'jsonwebtoken';
import axiosClient from 'axios';
import { authJwt } from '@/app/auth/authJwt';
import successHandler from '@/app/handler/success-handle';
import { JWT_SECRET } from '@/config/secret';
import { mediaAuthority, mediaSessionHash } from '@/service/media-authority';
import { requireMediaAccess } from '@/service/media-access';
import userModel from '@/model/user.model';
import { CustomError } from '@/model/customError.model';

const router = new Router({ prefix: '/srs' });
// Connections are revoked after a Billd restart; clients must obtain a fresh ticket.
const publishers = new Map<string, { token: string; live: string }>();
const livePattern = /^[1-9][0-9]{0,15}$/;
function internalKey(value: unknown): boolean {
  const candidate = Buffer.from(typeof value === 'string' ? value : '');
  const key = Buffer.from(process.env.MEDIA_AUTH_KEY || '');
  return key.length >= 32 && candidate.length === key.length && crypto.timingSafeEqual(candidate,key);
}
async function checkPublisher(token: string, live: string): Promise<void> {
  const proof: any = jwt.verify(token, JWT_SECRET, { algorithms:['HS256'], audience:'srs-publish' });
  if (proof.live !== live || !livePattern.test(live) || typeof proof.sub !== 'string') throw Error('Invalid publish ticket');
  const user: any = await userModel.findByPk(proof.sub, { attributes:['id','status','token'] });
  if (!user?.token || user.status === 2 || mediaSessionHash(user.token) !== proof.session_hash) throw Error('Session revoked');
  const claims: any = jwt.verify(user.token,JWT_SECRET,{algorithms:['HS256']});
  if (Number(claims.userInfo?.id) !== Number(user.id)) throw Error('Invalid session');
  const authority = await mediaAuthority(live);
  // The first publisher is the owning host, never an accepted co-host.
  if (!authority.open || authority.publishers?.[0] !== String(user.id)) throw Error('Publish denied');
}
router.post('/publish-token', async ctx => {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id) throw new CustomError(auth.message,auth.code,auth.code);
  const live = String(ctx.request.body?.live_session_id || '');
  if (!livePattern.test(live)) throw new CustomError('直播间无效',400,400);
  const token = jwt.sign({live,session_hash:mediaSessionHash((ctx.headers.authorization || '').slice(7))},JWT_SECRET,
    {algorithm:'HS256',audience:'srs-publish',subject:String(auth.userInfo.id),expiresIn:'24h'});
  try { await checkPublisher(token,live); } catch { throw new CustomError('没有该直播间的推流权限',403,403); }
  successHandler({ctx,data:{token}});
});
router.post('/hook', async ctx => {
  if (!internalKey(ctx.query.key)) { ctx.status=403;ctx.body={code:403};return; }
  const body=ctx.request.body || {};
  const id=String(body.client_id || '');
  if (body.action==='on_unpublish') { publishers.delete(id);ctx.body={code:0};return; }
  try {
    if (body.action!=='on_publish' || body.app!=='live' || !id || id.length>128) throw Error('Invalid hook');
    const token=new URLSearchParams(String(body.param || '').replace(/^\?/, '')).get('ticket') || '';
    await checkPublisher(token,String(body.stream || ''));
    if (publishers.size>=10000 && !publishers.has(id)) throw Error('Capacity exceeded');
    publishers.set(id,{token,live:String(body.stream)});ctx.body={code:0};
  } catch { ctx.status=403;ctx.body={code:403}; }
});
router.get('/authorize', async ctx => {
  if (!internalKey(ctx.headers['x-media-key'])) throw new CustomError('禁止访问',403,403);
  const uri=String(ctx.headers['x-original-uri'] || '').split('?')[0];
  const match=/^\/live\/([1-9][0-9]{0,15})(?:\.m3u8|-[0-9]+\.ts)$/.exec(uri);
  if (!match) throw new CustomError('媒体路径无效',403,403);
  const auth=await authJwt(ctx);
  if (auth.code!==200 || !auth.userInfo?.id) throw new CustomError(auth.message,auth.code,auth.code);
  const authority=await mediaAuthority(match[1]);
  if (!authority.open) throw new CustomError('直播已结束',403,403);
  try { await requireMediaAccess(String(auth.userInfo.id),match[1]); }
  catch (error) { if (error instanceof CustomError && error.statusCode===402) throw new CustomError('试看已结束',403,403); throw error; }
  ctx.status=204;
});

// Enumerate the actual SRS connections, not just successful callbacks. Unknown publishers
// (including connections surviving a gateway restart) are removed, and failures deny access.
export async function reconcileSrsPublishers(): Promise<void> {
  const base=process.env.SRS_INTERNAL_API_URL || 'http://srs:1985';
  const result=await axiosClient.get(`${base}/api/v1/clients?start=0&count=10000`,{timeout:3000});
  if (result.data?.code!==0 || !Array.isArray(result.data.clients)) throw Error('Invalid SRS state');
  const active=new Set<string>();
  for (const client of result.data.clients) {
    if (!client.publish) continue;
    const id=String(client.id);active.add(id);
    const proof=publishers.get(id);
    try { if (!proof || client.name!==proof.live) throw Error('Unknown publisher'); await checkPublisher(proof.token,proof.live); }
    catch { await axiosClient.delete(`${base}/api/v1/clients/${encodeURIComponent(id)}`,{timeout:3000});publishers.delete(id); }
  }
  for (const id of publishers.keys()) if (!active.has(id)) publishers.delete(id);
}
let running=false;
const timer=setInterval(async()=>{
  if (running || process.env.SRS_RECONCILE_ENABLED!=='true') return;
  running=true;
  try { await reconcileSrsPublishers(); } catch { console.error('SRS reconciliation failed; retrying'); } finally { running=false; }
},5000);
timer.unref();
export default router;
