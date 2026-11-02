import { DataTypes } from 'sequelize';
import sequelize from '@/config/mysql';

export const ChatMessage = sequelize.define(
  'room_chat_message',
  {
    id: {
      type: DataTypes.BIGINT.UNSIGNED,
      primaryKey: true,
      autoIncrement: true,
    },
    request_id: { type: DataTypes.STRING(36), allowNull: false, unique: true },
    room_id: { type: DataTypes.STRING(16), allowNull: false },
    user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    username: { type: DataTypes.STRING(100), allowNull: false },
    text: { type: DataTypes.STRING(1000), allowNull: false },
    hidden: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
  },
  {
    freezeTableName: true,
    createdAt: 'created_at',
    updatedAt: false,
    indexes: [{ fields: ['room_id', 'id'] }],
  }
);

export const ChatMute = sequelize.define(
  'room_chat_mute',
  {
    room_id: { type: DataTypes.STRING(16), primaryKey: true },
    user_id: { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true },
    muted_until: { type: DataTypes.DATE, allowNull: false },
  },
  { freezeTableName: true, timestamps: false }
);

export const ChatAction = sequelize.define(
  'room_chat_action',
  {
    id: {
      type: DataTypes.BIGINT.UNSIGNED,
      primaryKey: true,
      autoIncrement: true,
    },
    request_id: { type: DataTypes.STRING(36), allowNull: false, unique: true },
    operator_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    user_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    room_id: { type: DataTypes.STRING(16), allowNull: false },
    command: { type: DataTypes.JSON, allowNull: false },
  },
  {
    freezeTableName: true,
    createdAt: 'created_at',
    updatedAt: false,
    indexes: [{ fields: ['room_id', 'id'] }],
  }
);
