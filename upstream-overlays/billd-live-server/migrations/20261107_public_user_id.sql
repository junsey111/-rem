ALTER TABLE `user` ADD COLUMN `public_id` INT UNSIGNED NULL;
CREATE UNIQUE INDEX `uq_user_public_id` ON `user` (`public_id`);
