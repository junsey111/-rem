import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('user_social_profile', {
  user_id: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
  height_cm: { type: DataTypes.SMALLINT.UNSIGNED, allowNull: true },
  weight_kg: { type: DataTypes.SMALLINT.UNSIGNED, allowNull: true },
  occupation: { type: DataTypes.STRING(50), allowNull: true },
  annual_income: { type: DataTypes.ENUM('UNDER_50K', '50K_100K', '100K_200K', '200K_500K', 'OVER_500K'), allowNull: true },
  city: { type: DataTypes.STRING(60), allowNull: true },
  marital_status: { type: DataTypes.ENUM('SINGLE', 'IN_RELATIONSHIP', 'MARRIED', 'DIVORCED', 'WIDOWED'), allowNull: true },
  tags_json: { type: DataTypes.TEXT, allowNull: true },
  dating_preferences: { type: DataTypes.STRING(300), allowNull: true },
  is_public: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [{ fields: ['is_public', 'updated_at'] }],
});
