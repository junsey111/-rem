import { randomUUID } from 'crypto';
import { EventEmitter } from 'events';
import { QueryTypes } from 'sequelize';
import sequelize from '@/config/mysql';
import Live from '@/model/live.model';
import { ChatMessage, ChatMute, ChatAction } from '@/model/roomChat.model';
import { CustomError } from '@/model/customError.model';

export const chatEvents = new EventEmitter();
const uuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
export function chatRoom(value: any): string {
  if (
    typeof value !== 'string' ||
    !/^[1-9][0-9]{0,15}$/.test(value) ||
    !Number.isSafeInteger(Number(value))
  )
    throw new CustomError('直播间编号无效', 400, 400);
  return value;
}
export async function persistChat(
  roomId: string,
  userId: number,
  token: string,
  body: any
) {
  chatRoom(roomId);
  const text = body?.text ?? body?.msg;
  const requestId = body?.request_id ?? randomUUID();
  if (
    typeof text !== 'string' ||
    !text.trim() ||
    Array.from(text).length > 500 ||
    !uuid.test(requestId)
  )
    throw new CustomError('消息内容或单号无效', 400, 400);
  return sequelize.transaction(async (transaction) => {
    const users: any[] = await sequelize.query(
      'SELECT id,username FROM user WHERE id=? AND token=? AND status=1 AND deleted_at IS NULL FOR UPDATE',
      { replacements: [userId, token], type: QueryTypes.SELECT, transaction }
    );
    if (!users.length) throw new CustomError('登录已失效', 401, 401);
    const previous: any = await ChatMessage.findOne({
      where: { request_id: requestId },
      transaction,
    });
    if (previous) {
      if (
        Number(previous.user_id) !== userId ||
        previous.room_id !== roomId ||
        previous.text !== text
      )
        throw new CustomError('消息单号冲突', 409, 409);
      return { message: previous, duplicate: true };
    }
    const live = await Live.findOne({
      where: { roomId },
      transaction,
      lock: transaction.LOCK.SHARE,
    });
    if (!live) throw new CustomError('直播已结束', 409, 409);
    const mute: any = await ChatMute.findOne({
      where: { room_id: roomId, user_id: userId },
      transaction,
    });
    if (mute && new Date(mute.muted_until).getTime() > Date.now())
      throw new CustomError('当前处于禁言中', 403, 403);
    const message = await ChatMessage.create(
      {
        request_id: requestId,
        room_id: roomId,
        user_id: userId,
        username: users[0].username,
        text,
      },
      { transaction }
    );
    return { message, duplicate: false };
  });
}

export async function moderateChat(
  operatorId: number,
  token: string,
  roomId: string,
  userId: number,
  command: any
) {
  chatRoom(roomId);
  if (
    !Number.isSafeInteger(userId) ||
    userId < 1 ||
    !uuid.test(command.request_id) ||
    typeof command.reason !== 'string' ||
    !command.reason.trim() ||
    Array.from(command.reason).length > 200
  )
    throw new CustomError('账号、操作单号或原因无效', 400, 400);
  if (!['MUTE', 'UNMUTE', 'HIDE'].includes(command.action))
    throw new CustomError('操作无效', 400, 400);
  if (
    command.action === 'MUTE' &&
    (!Number.isInteger(command.minutes) ||
      command.minutes < 1 ||
      command.minutes > 10080)
  )
    throw new CustomError('禁言时长为 1 到 10080 分钟', 400, 400);
  if (
    command.action === 'HIDE' &&
    !/^[1-9][0-9]{0,19}$/.test(String(command.message_id))
  )
    throw new CustomError('消息编号无效', 400, 400);
  const normalized = {
    action: command.action,
    reason: command.reason,
    minutes: command.action === 'MUTE' ? command.minutes : null,
    message_id: command.action === 'HIDE' ? String(command.message_id) : null,
  };
  const result = await sequelize.transaction(async (transaction) => {
    const users: any[] = await sequelize.query(
      'SELECT id,token,status FROM user WHERE id IN (?,?) AND deleted_at IS NULL ORDER BY id FOR UPDATE',
      {
        replacements: [operatorId, userId],
        type: QueryTypes.SELECT,
        transaction,
      }
    );
    if (!users.some((user) => Number(user.id) === userId))
      throw new CustomError('账号不存在', 404, 404);
    if (
      !users.some(
        (user) =>
          Number(user.id) === operatorId &&
          user.status === 1 &&
          user.token === token
      )
    )
      throw new CustomError('登录已失效', 401, 401);
    const roles: any[] = await sequelize.query(
      'SELECT r.role_value FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=? AND ur.deleted_at IS NULL AND r.deleted_at IS NULL FOR SHARE',
      { replacements: [operatorId], type: QueryTypes.SELECT, transaction }
    );
    if (!roles.some((role) => role.role_value === 'SUPER_ADMIN'))
      throw new CustomError('无运营权限', 403, 403);
    const prior: any = await ChatAction.findOne({
      where: { request_id: command.request_id },
      transaction,
    });
    if (prior) {
      const old =
        typeof prior.command === 'string'
          ? JSON.parse(prior.command)
          : prior.command;
      if (
        Number(prior.operator_id) !== operatorId ||
        Number(prior.user_id) !== userId ||
        prior.room_id !== roomId ||
        Object.keys(normalized).some((key) => normalized[key] !== old[key])
      )
        throw new CustomError('操作单号冲突', 409, 409);
      return { record: prior, duplicate: true };
    }
    if (command.action === 'HIDE') {
      const message: any = await ChatMessage.findOne({
        where: { id: normalized.message_id, room_id: roomId, user_id: userId },
        transaction,
        lock: transaction.LOCK.UPDATE,
      });
      if (!message) throw new CustomError('消息不存在', 404, 404);
      await message.update({ hidden: true }, { transaction });
    } else {
      await ChatMute.upsert(
        {
          room_id: roomId,
          user_id: userId,
          muted_until: new Date(
            command.action === 'MUTE' ? Date.now() + command.minutes * 60000 : 0
          ),
        },
        { transaction }
      );
    }
    const record = await ChatAction.create(
      {
        request_id: command.request_id,
        operator_id: operatorId,
        user_id: userId,
        room_id: roomId,
        command: normalized,
      },
      { transaction }
    );
    return { record, duplicate: false };
  });
  if (!result.duplicate)
    chatEvents.emit('moderation', {
      roomId,
      userId,
      action: normalized.action,
      messageId: normalized.message_id,
    });
  return result.record;
}
