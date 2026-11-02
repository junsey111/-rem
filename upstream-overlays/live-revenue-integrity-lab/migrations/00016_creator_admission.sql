-- +goose Up
CREATE TABLE creator_admission_settings (
 singleton TINYINT PRIMARY KEY,
 version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 require_approval BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO creator_admission_settings(singleton) VALUES(1);
CREATE TABLE creator_admission_actions (
 request_id CHAR(36) PRIMARY KEY,
 operator_id BIGINT UNSIGNED NOT NULL,
 require_approval BOOLEAN NOT NULL,
 reason VARCHAR(200) NOT NULL,
 version BIGINT UNSIGNED NOT NULL UNIQUE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
-- +goose Down
DROP TABLE creator_admission_actions;
DROP TABLE creator_admission_settings;
