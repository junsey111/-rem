-- +goose Up
CREATE TABLE level_settings (
    singleton TINYINT PRIMARY KEY,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    configured BOOLEAN NOT NULL DEFAULT FALSE,
    viewer_thresholds JSON NOT NULL,
    creator_thresholds JSON NOT NULL
);
INSERT INTO level_settings(singleton,viewer_thresholds,creator_thresholds)
VALUES(1,'[100,500,2000,10000,50000,100000,250000,500000,1000000]',
         '[100,500,2000,10000,50000,100000,250000,500000,1000000]');

-- +goose Down
DROP TABLE level_settings;
