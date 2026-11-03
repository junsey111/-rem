-- +goose Up
ALTER TABLE live_watch_progress ADD COLUMN preview_started_at DATETIME(6) NULL;

-- +goose Down
ALTER TABLE live_watch_progress DROP COLUMN preview_started_at;
