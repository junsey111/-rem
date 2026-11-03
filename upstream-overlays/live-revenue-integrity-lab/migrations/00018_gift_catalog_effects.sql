-- +goose Up
-- Existing gift_id rows and coin_price stay as they are. New columns default to a static gift.
ALTER TABLE gift_catalog
    ADD COLUMN effect_type ENUM('none','yyeva','svga','lottie') NOT NULL DEFAULT 'none' AFTER enabled,
    ADD COLUMN effect_url VARCHAR(512) NOT NULL DEFAULT '' AFTER effect_type,
    ADD COLUMN effect_video_mode VARCHAR(64) NOT NULL DEFAULT '' AFTER effect_url,
    ADD COLUMN effect_md5 VARCHAR(64) NOT NULL DEFAULT '' AFTER effect_video_mode,
    ADD COLUMN icon_url VARCHAR(512) NOT NULL DEFAULT '' AFTER effect_md5;

-- +goose Down
ALTER TABLE gift_catalog
    DROP COLUMN icon_url,
    DROP COLUMN effect_md5,
    DROP COLUMN effect_video_mode,
    DROP COLUMN effect_url,
    DROP COLUMN effect_type;
