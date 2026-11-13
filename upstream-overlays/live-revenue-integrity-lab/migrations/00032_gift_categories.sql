-- +goose Up
ALTER TABLE gift_catalog ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT 'recommend';
UPDATE gift_catalog SET category = CASE
  WHEN coin_price < 100 THEN 'heart'
  WHEN coin_price < 1000 THEN 'party'
  ELSE 'premium' END;
UPDATE gift_catalog SET category = 'recommend' WHERE gift_id IN ('ROSE', 'HEART', 'ROCKET', 'CASTLE');

-- +goose Down
ALTER TABLE gift_catalog DROP COLUMN category;
