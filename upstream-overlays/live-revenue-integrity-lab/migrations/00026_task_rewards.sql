-- +goose Up
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM(
        'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
        'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK','REFERRAL_REWARD','TASK_REWARD'
    ) NOT NULL;

CREATE TABLE task_reward_claims (
    claim_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    task_key VARCHAR(64) NOT NULL,
    task_date DATE NOT NULL,
    request_id CHAR(36) NOT NULL,
    reward_coins INT UNSIGNED NOT NULL,
    body_hash BINARY(32) NOT NULL,
    tx_id BIGINT UNSIGNED NOT NULL,
    new_balance BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_task_claim_day (user_id, task_key, task_date),
    UNIQUE KEY uq_task_claim_request (user_id, request_id),
    CONSTRAINT fk_task_claim_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_task_claim_tx FOREIGN KEY (tx_id) REFERENCES wallet_transactions(tx_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- +goose Down
DROP TABLE task_reward_claims;
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM(
        'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
        'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK','REFERRAL_REWARD'
    ) NOT NULL;
