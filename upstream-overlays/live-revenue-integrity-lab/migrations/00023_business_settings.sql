-- +goose Up
CREATE TABLE referral_settings (
 singleton TINYINT PRIMARY KEY,
 version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 configured BOOLEAN NOT NULL DEFAULT FALSE,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 inviter_coins BIGINT NOT NULL DEFAULT 0,
 invitee_coins BIGINT NOT NULL DEFAULT 0
);
INSERT INTO referral_settings(singleton) VALUES(1);
ALTER TABLE live_watch_prices ADD COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0;
CREATE TABLE business_setting_actions (
 action_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 request_id CHAR(36) NOT NULL UNIQUE,
 operator_id BIGINT UNSIGNED NOT NULL,
 kind VARCHAR(20) NOT NULL,
 target_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
 body_hash BINARY(32) NOT NULL,
 settings_json JSON NOT NULL,
 reason VARCHAR(200) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
-- +goose Down
DROP TABLE business_setting_actions;
ALTER TABLE live_watch_prices DROP COLUMN version;
DROP TABLE referral_settings;
