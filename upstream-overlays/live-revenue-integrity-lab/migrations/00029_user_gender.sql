-- +goose Up
ALTER TABLE users ADD COLUMN gender ENUM('MALE','FEMALE') NULL;

-- +goose Down
ALTER TABLE users DROP COLUMN gender;
