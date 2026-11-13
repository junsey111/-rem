import { Op } from 'sequelize';

import { CustomError } from '@/model/customError.model';
import UserBlock from '@/model/userBlock.model';

/** All server initiated peer features use the same block policy. */
export async function assertPeerAccess(leftUserId: number, rightUserId: number) {
  if (!Number.isSafeInteger(leftUserId) || !Number.isSafeInteger(rightUserId) || leftUserId < 1 || rightUserId < 1)
    throw new CustomError('用户无效', 400, 400);
  if (leftUserId === rightUserId) throw new CustomError('不能与自己建立私信关系', 400, 400);
  const blocked = await UserBlock.findOne({ where: { [Op.or]: [
    { blocker_id: leftUserId, blocked_id: rightUserId },
    { blocker_id: rightUserId, blocked_id: leftUserId },
  ] } });
  if (blocked) throw new CustomError('该用户暂不可联系', 403, 403);
}
