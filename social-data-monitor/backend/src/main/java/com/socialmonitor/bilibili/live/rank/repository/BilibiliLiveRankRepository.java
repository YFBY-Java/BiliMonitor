package com.socialmonitor.bilibili.live.rank.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.live.rank.domain.BilibiliLiveRankEntry;
import com.socialmonitor.bilibili.live.rank.domain.BilibiliLiveRankFetchedEntry;
import com.socialmonitor.bilibili.live.rank.domain.BilibiliLiveRankFetchedSnapshot;
import com.socialmonitor.bilibili.live.rank.domain.BilibiliLiveRankSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = {"storage-enabled", "rank.enabled"}, matchIfMissing = true)
public class BilibiliLiveRankRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BilibiliLiveRankRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Long upsertSnapshot(
            Long monitorId,
            Long roomId,
            Long ruid,
            BilibiliLiveRankFetchedSnapshot snapshot
    ) {
        String sql = """
                INSERT INTO bilibili_live_rank_snapshot (
                    monitor_id, room_id, ruid, rank_family, rank_type, rank_switch, period_scope,
                    page_no, page_size, total_count, count_text, value_text, remind_msg,
                    source_endpoint, signed_required, captured_at, captured_bucket, raw_payload_json
                )
                VALUES (
                    :monitorId, :roomId, :ruid, :rankFamily, :rankType, :rankSwitch, :periodScope,
                    :pageNo, :pageSize, :totalCount, :countText, :valueText, :remindMsg,
                    :sourceEndpoint, :signedRequired, :capturedAt, :capturedBucket, CAST(:rawPayload AS jsonb)
                )
                ON CONFLICT (
                    monitor_id,
                    rank_family,
                    rank_type,
                    (COALESCE(rank_switch, '')),
                    (COALESCE(period_scope, '')),
                    captured_bucket,
                    page_no
                )
                DO UPDATE SET
                    room_id = EXCLUDED.room_id,
                    ruid = EXCLUDED.ruid,
                    page_size = EXCLUDED.page_size,
                    total_count = EXCLUDED.total_count,
                    count_text = EXCLUDED.count_text,
                    value_text = EXCLUDED.value_text,
                    remind_msg = EXCLUDED.remind_msg,
                    source_endpoint = EXCLUDED.source_endpoint,
                    signed_required = EXCLUDED.signed_required,
                    captured_at = EXCLUDED.captured_at,
                    raw_payload_json = EXCLUDED.raw_payload_json
                RETURNING id
                """;
        Long snapshotId = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("roomId", roomId)
                .addValue("ruid", ruid)
                .addValue("rankFamily", snapshot.rankFamily())
                .addValue("rankType", snapshot.rankType())
                .addValue("rankSwitch", snapshot.rankSwitch())
                .addValue("periodScope", snapshot.periodScope())
                .addValue("pageNo", snapshot.pageNo())
                .addValue("pageSize", snapshot.pageSize())
                .addValue("totalCount", snapshot.totalCount())
                .addValue("countText", snapshot.countText())
                .addValue("valueText", snapshot.valueText())
                .addValue("remindMsg", truncate(snapshot.remindMsg(), 512))
                .addValue("sourceEndpoint", snapshot.sourceEndpoint())
                .addValue("signedRequired", Boolean.TRUE.equals(snapshot.signedRequired()))
                .addValue("capturedAt", snapshot.capturedAt())
                .addValue("capturedBucket", snapshot.capturedAt().truncatedTo(ChronoUnit.MINUTES))
                .addValue("rawPayload", safeJson(snapshot.rawPayload())), Long.class);
        if (snapshotId == null) {
            throw new IllegalStateException("Bilibili live rank snapshot upsert did not return id.");
        }
        replaceEntries(snapshotId, monitorId, roomId, ruid, snapshot.entries());
        return snapshotId;
    }

    public Optional<BilibiliLiveRankSnapshot> findLatestSnapshot(
            Long monitorId,
            String family,
            String rankType,
            String rankSwitch
    ) {
        String sql = """
                SELECT * FROM bilibili_live_rank_snapshot
                WHERE monitor_id = :monitorId
                  AND (:family IS NULL OR rank_family = :family)
                  AND (:rankType IS NULL OR rank_type = :rankType)
                  AND (:rankSwitch IS NULL OR COALESCE(rank_switch, '') = :rankSwitch)
                ORDER BY captured_at DESC, id DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("family", family)
                .addValue("rankType", rankType)
                .addValue("rankSwitch", rankSwitch), this::mapSnapshot).stream().findFirst();
    }

    public List<BilibiliLiveRankSnapshot> findLatestSnapshots(Long monitorId) {
        String sql = """
                SELECT DISTINCT ON (rank_family, rank_type, COALESCE(rank_switch, ''), COALESCE(period_scope, '')) *
                FROM bilibili_live_rank_snapshot
                WHERE monitor_id = :monitorId
                ORDER BY rank_family, rank_type, COALESCE(rank_switch, ''), COALESCE(period_scope, ''), captured_at DESC, id DESC
                """;
        return jdbcTemplate.query(sql, Map.of("monitorId", monitorId), this::mapSnapshot);
    }

    public List<BilibiliLiveRankSnapshot> findLatestSnapshotPages(Long monitorId) {
        String sql = """
                WITH latest_bucket AS (
                    SELECT
                        rank_family,
                        rank_type,
                        COALESCE(rank_switch, '') AS rank_switch_key,
                        COALESCE(period_scope, '') AS period_scope_key,
                        MAX(captured_bucket) AS captured_bucket
                    FROM bilibili_live_rank_snapshot
                    WHERE monitor_id = :monitorId
                    GROUP BY rank_family, rank_type, COALESCE(rank_switch, ''), COALESCE(period_scope, '')
                )
                SELECT s.*
                FROM bilibili_live_rank_snapshot s
                JOIN latest_bucket latest
                  ON s.rank_family = latest.rank_family
                 AND s.rank_type = latest.rank_type
                 AND COALESCE(s.rank_switch, '') = latest.rank_switch_key
                 AND COALESCE(s.period_scope, '') = latest.period_scope_key
                 AND s.captured_bucket = latest.captured_bucket
                WHERE s.monitor_id = :monitorId
                ORDER BY s.rank_family, s.rank_type, COALESCE(s.rank_switch, ''), COALESCE(s.period_scope, ''), s.page_no ASC
                """;
        return jdbcTemplate.query(sql, Map.of("monitorId", monitorId), this::mapSnapshot);
    }

    public List<BilibiliLiveRankEntry> findEntries(Long snapshotId, int limit) {
        String sql = """
                SELECT * FROM bilibili_live_rank_entry
                WHERE snapshot_id = :snapshotId
                ORDER BY rank_no ASC NULLS LAST, id ASC
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, Map.of("snapshotId", snapshotId, "limit", limit), this::mapEntry);
    }

    private void replaceEntries(
            Long snapshotId,
            Long monitorId,
            Long roomId,
            Long ruid,
            List<BilibiliLiveRankFetchedEntry> entries
    ) {
        jdbcTemplate.update("DELETE FROM bilibili_live_rank_entry WHERE snapshot_id = :snapshotId", Map.of("snapshotId", snapshotId));
        if (entries == null || entries.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO bilibili_live_rank_entry (
                    snapshot_id, monitor_id, room_id, ruid, user_uid, rank_no, entry_kind,
                    display_name, face_url, score, guard_level, wealth_level, medal_name, medal_level,
                    medal_ruid, medal_is_light, guard_expired_text, accompany_days, raw_entry_json
                )
                VALUES (
                    :snapshotId, :monitorId, :roomId, :ruid, :userUid, :rankNo, :entryKind,
                    :displayName, :faceUrl, :score, :guardLevel, :wealthLevel, :medalName, :medalLevel,
                    :medalRuid, :medalIsLight, :guardExpiredText, :accompanyDays, CAST(:rawEntryJson AS jsonb)
                )
                """;
        MapSqlParameterSource[] batch = entries.stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("snapshotId", snapshotId)
                        .addValue("monitorId", monitorId)
                        .addValue("roomId", roomId)
                        .addValue("ruid", ruid)
                        .addValue("userUid", entry.userUid())
                        .addValue("rankNo", entry.rankNo())
                        .addValue("entryKind", entry.entryKind())
                        .addValue("displayName", truncate(entry.displayName(), 512))
                        .addValue("faceUrl", entry.faceUrl())
                        .addValue("score", entry.score())
                        .addValue("guardLevel", entry.guardLevel())
                        .addValue("wealthLevel", entry.wealthLevel())
                        .addValue("medalName", truncate(entry.medalName(), 128))
                        .addValue("medalLevel", entry.medalLevel())
                        .addValue("medalRuid", entry.medalRuid())
                        .addValue("medalIsLight", entry.medalIsLight())
                        .addValue("guardExpiredText", truncate(entry.guardExpiredText(), 128))
                        .addValue("accompanyDays", entry.accompanyDays())
                        .addValue("rawEntryJson", safeJson(entry.rawEntryJson())))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private BilibiliLiveRankSnapshot mapSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliLiveRankSnapshot(
                rs.getLong("id"),
                rs.getLong("monitor_id"),
                rs.getLong("room_id"),
                rs.getLong("ruid"),
                rs.getString("rank_family"),
                rs.getString("rank_type"),
                rs.getString("rank_switch"),
                rs.getString("period_scope"),
                rs.getInt("page_no"),
                rs.getInt("page_size"),
                nullableLong(rs, "total_count"),
                rs.getString("count_text"),
                rs.getString("value_text"),
                rs.getString("remind_msg"),
                rs.getString("source_endpoint"),
                rs.getBoolean("signed_required"),
                rs.getObject("captured_at", OffsetDateTime.class),
                rs.getObject("captured_bucket", OffsetDateTime.class)
        );
    }

    private BilibiliLiveRankEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliLiveRankEntry(
                rs.getLong("id"),
                rs.getLong("snapshot_id"),
                rs.getLong("monitor_id"),
                rs.getLong("room_id"),
                rs.getLong("ruid"),
                nullableLong(rs, "user_uid"),
                nullableInteger(rs, "rank_no"),
                rs.getString("entry_kind"),
                rs.getString("display_name"),
                rs.getString("face_url"),
                nullableLong(rs, "score"),
                nullableInteger(rs, "guard_level"),
                nullableInteger(rs, "wealth_level"),
                rs.getString("medal_name"),
                nullableInteger(rs, "medal_level"),
                nullableLong(rs, "medal_ruid"),
                nullableInteger(rs, "medal_is_light"),
                rs.getString("guard_expired_text"),
                nullableInteger(rs, "accompany_days"),
                "{}"
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safeJson(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        return value;
    }

    @SuppressWarnings("unused")
    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(new HashMap<>(value));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
