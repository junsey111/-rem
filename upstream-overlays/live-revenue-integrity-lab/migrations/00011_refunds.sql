-- +goose Up
ALTER TABLE wallet_transactions MODIFY tx_type ENUM('RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE') NOT NULL;
CREATE TABLE refunds (
    refund_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    request_id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    recharge_request_id VARCHAR(64) NOT NULL UNIQUE,
    coins BIGINT NOT NULL,
    amount_fen BIGINT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    settings_version BIGINT UNSIGNED NOT NULL,
    consumed_allowed BOOLEAN NOT NULL,
    status ENUM('PENDING','APPROVED','PAID','REJECTED') NOT NULL DEFAULT 'PENDING',
    payment_ref VARCHAR(128) NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_refund_request(user_id,request_id),
    INDEX idx_refund_user(user_id,refund_id),
    CONSTRAINT fk_refund_user FOREIGN KEY(user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;
CREATE TABLE refund_actions (
    request_id CHAR(36) PRIMARY KEY,
    refund_id BIGINT UNSIGNED NOT NULL,
    operator_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    payment_ref VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_refund_action FOREIGN KEY(refund_id) REFERENCES refunds(refund_id)
) ENGINE=InnoDB;
-- +goose Down
DROP TABLE refund_actions;
DROP TABLE refunds;
-- Financial ledger enum values are retained to preserve historical postings.
