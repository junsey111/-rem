import History from '@/model/dmCallHistory.model';
import { DmCallError, readDmCall } from '@/service/dm-call';

/** Snapshots contain presentation state only, never media grants or billing authority. */
export async function snapshotDmCall(userId: string, callId: string) {
  const view = readDmCall({ userId, callId });
  await History.upsert({ call_id: callId, caller_id: Number(view.caller_id), peer_id: Number(view.peer_id), payload_json: JSON.stringify(view) });
  return view;
}
export async function storedDmCall(userId: string, callId: string) {
  try { return await snapshotDmCall(userId, callId); }
  catch (error) { if (!(error instanceof DmCallError) || error.status !== 404) throw error; }
  const row: any = await History.findByPk(callId);
  if (!row) throw new DmCallError('通话记录不存在', 404);
  if (String(row.caller_id) !== userId && String(row.peer_id) !== userId) throw new DmCallError('无权查看该通话', 403);
  const view = JSON.parse(row.payload_json);
  if (view.status === 'ringing') { view.status = 'no_answer'; view.connected = false; view.duration_seconds = 0; }
  if (view.status === 'connected') view.status = 'ended';
  return view;
}
