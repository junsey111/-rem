-- +goose Up
ALTER TABLE live_sessions
    ADD INDEX idx_live_sessions_creator_started (creator_id, started_at);

-- +goose Down
ALTER TABLE live_sessions DROP INDEX idx_live_sessions_creator_started;
