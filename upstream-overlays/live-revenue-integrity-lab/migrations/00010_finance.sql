-- +goose Up
CREATE TABLE finance_settings (
    singleton TINYINT UNSIGNED PRIMARY KEY,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    rules_json JSON NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
INSERT INTO finance_settings(singleton) VALUES(1);
CREATE TABLE finance_settings_history (
    version BIGINT UNSIGNED PRIMARY KEY,
    request_id CHAR(36) NOT NULL UNIQUE,
    operator_id BIGINT UNSIGNED NOT NULL,
    rules_json JSON NOT NULL,
    reason VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
CREATE TABLE withdrawals (
    withdrawal_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    request_id CHAR(36) NOT NULL,
    diamonds BIGINT NOT NULL,
    gross_fen BIGINT NOT NULL,
    fee_fen BIGINT NOT NULL,
    payout_fen BIGINT NOT NULL,
    settings_version BIGINT UNSIGNED NOT NULL,
    rules_json JSON NOT NULL,
    payout_method VARCHAR(32) NOT NULL,
    payout_account VARCHAR(200) NOT NULL,
    payout_name VARCHAR(80) NOT NULL,
    status ENUM('PENDING','APPROVED','PAID','REJECTED') NOT NULL DEFAULT 'PENDING',
    payment_ref VARCHAR(128) NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_withdrawal_request(user_id,request_id),
    INDEX idx_withdrawal_user(user_id,status),
    CONSTRAINT fk_withdrawal_user FOREIGN KEY(user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;
CREATE TABLE withdrawal_actions (
    request_id CHAR(36) PRIMARY KEY,
    withdrawal_id BIGINT UNSIGNED NOT NULL,
    operator_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    payment_ref VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_withdrawal_action FOREIGN KEY(withdrawal_id) REFERENCES withdrawals(withdrawal_id)
) ENGINE=InnoDB;
-- +goose Down
DROP TABLE withdrawal_actions;
DROP TABLE withdrawals;
DROP TABLE finance_settings_history;
DROP TABLE finance_settings;
