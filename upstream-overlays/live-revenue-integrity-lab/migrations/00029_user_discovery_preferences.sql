-- +goose Up
ALTER TABLE users
    ADD COLUMN personalized_recommendations BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN hide_guardian BOOLEAN NOT NULL DEFAULT FALSE;

-- +goose Down
ALTER TABLE users
    DROP COLUMN hide_guardian,
    DROP COLUMN personalized_recommendations;
