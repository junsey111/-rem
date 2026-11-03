-- +goose Up
-- Superset of later paid-watch and DM enum values so a tree that also has
-- 00019/00020 keeps those tx types. Referral coins use the same COIN wallet.
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM(
        'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
        'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK','REFERRAL_REWARD'
    ) NOT NULL;

-- Invite codes are not campaign ids and are not leaderboard keys.
CREATE TABLE referral_codes (
    user_id BIGINT UNSIGNED PRIMARY KEY,
    code CHAR(8) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_referral_code (code),
    CONSTRAINT fk_referral_code_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One successful redeem per invitee. The request_id on that row is the idempotency key.
CREATE TABLE referral_redemptions (
    redemption_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    invitee_id BIGINT UNSIGNED NOT NULL,
    inviter_id BIGINT UNSIGNED NOT NULL,
    request_id CHAR(36) NOT NULL,
    code CHAR(8) NOT NULL,
    inviter_coins INT UNSIGNED NOT NULL,
    invitee_coins INT UNSIGNED NOT NULL,
    invitee_balance BIGINT NOT NULL,
    body_hash BINARY(32) NOT NULL,
    tx_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_referral_invitee (invitee_id),
    INDEX idx_referral_inviter (inviter_id, redemption_id),
    CONSTRAINT fk_referral_invitee FOREIGN KEY (invitee_id) REFERENCES users(user_id),
    CONSTRAINT fk_referral_inviter FOREIGN KEY (inviter_id) REFERENCES users(user_id),
    CONSTRAINT fk_referral_tx FOREIGN KEY (tx_id) REFERENCES wallet_transactions(tx_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- +goose Down
DROP TABLE referral_redemptions;
DROP TABLE referral_codes;
DELETE e FROM wallet_entries e
    INNER JOIN wallet_transactions t ON t.tx_id = e.tx_id
    WHERE t.tx_type = 'REFERRAL_REWARD';
DELETE FROM wallet_transactions WHERE tx_type = 'REFERRAL_REWARD';
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM(
        'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
        'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK'
    ) NOT NULL;
