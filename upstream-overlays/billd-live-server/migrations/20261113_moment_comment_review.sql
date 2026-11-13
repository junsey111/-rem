-- Existing comments remain approved; new HTTP submissions explicitly enter PENDING.
-- Apply once after user_moment_comment exists; do not reset existing data.
ALTER TABLE `user_moment_comment`
  ADD COLUMN `review_status` VARCHAR(16) NOT NULL DEFAULT 'APPROVED' AFTER `visible`,
  ADD COLUMN `moderation_reason` VARCHAR(200) NULL AFTER `review_status`,
  ADD COLUMN `reviewed_by` INT UNSIGNED NULL AFTER `moderation_reason`,
  ADD COLUMN `reviewed_at` DATETIME NULL AFTER `reviewed_by`,
  ADD KEY `comment_review_page` (`review_status`, `id`);
