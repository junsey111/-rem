-- Apply once to existing installations after 20261102_recharge_orders.sql.
ALTER TABLE recharge_order ADD COLUMN last_checked_at DATETIME NULL;
