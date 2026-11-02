-- Widen before deploying hash-writing code. Existing plaintext is upgraded at login.
-- Repeating this migration does not rewrite credentials or invalidate sessions.
ALTER TABLE `user` MODIFY COLUMN `password` VARCHAR(255) NOT NULL;
