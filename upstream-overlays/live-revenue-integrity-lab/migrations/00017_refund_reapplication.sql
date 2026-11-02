-- +goose Up
ALTER TABLE refunds DROP INDEX recharge_request_id,
 ADD COLUMN active_recharge_request_id VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN status='REJECTED' THEN NULL ELSE recharge_request_id END) STORED,
 ADD UNIQUE KEY uq_active_refund_recharge(active_recharge_request_id),
 ADD INDEX idx_refund_recharge(recharge_request_id);
-- +goose Down
-- Refuse rollback if there are multiple historical applications for one recharge.
ALTER TABLE refunds ADD UNIQUE KEY recharge_request_id(recharge_request_id),
 DROP INDEX uq_active_refund_recharge, DROP INDEX idx_refund_recharge,
 DROP COLUMN active_recharge_request_id;
