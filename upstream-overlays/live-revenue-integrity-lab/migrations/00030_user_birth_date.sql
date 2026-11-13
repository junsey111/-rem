-- +goose Up
ALTER TABLE users ADD COLUMN birth_date DATE NULL AFTER gender;

-- +goose Down
ALTER TABLE users DROP COLUMN birth_date;
