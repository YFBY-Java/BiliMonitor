ALTER TABLE bilibili_monitored_user
    DROP CONSTRAINT IF EXISTS bilibili_monitored_user_interval_seconds_check;

ALTER TABLE bilibili_monitored_user
    DROP CONSTRAINT IF EXISTS chk_bilibili_monitored_user_interval_seconds_range;

ALTER TABLE bilibili_monitored_user
    ADD CONSTRAINT chk_bilibili_monitored_user_interval_seconds_range
    CHECK (interval_seconds BETWEEN 1 AND 2592000);
