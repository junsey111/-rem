import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

// Rows predating review have no record and remain approved. New submissions create
// a PENDING record in the same transaction as their text and media.
const MomentReview = sequelize.define('moment_review', {
  moment_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true },
  status: { type: DataTypes.STRING(16), allowNull: false, defaultValue: 'PENDING' },
  version: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false, defaultValue: 1 },
  reason: { type: DataTypes.STRING(200), allowNull: false, defaultValue: '' },
  operator_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
  reviewed_at: { type: DataTypes.DATE, allowNull: true },
}, { freezeTableName: true, timestamps: false, indexes: [{ fields: ['status', 'moment_id'] }] });

export const MomentReviewAction = sequelize.define('moment_review_action', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  request_id: { type: DataTypes.STRING(36), allowNull: false, unique: true },
  moment_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  operator_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  decision: { type: DataTypes.STRING(16), allowNull: false },
  from_version: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  reason: { type: DataTypes.STRING(200), allowNull: false },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: false, indexes: [{ fields: ['moment_id', 'id'] }] });

export default MomentReview;
