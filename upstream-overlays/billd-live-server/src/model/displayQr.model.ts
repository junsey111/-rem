import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export default sequelize.define('display_qr', {
  kind: { type: DataTypes.STRING(16), primaryKey: true },
  image: { type: DataTypes.BLOB('medium'), allowNull: false },
}, { freezeTableName: true, createdAt: false, updatedAt: 'updated_at' });
