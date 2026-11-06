import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('user_close_friend_relation', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  user_low_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  user_high_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  requested_by: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  status: { type: DataTypes.ENUM('pending', 'accepted'), allowNull: false, defaultValue: 'pending' },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [
    { unique: true, fields: ['user_low_id', 'user_high_id'] },
    { fields: ['user_low_id', 'status'] },
    { fields: ['user_high_id', 'status'] },
  ],
});
