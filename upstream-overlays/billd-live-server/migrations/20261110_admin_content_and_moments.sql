CREATE TABLE IF NOT EXISTS `platform_content` (
  `content_key` varchar(32) NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `value_json` longtext NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`content_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `platform_content_action` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `request_id` varchar(36) NOT NULL,
  `content_key` varchar(32) NOT NULL,
  `version` int unsigned NOT NULL,
  `operator_id` int unsigned NOT NULL,
  `reason` varchar(200) NOT NULL,
  `payload_hash` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_platform_content_action_request` (`request_id`),
  KEY `idx_platform_content_action_key` (`content_key`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `moment_moderation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `request_id` varchar(36) NOT NULL,
  `moment_id` bigint unsigned NOT NULL,
  `operator_id` int unsigned NOT NULL,
  `reason` varchar(200) NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_moment_moderation_request` (`request_id`),
  KEY `idx_moment_moderation_moment` (`moment_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
