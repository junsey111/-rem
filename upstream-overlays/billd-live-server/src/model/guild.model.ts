import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export const Guild = sequelize.define('platform_guild', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  owner_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  name: { type: DataTypes.STRING(40), allowNull: false },
  description: { type: DataTypes.STRING(255), allowNull: false, defaultValue: '' },
  status: { type: DataTypes.ENUM('ACTIVE', 'SUSPENDED', 'CLOSED'), allowNull: false, defaultValue: 'ACTIVE' },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [{ fields: ['status', 'id'] }, { fields: ['owner_id', 'status'] }],
});

export const GuildMember = sequelize.define('platform_guild_member', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  guild_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  role: { type: DataTypes.ENUM('OWNER', 'ADMIN', 'MEMBER'), allowNull: false, defaultValue: 'MEMBER' },
  status: { type: DataTypes.ENUM('ACTIVE', 'LEFT'), allowNull: false, defaultValue: 'ACTIVE' },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [{ unique: true, fields: ['guild_id', 'user_id'] }, { fields: ['user_id', 'status'] }, { fields: ['guild_id', 'status'] }],
});

export const GuildApplication = sequelize.define('platform_guild_application', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  guild_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  message: { type: DataTypes.STRING(255), allowNull: false, defaultValue: '' },
  status: { type: DataTypes.ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'), allowNull: false, defaultValue: 'PENDING' },
  decided_by: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
  decided_at: { type: DataTypes.DATE, allowNull: true },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [{ unique: true, fields: ['guild_id', 'user_id'] }, { fields: ['guild_id', 'status', 'id'] }],
});
