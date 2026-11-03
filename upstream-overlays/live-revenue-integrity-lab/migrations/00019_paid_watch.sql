-- +goose Up
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM('RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK') NOT NULL;

CREATE TABLE live_watch_prices (
    live_session_id BIGINT UNSIGNED PRIMARY KEY,
    unlock_price_coins INT UNSIGNED NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_watch_price_live FOREIGN KEY (live_session_id) REFERENCES live_sessions(live_session_id)
) ENGINE=InnoDB;

CREATE TABLE live_watch_progress (
    user_id BIGINT UNSIGNED NOT NULL,
    live_session_id BIGINT UNSIGNED NOT NULL,
    watched_unpaid_seconds INT UNSIGNED NOT NULL DEFAULT 0,
    unlocked TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, live_session_id),
    CONSTRAINT fk_watch_progress_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_watch_progress_live FOREIGN KEY (live_session_id) REFERENCES live_sessions(live_session_id)
) ENGINE=InnoDB;

CREATE TABLE live_watch_heartbeats (
    user_id BIGINT UNSIGNED NOT NULL,
    request_id CHAR(36) NOT NULL,
    live_session_id BIGINT UNSIGNED NOT NULL,
    delta_seconds INT NOT NULL,
    applied_seconds INT UNSIGNED NOT NULL,
    body_hash BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, request_id),
    CONSTRAINT fk_watch_hb_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

CREATE TABLE live_watch_unlocks (
    user_id BIGINT UNSIGNED NOT NULL,
    request_id CHAR(36) NOT NULL,
    live_session_id BIGINT UNSIGNED NOT NULL,
    creator_id BIGINT UNSIGNED NOT NULL,
    charged_coins INT UNSIGNED NOT NULL,
    diamond_reward INT UNSIGNED NOT NULL,
    post_balance BIGINT NOT NULL,
    tx_id BIGINT UNSIGNED NOT NULL,
    body_hash BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, request_id),
    UNIQUE KEY uq_watch_unlock_once (user_id, live_session_id),
    CONSTRAINT fk_watch_unlock_tx FOREIGN KEY (tx_id) REFERENCES wallet_transactions(tx_id),
    CONSTRAINT fk_watch_unlock_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- +goose Down
DROP TABLE live_watch_unlocks;
DROP TABLE live_watch_heartbeats;
DROP TABLE live_watch_progress;
DROP TABLE live_watch_prices;
ALTER TABLE wallet_transactions
    MODIFY tx_type ENUM('RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE') NOT NULL;
