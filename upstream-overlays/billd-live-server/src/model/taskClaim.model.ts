import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

const TaskClaim = sequelize.define('platform_task_claim', {
  claim_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  task_key: { type: DataTypes.STRING(64), allowNull: false },
  task_date: { type: DataTypes.DATEONLY, allowNull: false },
  request_id: { type: DataTypes.STRING(64), allowNull: false },
  reward_coins: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  tx_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  new_balance: { type: DataTypes.BIGINT, allowNull: false },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: false,
  indexes: [{ unique: true, fields: ['user_id', 'task_key', 'task_date'] }, { unique: true, fields: ['user_id', 'request_id'] }],
});

export default TaskClaim;
