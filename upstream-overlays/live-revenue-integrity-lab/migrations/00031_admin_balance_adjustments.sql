-- +goose Up
ALTER TABLE wallet_transactions
  MODIFY tx_type ENUM(
    'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
    'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK','DM_VIDEO_CALL','REFERRAL_REWARD','ADMIN_ADJUST'
  ) NOT NULL;

CREATE TABLE admin_balance_adjustments (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  body_hash BINARY(32) NOT NULL,
  old_balance BIGINT NOT NULL,
  new_balance BIGINT NOT NULL,
  reason VARCHAR(200) NOT NULL,
  wallet_tx_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_admin_balance_request (user_id, request_id),
  INDEX idx_admin_balance_user (user_id, id),
  CONSTRAINT fk_admin_balance_user FOREIGN KEY (user_id) REFERENCES users(user_id),
  CONSTRAINT fk_admin_balance_tx FOREIGN KEY (wallet_tx_id) REFERENCES wallet_transactions(tx_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- +goose Down
DROP TABLE admin_balance_adjustments;
ALTER TABLE wallet_transactions
  MODIFY tx_type ENUM(
    'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
    'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK','DM_VIDEO_CALL','REFERRAL_REWARD'
  ) NOT NULL;
