CREATE TABLE IF NOT EXISTS `user_social_profile` (
  `user_id` INT UNSIGNED NOT NULL,
  `height_cm` SMALLINT UNSIGNED NULL,
  `weight_kg` SMALLINT UNSIGNED NULL,
  `occupation` VARCHAR(50) NULL,
  `annual_income` ENUM('UNDER_50K', '50K_100K', '100K_200K', '200K_500K', 'OVER_500K') NULL,
  `city` VARCHAR(60) NULL,
  `marital_status` ENUM('SINGLE', 'IN_RELATIONSHIP', 'MARRIED', 'DIVORCED', 'WIDOWED') NULL,
  `tags_json` TEXT NULL,
  `dating_preferences` VARCHAR(300) NULL,
  `is_public` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  KEY `user_social_profile_public_updated` (`is_public`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
