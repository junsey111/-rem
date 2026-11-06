import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('user_notification_preference', {
  user_id: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
  messages: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true },
  visitors: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true },
  announcements: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true },
  marketing: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
  updated_at: { type: DataTypes.DATE, allowNull: false },
}, {
  freezeTableName: true,
  timestamps: false,
});
