CREATE TABLE IF NOT EXISTS openim_session (
  user_id INT NOT NULL,
  platform_id INT NOT NULL,
  session_hash VARCHAR(64) NULL,
  im_token TEXT NULL,
  expires_at BIGINT NOT NULL DEFAULT 0,
  pending TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (user_id, platform_id)
);
