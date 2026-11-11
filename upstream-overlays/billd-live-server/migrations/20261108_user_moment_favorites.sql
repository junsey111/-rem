CREATE TABLE IF NOT EXISTS `user_moment_favorite` (
  `moment_id` BIGINT UNSIGNED NOT NULL,
  `user_id` INT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`moment_id`, `user_id`),
  KEY `moment_favorite_user_page` (`user_id`, `moment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `moment_media_kind_page` ON `user_moment_media` (`media_kind`, `moment_id`);
