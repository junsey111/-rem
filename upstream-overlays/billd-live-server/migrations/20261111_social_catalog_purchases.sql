ALTER TABLE `platform_noble_membership`
  ADD COLUMN `purchase_request_id` VARCHAR(64) NULL,
  ADD UNIQUE KEY `uq_noble_membership_purchase_request` (`purchase_request_id`);

ALTER TABLE `platform_user_costume`
  ADD COLUMN `purchase_request_id` VARCHAR(64) NULL,
  ADD UNIQUE KEY `uq_user_costume_purchase_request` (`purchase_request_id`);
