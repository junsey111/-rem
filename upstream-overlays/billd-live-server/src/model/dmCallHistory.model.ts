import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('dm_call_history', {
  call_id: { type: DataTypes.STRING(32), primaryKey: true },
  caller_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  peer_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  payload_json: { type: DataTypes.TEXT, allowNull: false },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: 'updated_at' });
