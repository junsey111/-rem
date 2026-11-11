ALTER TABLE `user_moment_comment`
  ADD COLUMN `parent_id` BIGINT UNSIGNED NULL AFTER `user_id`,
  ADD COLUMN `reply_to_user_id` INT UNSIGNED NULL AFTER `parent_id`,
  ADD KEY `moment_reply_page` (`moment_id`, `parent_id`, `visible`, `id`);
