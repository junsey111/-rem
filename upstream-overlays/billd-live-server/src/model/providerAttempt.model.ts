import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';
export default sequelize.define(
  'provider_attempt',
  {
    business_key: { type: DataTypes.STRING(80), primaryKey: true },
    operator_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    request_id: { type: DataTypes.STRING(36), allowNull: false, unique: true },
    command_hash: { type: DataTypes.STRING(64), allowNull: false },
    reason: { type: DataTypes.STRING(200), allowNull: false },
    status: {
      type: DataTypes.STRING(20),
      allowNull: false,
      defaultValue: 'PENDING',
    },
    provider_ref: { type: DataTypes.STRING(128), allowNull: true },
  },
  { freezeTableName: true, createdAt: 'created_at', updatedAt: 'updated_at' }
);
