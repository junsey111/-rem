import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import axiosClient from 'axios';
import {
  INTERNAL_SERVICE_KEY,
  LIVEKIT_API_KEY,
  LIVEKIT_API_SECRET,
  REVENUE_API_URL,
} from '@/config/secret';
import axios from '@/utils/request';
import userModel from '@/model/user.model';
import liveModel from '@/model/live.model';

const managedRoom = /^(?:pk-)?[1-9][0-9]{0,19}$/;
export const mediaSessionHash = (token: string) =>
  crypto.createHash('sha256').update(token).digest('hex');

export async function mediaAuthority(
  room: string
): Promise<{
  room: string;
  open: boolean;
  directory_open: boolean;
  publishers: string[];
  publish_sources?: Record<string, string[]>;
}> {
  const state: any = await axios.get(
    `${REVENUE_API_URL}/v1/internal/media-rooms/${encodeURIComponent(room)}`,
    {
      headers: { 'X-Internal-Key': INTERNAL_SERVICE_KEY },
      timeout: 3000,
    }
  );
  if (
    state?.room !== room ||
    typeof state.open !== 'boolean' ||
    typeof state.directory_open !== 'boolean' ||
    !Array.isArray(state.publishers) ||
    state.publishers.some(
      (id) => typeof id !== 'string' || !/^[1-9][0-9]*$/.test(id)
    ) ||
    (state.open && !state.publishers.length)
  )
    throw Error('Invalid media authority response');
  if (
    state.publish_sources !== undefined &&
    (!state.publish_sources ||
      typeof state.publish_sources !== 'object' ||
      Array.isArray(state.publish_sources) ||
      Object.entries(state.publish_sources).some(
        ([id, sources]) =>
          !state.publishers.includes(id) ||
          !Array.isArray(sources) ||
          !sources.length ||
          sources.some((source) => !['microphone', 'camera'].includes(source))
      ))
  )
    throw Error('Invalid media source authority');
  return state;
}

export async function liveKitControl(
  method: string,
  room?: string,
  data: object = {}
): Promise<any> {
  // Dedicated internal HTTP endpoint; never send administrator tokens to the public WS URL.
  const url = process.env.LIVEKIT_API_URL || 'http://livekit:7880';
  const token = jwt.sign(
    {
      video: room
        ? {
            room,
            roomAdmin: true,
            ...(['CreateRoom', 'DeleteRoom'].includes(method)
              ? { roomCreate: true }
              : {}),
          }
        : { roomList: true },
    },
    LIVEKIT_API_SECRET,
    { algorithm: 'HS256', issuer: LIVEKIT_API_KEY, expiresIn: 30 }
  );
  try {
    const response = await axiosClient.post(
      `${url}/twirp/livekit.RoomService/${method}`,
      { ...(room ? { room } : {}), ...data },
      {
        headers: { Authorization: `Bearer ${token}` },
        timeout: 3000,
      }
    );
    return response.data;
  } catch (error) {
    if (
      axiosClient.isAxiosError(error) &&
      error.response?.status === 404 &&
      ['DeleteRoom', 'RemoveParticipant'].includes(method)
    )
      return {};
    throw error;
  }
}

async function validSession(
  identity: string,
  metadata: string
): Promise<boolean> {
  if (!/^[1-9][0-9]*$/.test(identity)) return false;
  let proof: any;
  try {
    proof = JSON.parse(metadata);
  } catch {
    return false;
  }
  const user: any = await userModel.findByPk(identity, {
    attributes: ['id', 'status', 'token'],
  });
  if (
    !user ||
    user.status === 2 ||
    !user.token ||
    proof?.session_hash !== mediaSessionHash(user.token)
  )
    return false;
  const claims: any = jwt.decode(user.token);
  return Number(claims?.exp) > Date.now() / 1000;
}

// Enumerate server state each pass, so crashes and failed close requests need no in-memory queue.
// ponytail: sequential requests keep passes disjoint; bound parallelism when room volume requires it.
export async function reconcileMedia(io): Promise<void> {
  let failures = 0;
  let rooms: any[] = [];
  try {
    const result = await liveKitControl('ListRooms');
    if (!Array.isArray(result?.rooms)) throw Error('Invalid LiveKit room list');
    rooms = result.rooms;
  } catch {
    failures++;
  }
  for (const room of rooms) {
    try {
      if (!managedRoom.test(room.name || '')) continue;
      const authority = await mediaAuthority(room.name);
      if (!authority.open) {
        await liveKitControl('DeleteRoom', room.name);
        continue;
      }
      const participants = await liveKitControl('ListParticipants', room.name);
      if (!Array.isArray(participants?.participants))
        throw Error('Invalid LiveKit participant list');
      for (const participant of participants.participants) {
        if (!(await validSession(participant.identity, participant.metadata))) {
          await liveKitControl('RemoveParticipant', room.name, {
            identity: participant.identity,
          });
        } else {
          const canPublish = authority.publishers.includes(
            participant.identity
          );
          const sources =
            authority.publish_sources?.[participant.identity]?.map((source) =>
              source === 'microphone' ? 2 : 1
            ) || [];
          const actualSources =
            participant.permission?.can_publish_sources || [];
          if (
            participant.permission?.can_publish === canPublish &&
            !participant.permission?.can_publish_data &&
            !participant.permission?.can_update_metadata &&
            JSON.stringify([...actualSources].sort()) ===
              JSON.stringify([...sources].sort())
          )
            continue;
          await liveKitControl('UpdateParticipant', room.name, {
            identity: participant.identity,
            permission: {
              can_publish: canPublish,
              can_publish_sources: sources,
              can_subscribe: true,
              can_publish_data: false,
              can_update_metadata: false,
            },
          });
        }
      }
    } catch {
      failures++;
    }
  }
  const rows: any[] = await liveModel.findAll();
  for (const row of rows) {
    try {
      if (
        !managedRoom.test(row.roomId || '') ||
        String(row.roomId).startsWith('pk-')
      )
        continue;
      const socket = io.sockets.sockets.get(row.socketId);
      const authority = await mediaAuthority(row.roomId);
      let owner: string | undefined;
      try {
        owner = String(JSON.parse(row.data)?.data?.userId);
      } catch {
        /* malformed directory row */
      }
      if (
        !socket?.connected ||
        !authority.directory_open ||
        !authority.publishers.includes(owner || '') ||
        !(await validSession(
          owner || '',
          JSON.stringify({ session_hash: socket?.data?.mediaSessionHash })
        ))
      ) {
        await liveModel.destroy({
          where: { id: row.id, socketId: row.socketId },
        });
        socket?.emit('liveEnded', { roomId: row.roomId });
        socket?.disconnect(true);
      }
    } catch {
      failures++;
    }
  }
  if (failures) throw Error('Media reconciliation incomplete');
}

export function startMediaReconciliation(io): () => void {
  let stopped = false;
  let timer: ReturnType<typeof setTimeout>;
  const run = async () => {
    try {
      await reconcileMedia(io);
    } catch {
      console.error('Media reconciliation failed; will retry');
    }
    if (!stopped) {
      timer = setTimeout(run, 5000);
      timer.unref();
    }
  };
  void run();
  io.httpServer?.once('close', () => {
    stopped = true;
    clearTimeout(timer);
  });
  return () => {
    stopped = true;
    clearTimeout(timer);
  };
}
