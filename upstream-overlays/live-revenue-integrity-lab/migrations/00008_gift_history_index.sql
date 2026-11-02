-- +goose Up
CREATE INDEX idx_gift_viewer_order ON gift_orders(viewer_id, gift_order_id);
-- +goose Down
DROP INDEX idx_gift_viewer_order ON gift_orders;
