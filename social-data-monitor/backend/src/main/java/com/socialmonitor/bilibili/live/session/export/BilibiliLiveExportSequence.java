package com.socialmonitor.bilibili.live.session.export;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveExportSequence {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BilibiliLiveExportSequence(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Allocate independently of the read-only export snapshot. Failed downloads do not reuse numbers.
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Allocation next(Long uid) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO bilibili_live_export_daily_counter (uid, export_date, export_count)
                VALUES (:uid, (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Shanghai')::date, 1)
                ON CONFLICT (uid, export_date) DO UPDATE
                SET export_count = bilibili_live_export_daily_counter.export_count + 1
                RETURNING export_date, export_count
                """, Map.of("uid", uid), (rs, rowNum) -> new Allocation(
                        rs.getObject("export_date", LocalDate.class), rs.getLong("export_count")));
    }

    public record Allocation(LocalDate date, long number) {
    }
}
