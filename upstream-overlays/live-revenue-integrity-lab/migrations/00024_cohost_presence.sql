-- +goose Up
ALTER TABLE cohost_requests
 ADD COLUMN media_absent_since DATETIME(6) NULL,
 ADD COLUMN media_observed_at DATETIME(6) NULL;
-- +goose Down
ALTER TABLE cohost_requests DROP COLUMN media_absent_since, DROP COLUMN media_observed_at;
