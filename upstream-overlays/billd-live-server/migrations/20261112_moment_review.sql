CREATE TABLE IF NOT EXISTS moment_review (
  moment_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  version INT UNSIGNED NOT NULL DEFAULT 1,
  reason VARCHAR(200) NOT NULL DEFAULT '',
  operator_id INT UNSIGNED NULL,
  reviewed_at DATETIME NULL,
  KEY moment_review_status (status, moment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS moment_review_action (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id VARCHAR(36) NOT NULL UNIQUE,
  moment_id BIGINT UNSIGNED NOT NULL,
  operator_id INT UNSIGNED NOT NULL,
  decision VARCHAR(16) NOT NULL,
  from_version INT UNSIGNED NOT NULL,
  reason VARCHAR(200) NOT NULL,
  created_at DATETIME NOT NULL,
  KEY moment_review_history (moment_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
