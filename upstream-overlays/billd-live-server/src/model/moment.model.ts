import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export const Moment = sequelize.define('user_moment', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  content: { type: DataTypes.STRING(500), allowNull: false },
  visible: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true },
  review_status: { type: DataTypes.STRING(16), allowNull: false, defaultValue: 'APPROVED' },
  moderation_reason: { type: DataTypes.STRING(200), allowNull: true },
  reviewed_by: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
  reviewed_at: { type: DataTypes.DATE, allowNull: true },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: false,
  indexes: [{ fields: ['visible', 'review_status', 'id'] }, { fields: ['user_id', 'visible', 'id'] }, { fields: ['review_status', 'id'] }],
});

export const MomentMedia = sequelize.define('user_moment_media', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  moment_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: true },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  storage_name: { type: DataTypes.STRING(64), allowNull: false, unique: true },
  content_type: { type: DataTypes.STRING(32), allowNull: false },
  byte_length: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  width: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  height: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  sha256: { type: DataTypes.STRING(64), allowNull: false },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: false,
  indexes: [{ fields: ['moment_id', 'id'] }, { fields: ['user_id', 'moment_id', 'id'] }],
});

export const MomentLike = sequelize.define('user_moment_like', {
  moment_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: false });

export const MomentComment = sequelize.define('user_moment_comment', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  moment_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  content: { type: DataTypes.STRING(500), allowNull: false },
  visible: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true },
  review_status: { type: DataTypes.STRING(16), allowNull: false, defaultValue: 'APPROVED' },
  moderation_reason: { type: DataTypes.STRING(200), allowNull: true },
  reviewed_by: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
  reviewed_at: { type: DataTypes.DATE, allowNull: true },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: false,
  indexes: [{ fields: ['moment_id', 'visible', 'id'] }, { fields: ['user_id', 'visible', 'id'] }, { fields: ['review_status', 'id'] }],
});
