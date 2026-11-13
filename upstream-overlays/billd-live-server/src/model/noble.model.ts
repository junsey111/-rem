import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export const NobleLevel = sequelize.define('platform_noble_level', {
  level: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
  name: { type: DataTypes.STRING(40), allowNull: false },
  description: { type: DataTypes.STRING(255), allowNull: false, defaultValue: '' },
  price_coins: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false, defaultValue: 0 },
  duration_days: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false, defaultValue: 30 },
  enabled: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
  updated_by: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: 'updated_at' });

export const NobleMembership = sequelize.define('platform_noble_membership', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  level: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  status: { type: DataTypes.ENUM('ACTIVE', 'EXPIRED', 'REVOKED'), allowNull: false, defaultValue: 'ACTIVE' },
  starts_at: { type: DataTypes.DATE, allowNull: false },
  expires_at: { type: DataTypes.DATE, allowNull: true },
  granted_by: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
  purchase_request_id: { type: DataTypes.STRING(64), allowNull: true, unique: true },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [{ fields: ['user_id', 'status', 'expires_at'] }, { fields: ['level', 'status'] }],
});
