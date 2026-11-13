-- +goose Up
ALTER TABLE gift_orders
    ADD INDEX idx_gift_orders_viewer_status_created (viewer_id, status, created_at);

-- +goose Down
ALTER TABLE gift_orders DROP INDEX idx_gift_orders_viewer_status_created;
