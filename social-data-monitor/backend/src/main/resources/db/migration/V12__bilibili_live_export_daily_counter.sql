CREATE TABLE bilibili_live_export_daily_counter (
    uid BIGINT NOT NULL,
    export_date DATE NOT NULL,
    export_count BIGINT NOT NULL CHECK (export_count > 0),
    PRIMARY KEY (uid, export_date)
);

COMMENT ON TABLE bilibili_live_export_daily_counter IS '每位主播按北京时间日期累计的导出编号';
