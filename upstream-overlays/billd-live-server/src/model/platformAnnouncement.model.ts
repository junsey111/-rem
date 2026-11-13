import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export const PlatformAnnouncement = sequelize.define('platform_announcement', {
  announcement_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  title: { type: DataTypes.STRING(120), allowNull: false },
  summary: { type: DataTypes.STRING(240), allowNull: false, defaultValue: '' },
  content: { type: DataTypes.TEXT, allowNull: false },
  enabled: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
  created_by: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  updated_by: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [{ fields: ['enabled', 'announcement_id'] }],
});

export const PlatformAnnouncementAudit = sequelize.define('platform_announcement_audit', {
  audit_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  request_id: { type: DataTypes.STRING(36), allowNull: false, unique: true },
  announcement_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: true },
  operator_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  action: { type: DataTypes.STRING(16), allowNull: false },
  reason: { type: DataTypes.STRING(200), allowNull: false },
  payload: { type: DataTypes.TEXT, allowNull: false },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: false });

export const UserAnnouncementRead = sequelize.define('user_announcement_read', {
  user_id: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
  announcement_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true },
  read_at: { type: DataTypes.DATE, allowNull: false },
}, { freezeTableName: true, timestamps: false });
