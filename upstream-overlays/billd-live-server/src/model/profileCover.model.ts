import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('profile_cover', {
  user_id: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
  image: { type: DataTypes.BLOB('medium'), allowNull: false },
}, { freezeTableName: true, createdAt: false, updatedAt: 'updated_at' });
