import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

const ReportEvidence = sequelize.define('report_evidence', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
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
  indexes: [{ fields: ['user_id', 'created_at'] }],
});

export default ReportEvidence;
