CREATE TABLE IF NOT EXISTS `platform_task_config` (
  `task_key` VARCHAR(64) NOT NULL,
  `category` VARCHAR(24) NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `description` VARCHAR(255) NOT NULL DEFAULT '',
  `target_count` INT UNSIGNED NOT NULL,
  `reward_coins` INT UNSIGNED NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `updated_by` BIGINT UNSIGNED NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`task_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `platform_task_claim` (
  `claim_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `task_key` VARCHAR(64) NOT NULL,
  `task_date` DATE NOT NULL,
  `request_id` VARCHAR(64) NOT NULL,
  `reward_coins` INT UNSIGNED NOT NULL,
  `tx_id` BIGINT UNSIGNED NOT NULL,
  `new_balance` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`claim_id`),
  UNIQUE KEY `uq_task_claim_day` (`user_id`, `task_key`, `task_date`),
  UNIQUE KEY `uq_task_claim_request` (`user_id`, `request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
