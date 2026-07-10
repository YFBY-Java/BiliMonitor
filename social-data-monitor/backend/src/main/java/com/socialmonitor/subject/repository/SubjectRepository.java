package com.socialmonitor.subject.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.subject.domain.MonitoredSubject;
import com.socialmonitor.subject.domain.SubjectBilibiliBinding;
import com.socialmonitor.subject.domain.SubjectWidgetLayout;
import com.socialmonitor.subject.dto.SubjectHealthEventView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.subject-monitor", name = "enabled", matchIfMissing = true)
public class SubjectRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SubjectRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<MonitoredSubject> findAllSubjects() {
        String sql = """
                SELECT * FROM monitored_subject
                ORDER BY monitor_status ASC, updated_at DESC, id ASC
                """;
        return jdbcTemplate.query(sql, Map.of(), this::mapSubject);
    }

    public Optional<MonitoredSubject> findSubject(Long subjectId) {
        String sql = "SELECT * FROM monitored_subject WHERE id = :id";
        return jdbcTemplate.query(sql, Map.of("id", subjectId), this::mapSubject).stream().findFirst();
    }

    public MonitoredSubject insertSubject(String displayName, String avatarUrl, String remark, List<String> tags) {
        String sql = """
                INSERT INTO monitored_subject (display_name, avatar_url, remark, tags_json, updated_at)
                VALUES (:displayName, :avatarUrl, :remark, CAST(:tags AS jsonb), now())
                RETURNING *
                """;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("displayName", displayName)
                .addValue("avatarUrl", avatarUrl)
                .addValue("remark", remark)
                .addValue("tags", toJson(tags == null ? List.of() : tags)), this::mapSubject);
    }

    public MonitoredSubject updateSubject(
            Long subjectId,
            String displayName,
            String avatarUrl,
            String remark,
            List<String> tags,
            String monitorStatus
    ) {
        String sql = """
                UPDATE monitored_subject SET
                    display_name = COALESCE(:displayName, display_name),
                    avatar_url = COALESCE(:avatarUrl, avatar_url),
                    remark = COALESCE(:remark, remark),
                    tags_json = CASE WHEN :tags IS NULL THEN tags_json ELSE CAST(:tags AS jsonb) END,
                    monitor_status = COALESCE(:monitorStatus, monitor_status),
                    updated_at = now()
                WHERE id = :id
                RETURNING *
                """;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("id", subjectId)
                .addValue("displayName", displayName)
                .addValue("avatarUrl", avatarUrl)
                .addValue("remark", remark)
                .addValue("tags", tags == null ? null : toJson(tags))
                .addValue("monitorStatus", monitorStatus), this::mapSubject);
    }

    public void updateSubjectHealth(Long subjectId, BigDecimal healthScore, OffsetDateTime lastSuccessAt, OffsetDateTime lastEventAt) {
        String sql = """
                UPDATE monitored_subject SET
                    health_score = :healthScore,
                    last_success_at = :lastSuccessAt,
                    last_event_at = :lastEventAt,
                    updated_at = now()
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", subjectId)
                .addValue("healthScore", healthScore)
                .addValue("lastSuccessAt", lastSuccessAt)
                .addValue("lastEventAt", lastEventAt));
    }

    public void deleteSubject(Long subjectId) {
        jdbcTemplate.update("DELETE FROM monitored_subject WHERE id = :id", Map.of("id", subjectId));
    }

    public Optional<SubjectBilibiliBinding> findBinding(Long subjectId) {
        String sql = "SELECT * FROM subject_bilibili_binding WHERE subject_id = :subjectId";
        return jdbcTemplate.query(sql, Map.of("subjectId", subjectId), this::mapBinding).stream().findFirst();
    }

    public SubjectBilibiliBinding upsertBinding(
            Long subjectId,
            Long bilibiliUserMonitorId,
            Long bilibiliLiveRoomMonitorId,
            Long mid,
            Long roomId,
            List<String> enabledCapabilities,
            Boolean danmuEnabled
    ) {
        String sql = """
                INSERT INTO subject_bilibili_binding (
                    subject_id, bilibili_user_monitor_id, bilibili_live_room_monitor_id, mid, room_id,
                    enabled_capabilities_json, danmu_enabled, updated_at
                )
                VALUES (
                    :subjectId, :userMonitorId, :liveRoomMonitorId, :mid, :roomId,
                    CAST(:capabilities AS jsonb), COALESCE(:danmuEnabled, false), now()
                )
                ON CONFLICT (subject_id) DO UPDATE SET
                    bilibili_user_monitor_id = COALESCE(EXCLUDED.bilibili_user_monitor_id, subject_bilibili_binding.bilibili_user_monitor_id),
                    bilibili_live_room_monitor_id = COALESCE(EXCLUDED.bilibili_live_room_monitor_id, subject_bilibili_binding.bilibili_live_room_monitor_id),
                    mid = COALESCE(EXCLUDED.mid, subject_bilibili_binding.mid),
                    room_id = COALESCE(EXCLUDED.room_id, subject_bilibili_binding.room_id),
                    enabled_capabilities_json = CASE
                        WHEN :capabilitiesWasProvided THEN EXCLUDED.enabled_capabilities_json
                        ELSE subject_bilibili_binding.enabled_capabilities_json
                    END,
                    danmu_enabled = CASE
                        WHEN :danmuWasProvided THEN EXCLUDED.danmu_enabled
                        ELSE subject_bilibili_binding.danmu_enabled
                    END,
                    updated_at = now()
                RETURNING *
                """;
        List<String> capabilities = enabledCapabilities == null
                ? List.of("follower", "live_heat")
                : enabledCapabilities;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("subjectId", subjectId)
                .addValue("userMonitorId", bilibiliUserMonitorId)
                .addValue("liveRoomMonitorId", bilibiliLiveRoomMonitorId)
                .addValue("mid", mid)
                .addValue("roomId", roomId)
                .addValue("capabilities", toJson(capabilities))
                .addValue("capabilitiesWasProvided", enabledCapabilities != null)
                .addValue("danmuEnabled", danmuEnabled)
                .addValue("danmuWasProvided", danmuEnabled != null), this::mapBinding);
    }

    public List<SubjectWidgetLayout> findLayouts(Long subjectId) {
        String sql = """
                SELECT * FROM subject_widget_layout
                WHERE subject_id = :subjectId AND enabled = true
                ORDER BY COALESCE((position_json ->> 'y')::int, 0), COALESCE((position_json ->> 'x')::int, 0), id ASC
                """;
        return jdbcTemplate.query(sql, Map.of("subjectId", subjectId), this::mapLayout);
    }

    public List<SubjectWidgetLayout> findAllLayouts(Long subjectId) {
        String sql = """
                SELECT * FROM subject_widget_layout
                WHERE subject_id = :subjectId
                ORDER BY COALESCE((position_json ->> 'y')::int, 0), COALESCE((position_json ->> 'x')::int, 0), id ASC
                """;
        return jdbcTemplate.query(sql, Map.of("subjectId", subjectId), this::mapLayout);
    }

    public void upsertLayout(Long subjectId, String widgetKey, Boolean enabled, Map<String, Object> position, Map<String, Object> settings) {
        String sql = """
                INSERT INTO subject_widget_layout (subject_id, widget_key, enabled, position_json, settings_json, updated_at)
                VALUES (:subjectId, :widgetKey, :enabled, CAST(:position AS jsonb), CAST(:settings AS jsonb), now())
                ON CONFLICT (subject_id, widget_key) DO UPDATE SET
                    enabled = EXCLUDED.enabled,
                    position_json = EXCLUDED.position_json,
                    settings_json = EXCLUDED.settings_json,
                    updated_at = now()
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("subjectId", subjectId)
                .addValue("widgetKey", widgetKey)
                .addValue("enabled", enabled == null || enabled)
                .addValue("position", toJson(position == null ? Map.of() : position))
                .addValue("settings", toJson(settings == null ? Map.of() : settings)));
    }

    public void deleteLayouts(Long subjectId) {
        jdbcTemplate.update("DELETE FROM subject_widget_layout WHERE subject_id = :subjectId", Map.of("subjectId", subjectId));
    }

    public List<SubjectHealthEventView> findRecentLiveEvents(Long liveRoomMonitorId, int limit) {
        String sql = """
                SELECT event_type, title_before, title_after, online_count, occurred_at
                FROM bilibili_live_status_event
                WHERE monitor_id = :monitorId
                ORDER BY occurred_at DESC
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, Map.of("monitorId", liveRoomMonitorId, "limit", limit), (rs, rowNum) -> {
            String eventType = rs.getString("event_type");
            String titleAfter = rs.getString("title_after");
            String title = switch (eventType) {
                case "LIVE_STARTED" -> "直播间已开播";
                case "LIVE_ENDED" -> "直播间已下播";
                case "TITLE_CHANGED" -> "直播标题发生变化";
                default -> "直播间状态更新";
            };
            String description = titleAfter == null || titleAfter.isBlank()
                    ? "在线/热度 " + nullableLong(rs, "online_count")
                    : titleAfter;
            return new SubjectHealthEventView(
                    eventType,
                    title,
                    description,
                    "bilibili-live",
                    rs.getObject("occurred_at", OffsetDateTime.class),
                    "info"
            );
        });
    }

    private MonitoredSubject mapSubject(ResultSet rs, int rowNum) throws SQLException {
        return new MonitoredSubject(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                rs.getString("remark"),
                readStringList(rs.getString("tags_json")),
                rs.getString("monitor_status"),
                rs.getBigDecimal("health_score"),
                rs.getObject("last_success_at", OffsetDateTime.class),
                rs.getObject("last_event_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private SubjectBilibiliBinding mapBinding(ResultSet rs, int rowNum) throws SQLException {
        return new SubjectBilibiliBinding(
                rs.getLong("id"),
                rs.getLong("subject_id"),
                nullableLong(rs, "bilibili_user_monitor_id"),
                nullableLong(rs, "bilibili_live_room_monitor_id"),
                nullableLong(rs, "mid"),
                nullableLong(rs, "room_id"),
                readStringList(rs.getString("enabled_capabilities_json")),
                rs.getBoolean("danmu_enabled"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private SubjectWidgetLayout mapLayout(ResultSet rs, int rowNum) throws SQLException {
        return new SubjectWidgetLayout(
                rs.getLong("id"),
                rs.getLong("subject_id"),
                rs.getString("widget_key"),
                rs.getBoolean("enabled"),
                readObjectMap(rs.getString("position_json")),
                readObjectMap(rs.getString("settings_json")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private List<String> readStringList(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawJson, STRING_LIST);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private Map<String, Object> readObjectMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, OBJECT_MAP);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<>() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
