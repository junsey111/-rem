import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('user_profile_visit', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  visitor_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  viewed_user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  last_seen_at: { type: DataTypes.DATE, allowNull: false },
}, {
  freezeTableName: true,
  timestamps: false,
  indexes: [
    { unique: true, fields: ['visitor_id', 'viewed_user_id'] },
    { fields: ['viewed_user_id', 'last_seen_at'] },
  ],
});
