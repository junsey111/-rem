import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

const DailyCheckin = sequelize.define('user_daily_checkin', {
  user_id: { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true },
  checkin_date: { type: DataTypes.DATEONLY, primaryKey: true },
}, { freezeTableName: true, timestamps: false });

export default DailyCheckin;
