ALTER TABLE provider_attempt ADD COLUMN reason VARCHAR(200) NOT NULL DEFAULT '未提供理由' AFTER command_hash;
