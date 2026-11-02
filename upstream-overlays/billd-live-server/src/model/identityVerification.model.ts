import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';
export default sequelize.define('identity_verification',{
 request_id:{type:DataTypes.STRING(36),primaryKey:true},
 user_id:{type:DataTypes.INTEGER.UNSIGNED,allowNull:false},
 status:{type:DataTypes.STRING(16),allowNull:false,defaultValue:'PENDING'},
 provider_reference:{type:DataTypes.STRING(128),allowNull:true},
 age_years:{type:DataTypes.INTEGER.UNSIGNED,allowNull:true},
 region_code:{type:DataTypes.STRING(2),allowNull:true},
},{freezeTableName:true,createdAt:'created_at',updatedAt:'updated_at',indexes:[{fields:['user_id','created_at']}]});
