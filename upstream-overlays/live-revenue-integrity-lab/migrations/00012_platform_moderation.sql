-- +goose Up
CREATE TABLE platform_account_state (
 user_id BIGINT UNSIGNED PRIMARY KEY,
 revision BIGINT UNSIGNED NOT NULL,
 disabled BOOLEAN NOT NULL,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
-- +goose Down
DROP TABLE platform_account_state;
