import { INTERNAL_SERVICE_KEY, REVENUE_API_URL } from '@/config/secret';
import axios from '@/utils/request';
import { CustomError } from '@/model/customError.model';

export async function requireMediaAccess(userId: string, liveId: string, room = liveId): Promise<number> {
  const result: any = await axios.post(`${REVENUE_API_URL}/v1/internal/media-access`, {
    user_id: Number(userId), live_session_id: Number(liveId), room,
  }, { headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY }, timeout: 3000 });
  if (result?.allowed !== true) throw new CustomError('试看已结束，请解锁后继续观看', 402, 402);
  const access=result.access;
  if (access?.status==='FREE' || access?.status==='UNLOCKED') return 600;
  if (access?.status==='PREVIEW' && Number.isInteger(access.seconds_remaining) && access.seconds_remaining>0 && access.seconds_remaining<=60) return access.seconds_remaining;
  throw new CustomError('媒体权限状态无效',503,503);
}
