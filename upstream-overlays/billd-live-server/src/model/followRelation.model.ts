import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('user_follow_relation', {
  id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
  follower_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
  followed_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: false,
  indexes: [
    { unique: true, fields: ['follower_id', 'followed_id'] },
    { fields: ['follower_id', 'id'] },
    { fields: ['followed_id', 'id'] },
  ],
});
