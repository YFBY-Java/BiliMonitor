ALTER TABLE bilibili_live_danmaku_recent
    ADD COLUMN IF NOT EXISTS sender_uid BIGINT;

ALTER TABLE bilibili_live_danmaku_recent
    ADD CONSTRAINT ck_bilibili_live_danmaku_recent_sender_uid
    CHECK (sender_uid IS NULL OR sender_uid > 0);
