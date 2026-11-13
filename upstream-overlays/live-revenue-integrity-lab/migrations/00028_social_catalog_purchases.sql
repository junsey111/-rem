-- +goose Up
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM(
        'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
        'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK','REFERRAL_REWARD','TASK_REWARD',
        'NOBLE_PURCHASE','COSTUME_PURCHASE','CATALOG_PURCHASE_REVERSE'
    ) NOT NULL;

CREATE TABLE social_catalog_purchases (
    purchase_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    request_id CHAR(36) NOT NULL,
    item_type ENUM('NOBLE','COSTUME') NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    price_coins INT UNSIGNED NOT NULL,
    body_hash BINARY(32) NOT NULL,
    tx_id BIGINT UNSIGNED NOT NULL,
    new_balance BIGINT NOT NULL,
    status ENUM('DEBITED','REVERSED') NOT NULL DEFAULT 'DEBITED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_social_purchase_request (user_id, request_id),
    KEY idx_social_purchase_item (item_type, item_id, created_at),
    CONSTRAINT fk_social_purchase_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_social_purchase_tx FOREIGN KEY (tx_id) REFERENCES wallet_transactions(tx_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- +goose Down
DROP TABLE social_catalog_purchases;
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM(
        'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
        'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK','REFERRAL_REWARD','TASK_REWARD'
    ) NOT NULL;
