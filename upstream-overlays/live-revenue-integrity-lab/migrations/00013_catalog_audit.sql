-- +goose Up
CREATE TABLE gift_catalog_actions (
 action_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 request_id CHAR(36) NOT NULL UNIQUE,
 operator_id BIGINT UNSIGNED NOT NULL,
 gift_id VARCHAR(32) NOT NULL,
 command_json JSON NOT NULL,
 reason VARCHAR(200) NOT NULL,
 applied BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
-- +goose Down
DROP TABLE gift_catalog_actions;
