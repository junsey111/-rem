CREATE TABLE IF NOT EXISTS room_chat_message (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id CHAR(36) NOT NULL UNIQUE,
  room_id VARCHAR(16) NOT NULL,
  user_id INT UNSIGNED NOT NULL,
  username VARCHAR(100) NOT NULL,
  text VARCHAR(1000) NOT NULL,
  hidden TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY room_chat_message_room_id_id (room_id,id)
);
CREATE TABLE IF NOT EXISTS room_chat_mute (
  room_id VARCHAR(16) NOT NULL,
  user_id INT UNSIGNED NOT NULL,
  muted_until DATETIME NOT NULL,
  PRIMARY KEY (room_id,user_id)
);
CREATE TABLE IF NOT EXISTS room_chat_action (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id CHAR(36) NOT NULL UNIQUE,
  operator_id INT UNSIGNED NOT NULL,
  user_id INT UNSIGNED NOT NULL,
  room_id VARCHAR(16) NOT NULL,
  command JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY room_chat_action_room_id_id (room_id,id)
);
