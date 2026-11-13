CREATE TABLE IF NOT EXISTS `platform_qualification` (
  `id` TINYINT UNSIGNED NOT NULL,
  `platform_name` VARCHAR(120) NOT NULL DEFAULT '',
  `subject_name` VARCHAR(160) NOT NULL DEFAULT '',
  `registration_number` VARCHAR(64) NOT NULL DEFAULT '',
  `office_address` VARCHAR(240) NOT NULL DEFAULT '',
  `contact_email` VARCHAR(160) NOT NULL DEFAULT '',
  `icp_record` VARCHAR(120) NOT NULL DEFAULT '',
  `network_culture_license` VARCHAR(120) NOT NULL DEFAULT '',
  `live_service_license` VARCHAR(120) NOT NULL DEFAULT '',
  `published` TINYINT(1) NOT NULL DEFAULT 0,
  `updated_by` INT UNSIGNED NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `platform_qualification_audit` (
  `audit_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_id` CHAR(36) NOT NULL,
  `operator_id` INT UNSIGNED NOT NULL,
  `reason` VARCHAR(200) NOT NULL,
  `payload` TEXT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`audit_id`),
  UNIQUE KEY `uq_platform_qualification_audit_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `platform_qualification` (`id`) VALUES (1);
