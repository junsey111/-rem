import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

const rechargeOrder = sequelize.define('recharge_order', {
  request_id: { type: DataTypes.STRING(64), primaryKey: true, allowNull: false },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  coins: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  amount_fen: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  status: { type: DataTypes.ENUM('PENDING', 'CREDITED'), allowNull: false, defaultValue: 'PENDING' },
  payment_ref: { type: DataTypes.STRING(128), allowNull: true, unique: true },
  last_checked_at: { type: DataTypes.DATE, allowNull: true },
}, {
  freezeTableName: true, createdAt: 'created_at', updatedAt: 'updated_at',
  indexes: [{ fields: ['user_id', 'created_at'] }],
});
export default rechargeOrder;
