-- +goose Up
UPDATE level_settings SET
    viewer_thresholds = JSON_REMOVE(viewer_thresholds, '$[8]', '$[7]', '$[6]', '$[5]'),
    creator_thresholds = JSON_REMOVE(creator_thresholds, '$[8]', '$[7]', '$[6]', '$[5]')
WHERE singleton = 1;

-- +goose Down
UPDATE level_settings SET
    viewer_thresholds = JSON_ARRAY_APPEND(viewer_thresholds, '$', 100000, '$', 250000, '$', 500000, '$', 1000000),
    creator_thresholds = JSON_ARRAY_APPEND(creator_thresholds, '$', 100000, '$', 250000, '$', 500000, '$', 1000000)
WHERE singleton = 1;
