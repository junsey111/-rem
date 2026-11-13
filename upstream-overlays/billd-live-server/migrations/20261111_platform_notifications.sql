CREATE TABLE IF NOT EXISTS `platform_announcement` (
  `announcement_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(120) NOT NULL,
  `summary` VARCHAR(240) NOT NULL DEFAULT '',
  `content` TEXT NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `created_by` INT UNSIGNED NOT NULL,
  `updated_by` INT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`announcement_id`),
  KEY `idx_announcement_enabled_id` (`enabled`, `announcement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `platform_announcement_audit` (
  `audit_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_id` CHAR(36) NOT NULL,
  `announcement_id` BIGINT UNSIGNED NULL,
  `operator_id` INT UNSIGNED NOT NULL,
  `action` VARCHAR(16) NOT NULL,
  `reason` VARCHAR(200) NOT NULL,
  `payload` TEXT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`audit_id`),
  UNIQUE KEY `uq_announcement_audit_request` (`request_id`),
  KEY `idx_announcement_audit_record` (`announcement_id`, `audit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_announcement_read` (
  `user_id` INT UNSIGNED NOT NULL,
  `announcement_id` BIGINT UNSIGNED NOT NULL,
  `read_at` DATETIME NOT NULL,
  PRIMARY KEY (`user_id`, `announcement_id`),
  KEY `idx_announcement_read_id` (`announcement_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_notification` (
  `notification_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL,
  `kind` VARCHAR(24) NOT NULL,
  `actor_id` INT UNSIGNED NULL,
  `title` VARCHAR(120) NOT NULL,
  `body` VARCHAR(240) NOT NULL DEFAULT '',
  `read_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`notification_id`),
  UNIQUE KEY `uq_user_notification_actor` (`user_id`, `kind`, `actor_id`),
  KEY `idx_user_notification_feed` (`user_id`, `notification_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_push_token` (
  `token_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL,
  `token` VARCHAR(512) NOT NULL,
  `platform` VARCHAR(16) NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`token_id`),
  UNIQUE KEY `uq_user_push_token` (`token`),
  KEY `idx_user_push_token_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
