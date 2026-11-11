import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export const SecretaryFollow = sequelize.define('secretary_follow', {
  user_id: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: false });

export const PlatformContent = sequelize.define('platform_content', {
  content_key: { type: DataTypes.STRING(32), primaryKey: true },
  version: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false, defaultValue: 0 },
  value_json: { type: DataTypes.TEXT('long'), allowNull: false },
}, { freezeTableName: true, createdAt: false, updatedAt: 'updated_at' });

export const PlatformContentAction = sequelize.define('platform_content_action', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  request_id: { type: DataTypes.STRING(36), allowNull: false, unique: true },
  content_key: { type: DataTypes.STRING(32), allowNull: false },
  version: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  operator_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  reason: { type: DataTypes.STRING(200), allowNull: false },
  payload_hash: { type: DataTypes.STRING(64), allowNull: false },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: false });
