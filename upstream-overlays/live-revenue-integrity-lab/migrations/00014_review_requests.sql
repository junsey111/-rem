-- +goose Up
CREATE TABLE review_requests (
 review_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT UNSIGNED NOT NULL,
 request_id CHAR(36) NOT NULL,
 kind ENUM('CREATOR','REPORT') NOT NULL,
 target_type VARCHAR(16) NOT NULL,
 target_id BIGINT UNSIGNED NOT NULL,
 content VARCHAR(1000) NOT NULL,
 evidence_reference VARCHAR(300) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_review_request(user_id,request_id),
 INDEX idx_review_queue(kind,review_id),
 INDEX idx_review_owner(user_id,kind,review_id),
 FOREIGN KEY(user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE review_actions (
 request_id CHAR(36) PRIMARY KEY,
 review_id BIGINT UNSIGNED NOT NULL,
 operator_id BIGINT UNSIGNED NOT NULL,
 decision VARCHAR(16) NOT NULL,
 reason VARCHAR(200) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(review_id) REFERENCES review_requests(review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- +goose Down
DROP TABLE review_actions;
DROP TABLE review_requests;
