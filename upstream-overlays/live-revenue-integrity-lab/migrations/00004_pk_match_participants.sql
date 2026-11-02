-- +goose Up
SET @add_creator = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='live_matches' AND COLUMN_NAME='creator_id'), 'SELECT 1', 'ALTER TABLE live_matches ADD COLUMN creator_id BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER live_session_id');
PREPARE stmt_add_creator FROM @add_creator;
EXECUTE stmt_add_creator;
DEALLOCATE PREPARE stmt_add_creator;

SET @add_opponent_session = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='live_matches' AND COLUMN_NAME='opponent_live_session_id'), 'SELECT 1', 'ALTER TABLE live_matches ADD COLUMN opponent_live_session_id BIGINT UNSIGNED NULL AFTER creator_id');
PREPARE stmt_add_opponent_session FROM @add_opponent_session;
EXECUTE stmt_add_opponent_session;
DEALLOCATE PREPARE stmt_add_opponent_session;

SET @add_opponent_creator = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='live_matches' AND COLUMN_NAME='opponent_creator_id'), 'SELECT 1', 'ALTER TABLE live_matches ADD COLUMN opponent_creator_id BIGINT UNSIGNED NULL AFTER opponent_live_session_id');
PREPARE stmt_add_opponent_creator FROM @add_opponent_creator;
EXECUTE stmt_add_opponent_creator;
DEALLOCATE PREPARE stmt_add_opponent_creator;

UPDATE live_matches m JOIN live_sessions s ON s.live_session_id=m.live_session_id
SET m.creator_id=s.creator_id WHERE m.creator_id=0;

CREATE TABLE IF NOT EXISTS live_match_invitations (
    invitation_id BIGINT UNSIGNED PRIMARY KEY,
    match_id BIGINT UNSIGNED NOT NULL,
    live_session_id BIGINT UNSIGNED NOT NULL,
    creator_id BIGINT UNSIGNED NOT NULL,
    opponent_live_session_id BIGINT UNSIGNED NOT NULL,
    opponent_creator_id BIGINT UNSIGNED NOT NULL,
    mode ENUM('ALL_GIFTS','SPECIFIC_GIFT') NOT NULL,
    specific_gift_id VARCHAR(32) NULL,
    status ENUM('PENDING','ACCEPTED','REJECTED','CANCELLED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    responded_at DATETIME(6) NULL,
    INDEX idx_match_invites_inbox (opponent_creator_id, status, created_at),
    INDEX idx_match_invites_creator (creator_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- +goose Down
DROP TABLE IF EXISTS live_match_invitations;
ALTER TABLE live_matches
    DROP COLUMN creator_id,
    DROP COLUMN opponent_creator_id,
    DROP COLUMN opponent_live_session_id;
