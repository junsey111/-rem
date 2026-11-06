CREATE TABLE IF NOT EXISTS `user_profile_visit` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `visitor_id` INT UNSIGNED NOT NULL,
  `viewed_user_id` INT UNSIGNED NOT NULL,
  `last_seen_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `profile_visit_pair` (`visitor_id`, `viewed_user_id`),
  KEY `profile_visit_recent` (`viewed_user_id`, `last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
