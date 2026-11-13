import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

const TaskConfig = sequelize.define('platform_task_config', {
  task_key: { type: DataTypes.STRING(64), primaryKey: true },
  category: { type: DataTypes.STRING(24), allowNull: false },
  title: { type: DataTypes.STRING(100), allowNull: false },
  description: { type: DataTypes.STRING(255), allowNull: false, defaultValue: '' },
  target_count: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  reward_coins: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  enabled: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
  updated_by: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: 'updated_at' });

export default TaskConfig;
