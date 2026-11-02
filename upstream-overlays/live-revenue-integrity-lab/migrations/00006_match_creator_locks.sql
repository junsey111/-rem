-- +goose Up
-- Transaction mutexes are separate from user/FK rows used by wallet writes.
CREATE TABLE live_match_creator_locks (
    creator_id BIGINT UNSIGNED PRIMARY KEY
) ENGINE=InnoDB;

-- +goose Down
DROP TABLE live_match_creator_locks;
