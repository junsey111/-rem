CREATE TABLE IF NOT EXISTS recharge_order (
  request_id VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL,
  coins INT UNSIGNED NOT NULL,
  amount_fen INT UNSIGNED NOT NULL,
  status ENUM('PENDING','CREDITED') NOT NULL DEFAULT 'PENDING',
  payment_ref VARCHAR(128) NULL UNIQUE,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX recharge_order_user_created (user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
