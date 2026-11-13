import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export const UserNotification = sequelize.define('user_notification', {
  notification_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  kind: { type: DataTypes.STRING(24), allowNull: false },
  actor_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
  title: { type: DataTypes.STRING(120), allowNull: false },
  body: { type: DataTypes.STRING(240), allowNull: false, defaultValue: '' },
  read_at: { type: DataTypes.DATE, allowNull: true },
  created_at: { type: DataTypes.DATE, allowNull: false },
}, {
  freezeTableName: true,
  timestamps: false,
  indexes: [
    { unique: true, fields: ['user_id', 'kind', 'actor_id'] },
    { fields: ['user_id', 'notification_id'] },
  ],
});

export const UserPushToken = sequelize.define('user_push_token', {
  token_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  token: { type: DataTypes.STRING(512), allowNull: false, unique: true },
  platform: { type: DataTypes.STRING(16), allowNull: false },
  updated_at: { type: DataTypes.DATE, allowNull: false },
}, {
  freezeTableName: true,
  timestamps: false,
  indexes: [{ fields: ['user_id'] }],
});
