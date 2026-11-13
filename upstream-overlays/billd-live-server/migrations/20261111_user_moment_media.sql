ALTER TABLE `user_moment`
  ADD COLUMN `review_status` VARCHAR(16) NOT NULL DEFAULT 'APPROVED' AFTER `visible`,
  ADD COLUMN `moderation_reason` VARCHAR(200) NULL AFTER `review_status`,
  ADD COLUMN `reviewed_by` INT UNSIGNED NULL AFTER `moderation_reason`,
  ADD COLUMN `reviewed_at` DATETIME NULL AFTER `reviewed_by`,
  ADD KEY `moment_review_page` (`review_status`, `id`),
  ADD KEY `moment_visible_review_page` (`visible`, `review_status`, `id`);

CREATE TABLE IF NOT EXISTS `user_moment_media` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `moment_id` BIGINT UNSIGNED NULL,
  `user_id` INT UNSIGNED NOT NULL,
  `storage_name` VARCHAR(64) NOT NULL,
  `content_type` VARCHAR(32) NOT NULL,
  `byte_length` INT UNSIGNED NOT NULL,
  `width` INT UNSIGNED NOT NULL,
  `height` INT UNSIGNED NOT NULL,
  `sha256` CHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `moment_media_storage` (`storage_name`),
  KEY `moment_media_page` (`moment_id`, `id`),
  KEY `moment_media_user_page` (`user_id`, `moment_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
