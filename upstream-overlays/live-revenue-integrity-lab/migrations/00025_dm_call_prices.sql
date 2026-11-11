-- +goose Up
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM(
        'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
        'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK','DM_VIDEO_CALL'
    ) NOT NULL;

CREATE TABLE dm_call_prices (
    owner_id BIGINT UNSIGNED PRIMARY KEY,
    price_coins INT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_dm_call_price_owner FOREIGN KEY (owner_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dm_call_charges (
    call_id CHAR(32) PRIMARY KEY,
    payer_id BIGINT UNSIGNED NOT NULL,
    owner_id BIGINT UNSIGNED NOT NULL,
    price_coins INT UNSIGNED NOT NULL,
    post_balance BIGINT NOT NULL,
    tx_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_dm_call_charges_owner (owner_id, created_at),
    CONSTRAINT fk_dm_call_charge_payer FOREIGN KEY (payer_id) REFERENCES users(user_id),
    CONSTRAINT fk_dm_call_charge_owner FOREIGN KEY (owner_id) REFERENCES users(user_id),
    CONSTRAINT fk_dm_call_charge_tx FOREIGN KEY (tx_id) REFERENCES wallet_transactions(tx_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- +goose Down
DROP TABLE dm_call_charges;
DROP TABLE dm_call_prices;
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM(
        'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
        'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK'
    ) NOT NULL;
