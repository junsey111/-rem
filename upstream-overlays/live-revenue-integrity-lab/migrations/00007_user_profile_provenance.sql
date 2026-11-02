-- +goose Up
ALTER TABLE users
    MODIFY age_years TINYINT UNSIGNED NULL,
    MODIFY region_code CHAR(2) NULL,
    ADD COLUMN profile_source ENUM('UNKNOWN','SELF_REPORTED') NOT NULL DEFAULT 'UNKNOWN';
-- Existing values have no reliable provenance. Retain them for audit, never present
-- or use them as declared facts until the account holder supplies their profile.

-- +goose Down
ALTER TABLE users DROP COLUMN profile_source;
