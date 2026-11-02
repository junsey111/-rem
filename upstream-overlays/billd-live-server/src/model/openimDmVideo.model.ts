import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

/** Video bytes stay on disk. This row is metadata only; price is what Revenue must charge. */
export const DmVideo = sequelize.define('openim_dm_video', {
  video_id: { type: DataTypes.STRING(36), primaryKey: true },
  request_id: { type: DataTypes.STRING(36), allowNull: false },
  owner_user_id: { type: DataTypes.INTEGER, allowNull: false },
  recipient_user_id: { type: DataTypes.INTEGER, allowNull: false },
  title: { type: DataTypes.STRING(40), allowNull: false },
  price_coins: { type: DataTypes.INTEGER, allowNull: false },
  duration_sec: { type: DataTypes.INTEGER, allowNull: false },
  content_type: { type: DataTypes.STRING(32), allowNull: false },
  byte_length: { type: DataTypes.INTEGER, allowNull: false },
  sha256: { type: DataTypes.STRING(64), allowNull: false },
  storage_name: { type: DataTypes.STRING(36), allowNull: false },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [{ unique: true, fields: ['owner_user_id', 'request_id'] }],
});

/** Cache of a Revenue unlock acceptance. Playback still honors an explicit Revenue denial. */
export const DmVideoUnlock = sequelize.define('openim_dm_video_unlock', {
  video_id: { type: DataTypes.STRING(36), primaryKey: true },
  viewer_user_id: { type: DataTypes.INTEGER, primaryKey: true },
  request_id: { type: DataTypes.STRING(36), allowNull: false },
  charged_coins: { type: DataTypes.INTEGER, allowNull: false },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: 'updated_at' });
