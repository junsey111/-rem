import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('platform_content_image', {
  id: { type: DataTypes.STRING(36), primaryKey: true },
  operator_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  image: { type: DataTypes.BLOB('medium'), allowNull: false },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: false });
