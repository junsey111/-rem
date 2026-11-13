import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('gift_icon', {
  gift_id: { type: DataTypes.STRING(32), primaryKey: true },
  image: { type: DataTypes.BLOB('medium'), allowNull: false },
}, { freezeTableName: true, createdAt: false, updatedAt: 'updated_at' });
