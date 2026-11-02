-- +goose Up
CREATE TABLE moderation_actions (
    action_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id CHAR(36) NOT NULL UNIQUE,
    operator_id BIGINT UNSIGNED NOT NULL,
    live_session_id BIGINT UNSIGNED NOT NULL,
    reason VARCHAR(200) NOT NULL,
    applied BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_moderation_operator (operator_id, action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- +goose Down
DROP TABLE moderation_actions;
