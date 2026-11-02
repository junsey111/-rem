import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

// Credentials are internal only; never include this model in public user responses.
export default sequelize.define('openim_session', {
  user_id: { type: DataTypes.INTEGER, primaryKey: true },
  platform_id: { type: DataTypes.INTEGER, primaryKey: true },
  session_hash: { type: DataTypes.STRING(64), allowNull: true },
  im_token: { type: DataTypes.TEXT, allowNull: true },
  expires_at: { type: DataTypes.BIGINT, allowNull: false, defaultValue: 0 },
  pending: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true },
}, { freezeTableName: true, createdAt: 'created_at', updatedAt: 'updated_at' });
