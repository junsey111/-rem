-- +goose Up
ALTER TABLE wallet_transactions
  MODIFY tx_type ENUM(
    'RECHARGE','GIFT_DEBIT','REFUND_HOLD','REFUND_RELEASE','WATCH_UNLOCK',
    'DM_GIFT_DEBIT','DM_VIDEO_UNLOCK','DM_VIDEO_CALL','REFERRAL_REWARD'
  ) NOT NULL;

ALTER TABLE referral_settings
  ADD COLUMN recharge_bps INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN withdrawal_bps INT UNSIGNED NOT NULL DEFAULT 0;

CREATE TABLE referral_reward_events (
  reward_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  source_kind ENUM('RECHARGE','WITHDRAWAL') NOT NULL,
  source_id BIGINT UNSIGNED NOT NULL,
  inviter_id BIGINT UNSIGNED NOT NULL,
  invitee_id BIGINT UNSIGNED NOT NULL,
  source_amount BIGINT NOT NULL,
  rate_bps INT UNSIGNED NOT NULL,
  reward_coins BIGINT NOT NULL,
  wallet_tx_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_referral_reward_source (source_kind, source_id),
  INDEX idx_referral_reward_inviter (inviter_id, reward_id),
  CONSTRAINT fk_referral_reward_inviter FOREIGN KEY (inviter_id) REFERENCES users(user_id),
  CONSTRAINT fk_referral_reward_invitee FOREIGN KEY (invitee_id) REFERENCES users(user_id),
  CONSTRAINT fk_referral_reward_tx FOREIGN KEY (wallet_tx_id) REFERENCES wallet_transactions(tx_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- +goose Down
DROP TABLE referral_reward_events;
ALTER TABLE referral_settings
  DROP COLUMN withdrawal_bps,
  DROP COLUMN recharge_bps;
