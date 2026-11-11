import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('moment_moderation', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  request_id: { type: DataTypes.STRING(36), allowNull: false, unique: true },
  moment_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  operator_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  reason: { type: DataTypes.STRING(200), allowNull: false },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: false });
