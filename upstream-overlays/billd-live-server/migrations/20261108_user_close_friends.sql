CREATE TABLE IF NOT EXISTS `user_close_friend_relation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_low_id` BIGINT UNSIGNED NOT NULL,
  `user_high_id` BIGINT UNSIGNED NOT NULL,
  `requested_by` BIGINT UNSIGNED NOT NULL,
  `status` ENUM('pending', 'accepted') NOT NULL DEFAULT 'pending',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_close_friend_pair` (`user_low_id`, `user_high_id`),
  KEY `user_close_friend_low_status` (`user_low_id`, `status`),
  KEY `user_close_friend_high_status` (`user_high_id`, `status`),
  CHECK (`user_low_id` < `user_high_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
