CREATE TABLE IF NOT EXISTS `user_daily_checkin` (
  `user_id` BIGINT UNSIGNED NOT NULL,
  `checkin_date` DATE NOT NULL,
  PRIMARY KEY (`user_id`, `checkin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
