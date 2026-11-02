CREATE TABLE IF NOT EXISTS account_action (
 action_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 request_id VARCHAR(36) NOT NULL UNIQUE,
 operator_id INT UNSIGNED NOT NULL,
 user_id INT UNSIGNED NOT NULL,
 disabled BOOLEAN NOT NULL,
 reason VARCHAR(200) NOT NULL,
 synced BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL,
 updated_at DATETIME NOT NULL,
 INDEX idx_account_action_sync(synced,action_id),
 INDEX idx_account_action_user(user_id,action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
