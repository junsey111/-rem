-- +goose Up
-- Superset of the paid-watch enum value so a tree that also has 00019 keeps WATCH_UNLOCK.
-- DM coins use the same COIN wallet and PLATFORM_RESERVED_COINS account as live gifts.
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM(
        'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
        'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK'
    ) NOT NULL;

CREATE TABLE dm_gift_orders (
    gift_order_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    viewer_id BIGINT UNSIGNED NOT NULL,
    recipient_id BIGINT UNSIGNED NOT NULL,
    request_id CHAR(36) NOT NULL,
    gift_id VARCHAR(64) NOT NULL,
    gift_name VARCHAR(40) NOT NULL,
    quantity SMALLINT UNSIGNED NOT NULL,
    charged_coins INT UNSIGNED NOT NULL,
    diamond_reward INT UNSIGNED NOT NULL,
    post_balance BIGINT NULL,
    status ENUM('ACCEPTED','REJECTED') NOT NULL,
    reject_code VARCHAR(64) NULL,
    sent_at_ms BIGINT NOT NULL,
    body_hash BINARY(32) NOT NULL,
    tx_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_dm_gift_request (viewer_id, request_id),
    INDEX idx_dm_gift_recipient (recipient_id, status, created_at),
    CONSTRAINT fk_dm_gift_viewer FOREIGN KEY (viewer_id) REFERENCES users(user_id),
    CONSTRAINT fk_dm_gift_recipient FOREIGN KEY (recipient_id) REFERENCES users(user_id),
    CONSTRAINT fk_dm_gift_tx FOREIGN KEY (tx_id) REFERENCES wallet_transactions(tx_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- First successful settlement anchors owner and price for an opaque video id.
CREATE TABLE dm_video_quotes (
    video_id VARCHAR(64) PRIMARY KEY,
    owner_id BIGINT UNSIGNED NOT NULL,
    price_coins INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_dm_video_owner FOREIGN KEY (owner_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dm_video_unlocks (
    unlock_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    viewer_id BIGINT UNSIGNED NOT NULL,
    owner_id BIGINT UNSIGNED NOT NULL,
    video_id VARCHAR(64) NOT NULL,
    request_id CHAR(36) NOT NULL,
    price_coins INT UNSIGNED NOT NULL,
    charged_coins INT UNSIGNED NOT NULL,
    diamond_reward INT UNSIGNED NOT NULL,
    post_balance BIGINT NULL,
    body_hash BINARY(32) NOT NULL,
    tx_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_dm_unlock_request (viewer_id, request_id),
    INDEX idx_dm_unlock_video (video_id, viewer_id),
    INDEX idx_dm_unlock_owner (owner_id, created_at),
    CONSTRAINT fk_dm_unlock_viewer FOREIGN KEY (viewer_id) REFERENCES users(user_id),
    CONSTRAINT fk_dm_unlock_owner FOREIGN KEY (owner_id) REFERENCES users(user_id),
    CONSTRAINT fk_dm_unlock_tx FOREIGN KEY (tx_id) REFERENCES wallet_transactions(tx_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- +goose Down
DROP TABLE dm_video_unlocks;
DROP TABLE dm_video_quotes;
DROP TABLE dm_gift_orders;
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM('RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK') NOT NULL;
