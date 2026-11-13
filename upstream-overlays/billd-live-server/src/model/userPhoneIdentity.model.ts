import { DataTypes } from 'sequelize';

import sequelize from '@/config/mysql';

export default sequelize.define('user_phone_identity', {
  user_id: { type: DataTypes.INTEGER, primaryKey: true, allowNull: false },
  phone_hash: { type: DataTypes.CHAR(64), allowNull: false },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
  indexes: [{ name: 'user_phone_identity_phone_hash', unique: true, fields: ['phone_hash'] }],
});
