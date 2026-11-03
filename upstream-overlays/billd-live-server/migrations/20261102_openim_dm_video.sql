CREATE TABLE IF NOT EXISTS openim_dm_video (
  video_id CHAR(36) NOT NULL,
  request_id CHAR(36) NOT NULL,
  owner_user_id INT NOT NULL,
  recipient_user_id INT NOT NULL,
  title VARCHAR(40) NOT NULL,
  price_coins INT NOT NULL,
  duration_sec INT NOT NULL,
  content_type VARCHAR(32) NOT NULL,
  byte_length INT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  storage_name CHAR(36) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (video_id),
  UNIQUE KEY openim_dm_video_owner_request (owner_user_id, request_id)
);

-- Receipt cache of a Revenue acceptance. Not a balance and not a second ledger.
CREATE TABLE IF NOT EXISTS openim_dm_video_unlock (
  video_id CHAR(36) NOT NULL,
  viewer_user_id INT NOT NULL,
  request_id CHAR(36) NOT NULL,
  charged_coins INT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (video_id, viewer_user_id)
);
