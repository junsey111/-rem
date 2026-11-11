CREATE TABLE IF NOT EXISTS `user_moment_media` (
  `moment_id` BIGINT UNSIGNED NOT NULL,
  `media_kind` VARCHAR(8) NOT NULL,
  `content_type` VARCHAR(32) NOT NULL,
  `bytes` LONGBLOB NOT NULL,
  PRIMARY KEY (`moment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
