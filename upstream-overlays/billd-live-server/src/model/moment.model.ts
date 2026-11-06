import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export const Moment = sequelize.define('user_moment', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  content: { type: DataTypes.STRING(500), allowNull: false },
  visible: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: false,
  indexes: [{ fields: ['visible', 'id'] }, { fields: ['user_id', 'visible', 'id'] }],
});

export const MomentLike = sequelize.define('user_moment_like', {
  moment_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: false });

export const MomentComment = sequelize.define('user_moment_comment', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  moment_id: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false },
  user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  content: { type: DataTypes.STRING(500), allowNull: false },
  visible: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: false,
  indexes: [{ fields: ['moment_id', 'visible', 'id'] }, { fields: ['user_id', 'visible', 'id'] }],
});
