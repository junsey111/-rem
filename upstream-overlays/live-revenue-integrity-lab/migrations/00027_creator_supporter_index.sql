-- +goose Up
CREATE INDEX idx_gift_creator_recent ON gift_orders(creator_id, status, created_at, viewer_id);

-- +goose Down
DROP INDEX idx_gift_creator_recent ON gift_orders;
