# 数据模型与存储说明

最后更新：2026-06-19

## 迁移文件

数据库迁移使用 Flyway，文件在 [`../social-data-monitor/backend/src/main/resources/db/migration/`](../social-data-monitor/backend/src/main/resources/db/migration/)。

| 文件 | 说明 |
| --- | --- |
| [`V1__init_schema.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V1__init_schema.sql) | 初始化平台、凭证、采集任务、原始载荷、归一化内容、AI、通知、身份映射等通用表。 |
| [`V2__bilibili_follower_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V2__bilibili_follower_monitor.sql) | 新增 B站监控用户表和粉丝快照表。 |
| [`V3__bilibili_interval_range.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V3__bilibili_interval_range.sql) | 将 B站采集间隔约束调整为 `1` 到 `2592000` 秒。 |
| [`V4__bilibili_live_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V4__bilibili_live_monitor.sql) | 新增 B站直播间监控、直播快照和直播状态事件表。 |
| [`V5__subject_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V5__subject_monitor.sql) | 新增指定用户 Subject 聚合层、B站绑定和 Widget 布局表。 |
| [`V6__bilibili_live_danmaku_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V6__bilibili_live_danmaku_monitor.sql) | 新增直播弹幕 WebSocket session、分钟级指标桶和最近弹幕表。 |
| [`V7__bilibili_auth_credential.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V7__bilibili_auth_credential.sql) | 为 B站 Web Cookie 登录态增加唯一索引和按平台/类型/状态查询索引。 |
| [`V8__bilibili_live_rank_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V8__bilibili_live_rank_monitor.sql) | 新增直播房间观众、大航海榜单快照和榜单明细表。 |

## B站监控用户表

表名：`bilibili_monitored_user`。

用途：保存被监控的 B站用户当前状态和调度信息。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 内部主键。 |
| `mid` | B站 UID，唯一。 |
| `nickname` | 昵称，来自 `data.card.name`。 |
| `avatar_url` | 头像 URL，来自 `data.card.face`。 |
| `profile_url` | 空间链接，当前形如 `https://space.bilibili.com/{mid}`。 |
| `current_follower_count` | 当前粉丝数。 |
| `current_following_count` | 当前关注数。 |
| `monitor_status` | `ACTIVE` 或 `PAUSED`。 |
| `interval_seconds` | 单用户采集间隔。当前约束为 `1` 到 `2592000`。 |
| `next_collect_at` | 下一次到期采集时间。 |
| `last_snapshot_at` | 最近一次快照时间。 |
| `last_success_at` | 最近一次成功采集时间。 |
| `last_error_at` | 最近一次失败时间。 |
| `last_error_type` | 最近一次错误类型。 |
| `last_error_message` | 最近一次错误信息。 |
| `source_endpoint` | 最近一次成功采集来源接口。 |
| `raw_payload` | 最近一次成功采集原始响应。 |
| `created_at` / `updated_at` | 创建和更新时间。 |

索引：

- `mid` 唯一。
- `(monitor_status, next_collect_at)` 用于扫描到期用户。

## B站粉丝快照表

表名：`bilibili_follower_snapshot`。

用途：保存趋势图使用的历史粉丝数点。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 内部主键。 |
| `monitored_user_id` | 关联 `bilibili_monitored_user.id`，删除用户时级联删除快照。 |
| `mid` | B站 UID，便于按 UID 查询。 |
| `follower_count` | 快照粉丝数。 |
| `following_count` | 快照关注数。 |
| `captured_at` | 采集时间。 |
| `source_endpoint` | 来源接口。 |
| `raw_payload` | 原始响应。 |
| `created_at` | 入库时间。 |

去重策略：

- Repository 写入时使用 `captured_at` 截断到秒的 bucket，避免同一用户同一秒内重复污染趋势点。
- 趋势查询按时间排序返回。

索引：

- `(monitored_user_id, captured_at)`。
- `(mid, captured_at)`。

## B站直播间监控表

表名：`bilibili_live_room_monitor`。

用途：保存被监控直播间的当前状态、房间资料和调度信息。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 内部主键。 |
| `uid` | 主播 UID，唯一。 |
| `room_id` | 真实直播房间号，唯一。 |
| `short_id` | 直播间短号，如接口返回。 |
| `uname` | 主播昵称。 |
| `face_url` | 主播头像 URL。 |
| `title` | 当前直播间标题。 |
| `cover_url` / `keyframe_url` | 封面和关键帧。 |
| `area_id` / `area_name` | 当前分区。 |
| `parent_area_id` / `parent_area_name` | 父分区。 |
| `live_status` | 直播状态，常见值为 `0` 未开播、`1` 直播中、`2` 轮播。 |
| `live_time` | 开播时间，如接口返回。 |
| `online_count` | 当前在线/热度数。 |
| `attention_count` | 关注数或房间关注量，如接口返回。 |
| `monitor_status` | `ACTIVE` 或 `PAUSED`。 |
| `interval_seconds` | 单直播间采集间隔，约束为 `1` 到 `2592000` 秒。 |
| `next_collect_at` | 下一次到期采集时间。 |
| `last_snapshot_at` / `last_success_at` | 最近快照和最近成功时间。 |
| `last_error_at` / `last_error_type` / `last_error_message` | 最近失败信息。 |
| `backoff_until` | 失败退避到期时间。 |
| `source_endpoint` | 最近一次成功采集来源接口。 |
| `extension_json` | 预留扩展字段。 |

索引：

- `uid` 唯一。
- `room_id` 唯一。
- `(monitor_status, next_collect_at)` 用于扫描到期直播间。
- `live_status` 用于快速统计直播中/未开播状态。

## B站直播快照表

表名：`bilibili_live_room_snapshot`。

用途：保存直播间在线/热度趋势和状态历史。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 内部主键。 |
| `monitor_id` | 关联 `bilibili_live_room_monitor.id`，删除直播间监控时级联删除快照。 |
| `uid` / `room_id` | 主播 UID 和房间号。 |
| `live_status` | 采集时直播状态。 |
| `title` | 采集时标题。 |
| `area_id` / `area_name` | 采集时分区。 |
| `online_count` | 采集时在线/热度数。 |
| `attention_count` | 采集时关注数或房间关注量。 |
| `live_time` | 开播时间。 |
| `source_endpoint` | 来源接口。 |
| `raw_payload_json` | 原始响应片段。 |
| `captured_at` | 采集时间。 |
| `captured_bucket` | 去重时间桶。 |

去重策略：

- `UNIQUE (monitor_id, captured_bucket)` 避免同一直播间同一时间桶重复写入，减少趋势图污染。
- 趋势查询按 `captured_at` 排序返回。

## B站直播状态事件表

表名：`bilibili_live_status_event`。

用途：从直播快照差异派生状态事件，例如开播、下播、标题变化。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `monitor_id` | 关联直播间监控。 |
| `uid` / `room_id` | 主播 UID 和房间号。 |
| `event_type` | 事件类型。 |
| `from_live_status` / `to_live_status` | 状态变化前后值。 |
| `title_before` / `title_after` | 标题变化前后值。 |
| `online_count` | 事件发生时在线/热度数。 |
| `occurred_at` | 事件时间。 |
| `extension_json` | 预留扩展字段。 |

索引：

- `occurred_at DESC`。
- `(room_id, occurred_at DESC)`。

## B站直播榜单快照表

表名：`bilibili_live_rank_snapshot`。

用途：保存直播间“房间观众”和“大航海”各类榜单的一次采集快照。直播页详情区和用户工作台右侧榜单视图都优先读取这张表，只有手动刷新榜单时才主动请求外部接口。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `monitor_id` | 关联 `bilibili_live_room_monitor.id`，删除直播间监控时级联删除榜单快照。 |
| `room_id` / `ruid` | 直播房间号和主播 UID。 |
| `rank_family` | 榜单族，当前主要为 `audience` 和 `guard`。 |
| `rank_type` | 榜单类型，例如在线榜、进房、日榜、周榜、月榜、陪伴榜等后端归一化值。 |
| `rank_switch` / `period_scope` | B站接口中的榜单切换参数和周期范围。 |
| `page_no` / `page_size` | 榜单分页。 |
| `total_count` / `count_text` / `value_text` | 榜单总数或接口返回的展示文本。 |
| `source_endpoint` | 来源接口。 |
| `signed_required` | 该次请求是否涉及签名请求策略。 |
| `captured_at` / `captured_bucket` | 采集时间和去重时间桶。 |
| `raw_payload_json` / `extension_json` | 原始响应和扩展数据。 |

去重策略：

- `(monitor_id, rank_family, rank_type, rank_switch, period_scope, captured_bucket, page_no)` 唯一，避免同一个时间桶内同页榜单重复写入。

## B站直播榜单明细表

表名：`bilibili_live_rank_entry`。

用途：保存某个榜单快照下的用户条目，例如在线榜贡献值、大航海舰长/提督/总督排行、陪伴天数等。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `snapshot_id` | 关联 `bilibili_live_rank_snapshot.id`，删除快照时级联删除明细。 |
| `monitor_id` / `room_id` / `ruid` | 冗余直播间监控、房间号和主播 UID，便于查询。 |
| `user_uid` | 榜单用户 UID，如接口提供。 |
| `rank_no` | 排名。 |
| `entry_kind` | 条目类型，区分贡献、进房、守护、陪伴等。 |
| `display_name` / `face_url` | 榜单用户昵称和头像。 |
| `score` | 贡献值或榜单分数。 |
| `guard_level` / `guard_expired_text` / `accompany_days` | 大航海等级、到期文本和陪伴天数等字段。 |
| `wealth_level` / `medal_name` / `medal_level` | 财富等级、粉丝牌名称和等级。 |
| `raw_entry_json` | 原始条目 JSON。 |

索引：

- `(snapshot_id, rank_no NULLS LAST)` 用于读取单个快照下排序后的榜单。
- `(user_uid, room_id, created_at DESC)` 用于后续按用户追踪榜单出现情况。

## 指定用户 Subject 表

表名：`monitored_subject`。

用途：保存“指定用户监控工作台”的聚合对象。Subject 不复制粉丝/直播历史数据，只保存聚合对象的显示信息、健康状态和绑定关系。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `id` | Subject 主键。 |
| `display_name` | 工作台显示名。创建时优先来自 B站粉丝监控昵称，其次来自直播间主播昵称。 |
| `avatar_url` | 工作台头像。创建或访问列表/详情/workbench 时会尝试自动回填。 |
| `remark` | 备注。 |
| `tags_json` | 标签数组。 |
| `monitor_status` | `ACTIVE` 或 `PAUSED`。 |
| `health_score` | 聚合健康分。 |
| `last_success_at` | 最近一次底层模块成功采集时间。 |
| `last_event_at` | 最近事件时间。 |
| `created_at` / `updated_at` | 创建和更新时间。 |

## Subject B站绑定表

表名：`subject_bilibili_binding`。

用途：把 Subject 和已有 B站粉丝监控、直播间监控连接起来。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `subject_id` | 关联 `monitored_subject.id`，删除 Subject 时级联删除绑定。 |
| `bilibili_user_monitor_id` | 关联 `bilibili_monitored_user.id`，底层粉丝监控删除时置空。 |
| `bilibili_live_room_monitor_id` | 关联 `bilibili_live_room_monitor.id`，底层直播间监控删除时置空。 |
| `mid` | B站 UID。 |
| `room_id` | 直播间真实房间号。 |
| `enabled_capabilities_json` | 当前默认包含 `follower`、`live_heat`。 |
| `danmu_enabled` | 是否启用弹幕模块。 |

注意：删除 Subject 不会删除被绑定的底层粉丝监控或直播间监控，只删除 Subject 自身、绑定和布局。删除底层监控后，绑定表对应外键会置空。

## Subject Widget 布局表

表名：`subject_widget_layout`。

用途：保存用户工作台卡片布局和设置。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `subject_id` | 关联 Subject。 |
| `widget_key` | Widget 标识，例如 `bilibili-follower-live-heat`、`bilibili-live-danmu`。 |
| `enabled` | 是否显示。 |
| `position_json` | 位置和尺寸配置。 |
| `settings_json` | Widget 私有设置。 |

## B站直播弹幕 Session 表

表名：`bilibili_live_danmaku_session`。

用途：记录每次弹幕 WebSocket 连接。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `live_room_monitor_id` | 关联直播间监控。 |
| `room_id` | 直播间房间号。 |
| `started_at` / `ended_at` | 连接开始和结束时间。 |
| `status` | `CONNECTING`、`AUTHENTICATING`、`CONNECTED`、`STOPPED`、`CLOSED`、`ERROR` 等。 |
| `connect_host` | 实际连接的 WebSocket host。 |
| `last_heartbeat_at` | 最近心跳时间。 |
| `last_error_type` / `last_error_message` | 最近错误信息。 |

## B站直播弹幕指标桶表

表名：`bilibili_live_danmaku_metric_bucket`。

用途：按时间桶保存弹幕数、点赞增量、看过人数、心跳热度、礼物和 SC 等指标。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `live_room_monitor_id` | 关联直播间监控。 |
| `session_id` | 关联弹幕连接 session，可为空。 |
| `bucket_start` | 指标桶开始时间。 |
| `bucket_seconds` | 指标桶粒度，默认 `60` 秒。 |
| `danmu_count` | 该桶内弹幕数量。 |
| `like_count` / `like_increment` | 点赞总量和增量。 |
| `watched_count` | 看过人数。 |
| `heartbeat_popularity` | 心跳包热度。 |
| `gift_count` / `super_chat_count` | 礼物和 SC 计数。 |
| `raw_event_count` | 原始事件计数。 |

去重策略：

- `UNIQUE (live_room_monitor_id, bucket_start, bucket_seconds)`，同一个直播间同一时间桶累加指标，避免重复行污染趋势。

## B站直播最近弹幕表

表名：`bilibili_live_danmaku_recent`。

用途：保存每个直播间最近一批弹幕，用于工作台实时列表。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `live_room_monitor_id` | 关联直播间监控。 |
| `room_id` | 直播间房间号。 |
| `message_text` | 弹幕文本。 |
| `display_name` | 发送者昵称。项目不主动脱敏；当前弹幕链路优先复用已保存登录态获取完整昵称，失败回退游客态；如果 B站只给脱敏名，后端会在新弹幕带 UID 时尝试补全。 |
| `medal_name` | 粉丝牌名称，如弹幕包提供。 |
| `sent_at` | 弹幕发送时间。 |

保留策略：

- `BilibiliLiveDanmakuRepository.trimRecent` 按配置保留最近 N 条，默认来自 `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_RECENT_LIMIT=200`。
- 旧历史弹幕如果入库时没有 UID 或完整昵称，后续无法保证恢复，只能继续展示当时保存的 `display_name`。

## B站 Web 登录态凭据

主要表：`platform_credential` 和 `platform_account`。

用途：保存用户主动扫码授权后的 B站 Web Cookie 登录态，供登录态面板、弹幕 `getDanmuInfo` 和 WebSocket 鉴权优先复用。

关键约定：

| 字段/约束 | 说明 |
| --- | --- |
| `auth_type='BILIBILI_WEB_COOKIE'` | 当前 B站 Web Cookie 登录态类型。 |
| `encrypted_payload` | 使用 AES-GCM 保存 Cookie、CSRF、refreshToken 等登录态字段，不应明文落库。 |
| `status='ACTIVE'` | 当前有效凭据。 |
| `ux_platform_credential_bilibili_web_active` | `V7` 增加的唯一索引，限制同一平台同一登录态类型只有一条 ACTIVE 凭据。 |
| `idx_platform_credential_platform_auth_status` | 按平台、类型、状态快速查询凭据。 |

注意：

- 登录态字段按当前开发期约定可由接口完整返回和展示，但不要写入日志、截图或文档。
- 生产部署前必须给 `/api/bilibili/auth/**` 加管理员鉴权，并重新评估登录态展示范围。

## 通用采集日志

B站粉丝接口调用会写入通用 `api_call_log` 表，相关代码在 [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/repository/BilibiliFollowerMonitorRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/repository/BilibiliFollowerMonitorRepository.java)。直播监控当前主要使用业务表上的错误字段和后端日志排查采集失败。

记录内容包括：

- `platform_id`
- `endpoint_key`
- `status_code`
- `duration_ms`
- `error_type`
- `retryable`
- 请求摘要
- 响应摘要

排查接口变化、限流、风控响应时，优先看后端日志和这张表。

## 本地数据目录

当前机器存在：

- `../social-data-monitor/.dev-data/postgres`
- `../social-data-monitor/.dev-data/postgres.log`
- `../social-data-monitor/.dev-tools/postgresql/pgsql/bin/pg_ctl.exe`

这些是本地便携 PostgreSQL 运行态，不是跨机器保证存在的源码。换机器时按 [`runbook.md`](runbook.md) 创建 PostgreSQL 数据库。

## 数据兼容注意事项

- `V2` 初版采集间隔约束曾经是 `>= 300` 秒，`V3` 已经改成 `1` 到 `2592000` 秒。新数据库会顺序应用两份迁移，最终以 `V3` 为准。
- 已有监控用户如果缺少头像，下一次成功 card 采集会更新 `avatar_url`。如果只走 `relation/stat` 兜底，则会保留已有头像，不会补新头像。
- 删除用户会级联删除历史快照；暂停监控请使用 `PAUSED` 状态。
- 删除直播间监控会级联删除直播快照和状态事件；暂停监控请使用 `PAUSED` 状态。
- 删除直播间监控也会级联删除弹幕 session、指标桶和最近弹幕。
- 删除 Subject 只删除聚合层数据，不删除底层 B站粉丝监控、直播间监控和它们的历史数据。
- 原始响应保存在业务表和通用日志里，后续如涉及隐私字段扩展，需重新评估存储范围。
- 2026-06-13 后端新增弹幕昵称补全逻辑，但旧的 `bilibili_live_danmaku_recent.display_name` 如果已经只保存脱敏名且没有 UID，无法可靠恢复。
