import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export const CostumeItem = sequelize.define('platform_costume_item', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  category: { type: DataTypes.STRING(32), allowNull: false },
  name: { type: DataTypes.STRING(80), allowNull: false },
  description: { type: DataTypes.STRING(255), allowNull: false, defaultValue: '' },
  price_coins: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false, defaultValue: 0 },
  asset_url: { type: DataTypes.STRING(512), allowNull: false, defaultValue: '' },
  enabled: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
  updated_by: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [{ fields: ['category', 'enabled', 'id'] }],
});

export const UserCostume = sequelize.define('platform_user_costume', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  costume_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  equipped: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
  acquired_at: { type: DataTypes.DATE, allowNull: false },
  purchase_request_id: { type: DataTypes.STRING(64), allowNull: true, unique: true },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [{ unique: true, fields: ['user_id', 'costume_id'] }, { fields: ['user_id', 'equipped'] }],
});
