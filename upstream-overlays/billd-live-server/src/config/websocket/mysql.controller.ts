import sequelize from '@/config/mysql';
import liveModel from '@/model/live.model';

class DBController {
  /** 新增一个在线房间 */
  addLiveRoom = async ({ roomId, socketId, data, userId, token }) => {
    return sequelize.transaction(async transaction => {
      // The existing account row serializes registrations for this owner across processes.
      const [users]: any = await sequelize.query('SELECT id FROM user WHERE id=? AND token=? AND deleted_at IS NULL AND status<>2 FOR UPDATE', { replacements: [userId, token], transaction });
      if (!users.length) throw new Error('账号不可用');
      const existing: any = await liveModel.findOne({ where: { roomId }, transaction, lock: transaction.LOCK.UPDATE });
      if (existing) {
        if (existing.socketId !== socketId) throw new Error('直播已在其他连接中进行，请先结束原直播');
        return existing.update({ data: JSON.stringify(data) }, { transaction });
      }
      const ownRoom = await liveModel.findOne({ where: { socketId }, transaction });
      if (ownRoom) throw new Error('当前连接已在其他直播间开播');
      return liveModel.create({ roomId, socketId, data: JSON.stringify(data) }, { transaction });
    });
  };

  /** 删除一个在线房间 */
  deleteLiveRoom = async (socketId: string) => {
    const res = await liveModel.destroy({ where: { socketId } });
    return res;
  };

  /** 获取所有在线房间 */
  getAllLiveRoom = async () => {
    const res = await liveModel.findAndCountAll();
    return res;
  };

  /** 获取所有在线房间 */
  searchLiveRoomBySocketId = async (socketId: string) => {
    const res = await liveModel.findAndCountAll({ where: { socketId } });
    return res;
  };

  /** 获取所有在线房间 */
  searchLiveRoomByRoomId = async (roomId: string) => {
    const res = await liveModel.findAndCountAll({ where: { roomId } });
    return res;
  };
}

export default new DBController();
