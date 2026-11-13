import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

const PlatformQualificationAudit = sequelize.define('platform_qualification_audit', {
  audit_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  request_id: { type: DataTypes.STRING(36), allowNull: false, unique: true },
  operator_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  reason: { type: DataTypes.STRING(200), allowNull: false },
  payload: { type: DataTypes.TEXT, allowNull: false },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: false });

export default PlatformQualificationAudit;
