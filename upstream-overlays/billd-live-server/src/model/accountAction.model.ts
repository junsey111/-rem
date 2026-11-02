import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';
export default sequelize.define('account_action',{
 action_id:{type:DataTypes.BIGINT.UNSIGNED,primaryKey:true,autoIncrement:true},
 request_id:{type:DataTypes.STRING(36),allowNull:false,unique:true},
 operator_id:{type:DataTypes.INTEGER.UNSIGNED,allowNull:false},
 user_id:{type:DataTypes.INTEGER.UNSIGNED,allowNull:false},
 disabled:{type:DataTypes.BOOLEAN,allowNull:false},
 reason:{type:DataTypes.STRING(200),allowNull:false},
 synced:{type:DataTypes.BOOLEAN,allowNull:false,defaultValue:false},
 },{freezeTableName:true,createdAt:'created_at',updatedAt:'updated_at',indexes:[{fields:['synced','action_id']},{fields:['user_id','action_id']}]});
