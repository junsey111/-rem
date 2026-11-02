-- +goose Up
-- Product catalog only. No accounts, balances, rooms, or demo activity are seeded here.
INSERT INTO gift_catalog (gift_id, display_name, coin_price, match_points, diamond_reward, enabled)
VALUES
    ('ROSE', 'Rose', 1, 1, 1, TRUE),
    ('HEART', 'Heart', 5, 5, 5, TRUE),
    ('GG', 'GG', 10, 10, 10, TRUE),
    ('DONUT', 'Donut', 30, 30, 30, TRUE),
    ('COFFEE', 'Coffee', 50, 50, 50, TRUE),
    ('CLOUD', 'Cloud', 99, 99, 99, TRUE),
    ('PERFUME', 'Perfume', 199, 199, 199, TRUE),
    ('RING', 'Ring', 299, 299, 299, TRUE),
    ('FIREWORK', 'Firework', 399, 399, 399, TRUE),
    ('DRUM', 'Drum', 499, 499, 499, TRUE),
    ('CASTLE', 'Castle', 999, 999, 999, TRUE),
    ('LION', 'Lion', 1999, 1999, 1999, TRUE)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    coin_price = VALUES(coin_price),
    match_points = VALUES(match_points),
    diamond_reward = VALUES(diamond_reward),
    enabled = VALUES(enabled);

-- +goose Down
DELETE FROM gift_catalog WHERE gift_id IN ('ROSE','HEART','GG','DONUT','COFFEE','CLOUD','PERFUME','RING','FIREWORK','DRUM','CASTLE','LION');
