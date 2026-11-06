import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('user_block_relation', {
  blocker_id: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
  blocked_id: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
  created_at: { type: DataTypes.DATE, allowNull: false },
}, {
  freezeTableName: true,
  timestamps: false,
  indexes: [{ fields: ['blocked_id', 'created_at'] }],
});
