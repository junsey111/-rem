import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

const PlatformQualification = sequelize.define('platform_qualification', {
  id: { type: DataTypes.TINYINT.UNSIGNED, primaryKey: true },
  platform_name: { type: DataTypes.STRING(120), allowNull: false, defaultValue: '' },
  subject_name: { type: DataTypes.STRING(160), allowNull: false, defaultValue: '' },
  registration_number: { type: DataTypes.STRING(64), allowNull: false, defaultValue: '' },
  office_address: { type: DataTypes.STRING(240), allowNull: false, defaultValue: '' },
  contact_email: { type: DataTypes.STRING(160), allowNull: false, defaultValue: '' },
  icp_record: { type: DataTypes.STRING(120), allowNull: false, defaultValue: '' },
  network_culture_license: { type: DataTypes.STRING(120), allowNull: false, defaultValue: '' },
  live_service_license: { type: DataTypes.STRING(120), allowNull: false, defaultValue: '' },
  published: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
  updated_by: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
}, {
  freezeTableName: true,
  createdAt: 'created_at',
  updatedAt: 'updated_at',
});

export default PlatformQualification;
