import { Server, Socket } from 'socket.io';

import { PROJECT_ENV, PROJECT_ENV_ENUM } from '@/constant';
import { REVENUE_API_URL } from '@/config/secret';
import { authJwt } from '@/app/auth/authJwt';
import { mediaSessionHash } from '@/service/media-authority';
import { RequestRateLimiter } from '@/app/request-rate';
import { giftEvents } from '@/service/gift-broadcast';
import { persistChat, chatEvents } from '@/service/room-chat';
import { roomPresence } from '@/service/room-presence';
import axios from '@/utils/request';
import { chalkINFO } from '@/utils/chalkTip';

import { WsConnectStatusEnum, WsMsgTypeEnum } from './constant';
import DBController from './mysql.controller';

function socketToken(socket: Socket): string | undefined {
  const cookieToken = socket.handshake.headers.cookie
    ?.split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith('token='))
    ?.slice(6);
  return (
    socket.handshake.auth?.token ||
    socket.handshake.headers.authorization?.split(' ')[1] ||
    cookieToken
  );
}

async function getAllLiveUser(io) {
  const allSocketsMap = await io.fetchSockets();
  return Object.keys(allSocketsMap).map((item) => {
    return {
      id: allSocketsMap[item].id,
      rooms: [...allSocketsMap[item].rooms.values()],
    };
  });
}

export const connectWebSocket = (server) => {
  if (PROJECT_ENV === PROJECT_ENV_ENUM.beta) {
    console.log(chalkINFO('当前是beta环境，不初始化websocket'));
    return;
  }
  console.log(chalkINFO('当前不是beta环境，初始化websocket'));

  const io = new Server(server);
  const notifyModeration = (data) =>
    io.to(data.roomId).emit('chatModeration', data);
  chatEvents.on('moderation', notifyModeration);
  const notifyMessage = (data) => io.to(data.roomId).emit(WsMsgTypeEnum.message,data);
  chatEvents.on('message',notifyMessage);
  const notifyGift = (data) => {
    if (!data?.roomId) return;
    io.to(String(data.roomId)).emit('giftAccepted', {
      roomId: String(data.roomId),
      request_id: String(data.request_id || ''),
      gift_id: String(data.gift_id || ''),
      viewer_id: Number(data.viewer_id),
      quantity: Number(data.quantity || 1),
    });
  };
  giftEvents.on('accepted', notifyGift);
  server.once('close', () => {
    chatEvents.off('moderation', notifyModeration);
    chatEvents.off('message', notifyMessage);
    giftEvents.off('accepted', notifyGift);
  });

  // socket.emit会将消息发送给发件人
  // socket.broadcast.emit会将消息发送给除了发件人以外的所有人
  // io.emit会将消息发送给所有人，包括发件人

  io.on(WsConnectStatusEnum.connection, (socket: Socket) => {
    console.log(new Date().toLocaleString(), 'connection', socket.id);
    // Order join/leave writes on one socket; disconnect cleanup also waits for them.
    let directoryWork: Promise<void> = Promise.resolve();
    const enqueue = (work: () => Promise<void>) => {
      directoryWork = directoryWork.then(work).catch(() => {
        socket.emit('joinRejected', { message: '直播间操作未确认，请重试' });
      });
    };
    // const currLiveUser = await getAllLiveUser(io);
    // io.emit(WsMsgTypeEnum.liveUser, currLiveUser);

    // 收到用户进入房间
    socket.on(
      WsMsgTypeEnum.join,
      (data: {
        roomId: string;
        roomName: string;
        data: any;
        isAdmin?: boolean;
        roomType?: string;
        welcomeText?: string;
        backgroundId?: number;
      }) =>
        enqueue(async () => {
          try {
            if (!socket.connected) return;
            if (
              !data ||
              typeof data.roomId !== 'string' ||
              !/^[1-9][0-9]{0,15}$/.test(data.roomId) ||
              !Number.isSafeInteger(Number(data.roomId))
            ) {
              throw new Error('直播间编号无效');
            }
            let canonical = data;
            if (data.isAdmin) {
              if (data.isAdmin !== true) throw new Error('开播身份无效');
              const token = socketToken(socket);
              const auth = await authJwt({
                req: {
                  headers: {
                    authorization: token ? `Bearer ${token}` : undefined,
                  },
                },
              });
              if (auth.code !== 200 || !auth.userInfo?.id)
                throw new Error('登录已失效或账号不可用，请重新登录');
              if (Number(data.data?.userId) !== Number(auth.userInfo.id))
                throw new Error('不能使用其他主播的身份');
              const live: any = await axios.get(
                `${REVENUE_API_URL}/v1/lives/${data.roomId}`,
                {
                  headers: { Authorization: `Bearer ${token}` },
                  timeout: 5000,
                }
              );
              if (
                live?.status !== 'OPEN' ||
                String(live.live_session_id) !== data.roomId ||
                Number(live.creator_id) !== Number(auth.userInfo.id)
              ) {
                throw new Error('直播已结束或不属于当前账号');
              }
              canonical = {
                roomId: data.roomId,
                roomName:
                  typeof data.roomName === 'string'
                    ? data.roomName.trim().slice(0, 80)
                    : '直播间',
                roomType: ['SINGLE', 'MULTI', 'VOICE'].includes((data as any).roomType)
                  ? (data as any).roomType : 'SINGLE',
                welcomeText: typeof (data as any).welcomeText === 'string'
                  ? (data as any).welcomeText.trim().slice(0, 120) : '',
                backgroundId: Number.isInteger((data as any).backgroundId) &&
                  (data as any).backgroundId >= 0 && (data as any).backgroundId <= 3
                  ? (data as any).backgroundId : 0,
                isAdmin: true,
                data: {
                  userId: Number(auth.userInfo.id),
                  username: auth.userInfo.username,
                },
              };
              if (!socket.connected) return;
              await DBController.addLiveRoom({
                roomId: data.roomId,
                socketId: socket.id,
                data: canonical,
                userId: Number(auth.userInfo.id),
                token,
              });
              socket.data.mediaSessionHash = mediaSessionHash(token || '');
              // Disconnect may occur while the database transaction is committing.
              if (!socket.connected) {
                await DBController.deleteLiveRoom(socket.id);
                return;
              }
            } else {
              const res = await DBController.searchLiveRoomByRoomId(
                data.roomId
              );
              socket.emit(
                res.count
                  ? WsMsgTypeEnum.roomLiveing
                  : WsMsgTypeEnum.roomNoLive,
                { roomId: data.roomId }
              );
            }
            await socket.join(data.roomId);
            const presenceToken = socketToken(socket);
            const presenceAuth = await authJwt({ req: { headers: { authorization: presenceToken ? `Bearer ${presenceToken}` : undefined } } });
            const presenceRoom = await DBController.searchLiveRoomByRoomId(data.roomId);
            if (socket.connected && presenceAuth.code === 200 && presenceAuth.userInfo?.id && presenceRoom.rows.length) {
              const raw = presenceRoom.rows[0].get('data');
              const info = typeof raw === 'string' ? JSON.parse(raw) : raw;
              roomPresence.join(socket.id, { roomId: data.roomId, userId: Number(presenceAuth.userInfo.id),
                username: presenceAuth.userInfo.username || '用户', hostId: Number(info?.data?.userId) });
            }
            socket.emit(WsMsgTypeEnum.joined, canonical);
            socket
              .to(data.roomId)
              .emit(WsMsgTypeEnum.otherJoin, { socketId: socket.id });
            io.emit(WsMsgTypeEnum.liveUser, await getAllLiveUser(io));
          } catch (error) {
            const known = [
              '直播间编号无效',
              '开播身份无效',
              '登录已失效或账号不可用，请重新登录',
              '不能使用其他主播的身份',
              '直播已结束或不属于当前账号',
              '账号不可用',
              '直播已在其他连接中进行，请先结束原直播',
              '当前连接已在其他直播间开播',
            ];
            const message =
              error instanceof Error && known.includes(error.message)
                ? error.message
                : '直播间暂时不可用，请稍后重试';
            socket.emit('joinRejected', { message });
          }
        })
    );

    // 收到用户退出房间
    socket.on(WsMsgTypeEnum.leave, () =>
      enqueue(async () => {
        try {
          await DBController.deleteLiveRoom(socket.id);
          roomPresence.leave(socket.id);
          for (const room of socket.rooms)
            if (room !== socket.id) await socket.leave(room);
          socket.emit(WsMsgTypeEnum.leaved, { socketId: socket.id });
          io.emit(WsMsgTypeEnum.liveUser, await getAllLiveUser(io));
        } catch {
          socket.emit('leaveRejected', {
            message: '退出未确认，请断开连接后重试',
          });
        }
      })
    );

    // Legacy file capture is not an upload API. Never write client-provided paths or blobs.
    socket.on(WsMsgTypeEnum.sendBlob, () => {
      socket.emit('eventRejected', {
        event: WsMsgTypeEnum.sendBlob,
        message: '不支持此上传方式',
      });
    });

    const eventRate = new RequestRateLimiter();
    const relay = async (event: WsMsgTypeEnum, data: any) => {
      try {
        if (
          !socket.connected ||
          !data ||
          typeof data.roomId !== 'string' ||
          !socket.rooms.has(data.roomId) ||
          data.roomId === socket.id ||
          !/^[1-9][0-9]{0,15}$/.test(data.roomId)
        )
          throw Error('membership');
        if (eventRate.consume('relay', 120)) throw Error('rate');
        if (Buffer.byteLength(JSON.stringify(data), 'utf8') > 32768)
          throw Error('size');
        const token = socketToken(socket);
        const auth = await authJwt({
          req: {
            headers: { authorization: token ? `Bearer ${token}` : undefined },
          },
        });
        if (auth.code !== 200 || !auth.userInfo?.id) throw Error('session');
        const room = await DBController.searchLiveRoomByRoomId(data.roomId);
        if (!room.count) throw Error('closed');
        if (!socket.connected || !socket.rooms.has(data.roomId))
          throw Error('membership');
        if (event === WsMsgTypeEnum.message) {
          const result = await persistChat(
            data.roomId,
            Number(auth.userInfo.id),
            token || '',
            data.data
          );
          const message: any = result.message.get();
          const payload = {
            roomId: data.roomId,
            socketId: socket.id,
            messageId: String(message.id),
            userId: Number(message.user_id),
            username: message.username,
            data: {
              text: message.text,
              msg: message.text,
              request_id: message.request_id,
              userId: Number(message.user_id),
              username: message.username,
            },
            created_at: message.created_at,
          };
          if (!result.duplicate && !message.hidden)
            socket.to(data.roomId).emit(event, payload);
          socket.emit('messageAccepted', {
            messageId: String(message.id),
            request_id: message.request_id,
            hidden: message.hidden,
          });
          return;
        }
        if (
          event === WsMsgTypeEnum.roomLiveing ||
          event === WsMsgTypeEnum.roomNoLive
        ) {
          if (!room.rows.some((row) => row.get().socketId === socket.id))
            throw Error('owner');
          socket.to(data.roomId).emit(event, { roomId: data.roomId });
          return;
        }
        socket.to(data.roomId).emit(event, {
          ...data,
          socketId: socket.id,
          userId: Number(auth.userInfo.id),
          username: auth.userInfo.username,
          data:
            data.data &&
            typeof data.data === 'object' &&
            !Array.isArray(data.data)
              ? {
                  ...data.data,
                  userId: Number(auth.userInfo.id),
                  username: auth.userInfo.username,
                }
              : data.data,
        });
      } catch {
        socket.emit('eventRejected', {
          event,
          message: '发送未通过，请检查登录、直播间或稍后重试',
        });
      }
    };
    for (const event of [
      WsMsgTypeEnum.message,
      WsMsgTypeEnum.offer,
      WsMsgTypeEnum.answer,
      WsMsgTypeEnum.candidate,
      WsMsgTypeEnum.roomLiveing,
      WsMsgTypeEnum.roomNoLive,
    ]) {
      socket.on(event, (data) => {
        void relay(event, data);
      });
    }

    socket.on(WsConnectStatusEnum.disconnect, () =>
      enqueue(async () => {
        roomPresence.leave(socket.id);
        try {
          await DBController.deleteLiveRoom(socket.id);
          io.emit(WsMsgTypeEnum.liveUser, await getAllLiveUser(io));
          io.emit(WsMsgTypeEnum.leaved, { socketId: socket.id });
        } catch {
          console.error('Live directory cleanup failed');
        }
      })
    );
  });
  return io;
};
