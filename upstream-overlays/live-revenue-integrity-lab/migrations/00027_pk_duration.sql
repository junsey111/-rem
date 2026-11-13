-- +goose Up
ALTER TABLE live_match_invitations ADD COLUMN duration_minutes TINYINT UNSIGNED NOT NULL DEFAULT 5;
ALTER TABLE live_matches
    ADD COLUMN duration_minutes TINYINT UNSIGNED NOT NULL DEFAULT 5,
    ADD COLUMN started_at DATETIME(6) NULL,
    ADD COLUMN ends_at DATETIME(6) NULL;

-- +goose Down
ALTER TABLE live_matches
    DROP COLUMN ends_at,
    DROP COLUMN started_at,
    DROP COLUMN duration_minutes;
ALTER TABLE live_match_invitations DROP COLUMN duration_minutes;
