# 架构说明

最后更新：2026-06-19

## 技术栈

后端：

- Spring Boot 3.3.6。
- Java 17。
- Spring Web、Validation、Security、Actuator。
- MyBatis-Plus 3.5.9，但当前 B站监控主要使用 `NamedParameterJdbcTemplate`。
- Flyway。
- PostgreSQL。
- springdoc-openapi。

前端：

- Vue 3。
- Vite 6。
- TypeScript。
- Vue Router。
- Pinia。
- Element Plus。
- ECharts。
- Axios。

## 后端结构

入口：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/SocialDataMonitorApplication.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/SocialDataMonitorApplication.java)

通用层：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/common/`](../social-data-monitor/backend/src/main/java/com/socialmonitor/common/)：统一响应、错误码、业务异常、全局异常处理。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/security/`](../social-data-monitor/backend/src/main/java/com/socialmonitor/security/)：开发期安全配置。当前 `/api/**`、健康检查和 Swagger 都放行。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/config/`](../social-data-monitor/backend/src/main/java/com/socialmonitor/config/)：CORS、OpenAPI、采集调度配置。

平台和采集抽象：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/platform/`](../social-data-monitor/backend/src/main/java/com/socialmonitor/platform/)：平台适配器、能力枚举、风控等级、抓取结果模型。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/collector/`](../social-data-monitor/backend/src/main/java/com/socialmonitor/collector/)：采集任务、限频、重试、原始载荷、API 调用日志。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/ingestion/`](../social-data-monitor/backend/src/main/java/com/socialmonitor/ingestion/)：归一化接口和注册表。

B站模块：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/client/BilibiliApiClient.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/client/BilibiliApiClient.java)：B站公开接口调用和解析。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/service/BilibiliFollowerMonitorService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/service/BilibiliFollowerMonitorService.java)：添加用户、更新配置、采集、历史趋势和错误处理。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/service/BilibiliFollowerMonitorScheduler.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/service/BilibiliFollowerMonitorScheduler.java)：定时扫描到期用户并采集。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/repository/BilibiliFollowerMonitorRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/repository/BilibiliFollowerMonitorRepository.java)：B站监控用户和历史快照读写。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/controller/BilibiliFollowerMonitorController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/controller/BilibiliFollowerMonitorController.java)：前端使用的 REST API。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/adapter/BilibiliPlatformAdapter.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/adapter/BilibiliPlatformAdapter.java)：接入通用平台适配器抽象的 B站实现。

B站直播模块：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/client/BilibiliLiveApiClient.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/client/BilibiliLiveApiClient.java)：B站直播匿名公开接口调用和解析。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/service/BilibiliLiveMonitorService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/service/BilibiliLiveMonitorService.java)：添加直播间、更新配置、采集快照、状态事件、趋势组装和错误处理。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/service/BilibiliLiveMonitorScheduler.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/service/BilibiliLiveMonitorScheduler.java)：定时扫描到期直播间并采集。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/repository/BilibiliLiveMonitorRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/repository/BilibiliLiveMonitorRepository.java)：直播间监控、快照和事件读写。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/controller/BilibiliLiveMonitorController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/controller/BilibiliLiveMonitorController.java)：直播监控 REST API。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/config/BilibiliLiveMonitorProperties.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/config/BilibiliLiveMonitorProperties.java)：直播监控采集间隔、批量大小、超时、重试和请求头配置。

B站直播弹幕模块：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/service/BilibiliLiveDanmakuService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/service/BilibiliLiveDanmakuService.java)：WebSocket 连接、手动/自动启动、状态查询、最近弹幕、指标桶聚合。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuPacketCodec.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuPacketCodec.java)：B站弹幕包编码/解码，兼容 `protover=0/1/2/3`，其中 `2` 为 zlib，`3` 为 brotli。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuEventParser.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuEventParser.java)：解析 `DANMU_MSG`、点赞、看过人数、礼物等事件。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/repository/BilibiliLiveDanmakuRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/repository/BilibiliLiveDanmakuRepository.java)：弹幕 session、指标桶和最近弹幕读写。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/controller/BilibiliLiveDanmakuController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/controller/BilibiliLiveDanmakuController.java)：弹幕启动、停止、状态、最近消息、指标 API。

B站直播榜单模块：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/client/BilibiliLiveRankApiClient.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/client/BilibiliLiveRankApiClient.java)：房间观众榜和大航海榜外部接口调用与解析。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/service/BilibiliLiveRankService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/service/BilibiliLiveRankService.java)：榜单刷新、快照保存、summary/latest 视图组装和错误收集。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/repository/BilibiliLiveRankRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/repository/BilibiliLiveRankRepository.java)：`bilibili_live_rank_snapshot` 和 `bilibili_live_rank_entry` 读写。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/controller/BilibiliLiveRankController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/controller/BilibiliLiveRankController.java)：直播榜单 summary、latest、refresh API。

Subject 聚合层：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/controller/SubjectController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/controller/SubjectController.java)：用户监控 REST API。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectService.java)：Subject 增删改、B站绑定、布局配置、头像回填。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectWorkbenchService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectWorkbenchService.java)：聚合粉丝、直播热度、弹幕、健康事件和趋势数据。
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/repository/SubjectRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/repository/SubjectRepository.java)：`monitored_subject`、`subject_bilibili_binding`、`subject_widget_layout` 读写。

## B站粉丝监控链路

```text
前端 BilibiliView
  -> frontend/src/api/bilibili.ts
  -> /api/bilibili/follower-monitor/*
  -> BilibiliFollowerMonitorController
  -> BilibiliFollowerMonitorService
  -> RateLimitService
  -> BilibiliApiClient
  -> B站公开接口
  -> BilibiliFollowerMonitorRepository
  -> PostgreSQL
```

添加用户时：

1. 前端提交 `mid` 和 `intervalSeconds`。
2. 后端校验 `mid >= 1`、`intervalSeconds >= 1`，并按配置夹在最小值和最大值之间。
3. `BilibiliApiClient.fetchUserCard(mid)` 请求 `x/web-interface/card`。
4. 解析 `data.card.name`、`data.card.face`、`data.follower` 等字段。
5. 写入或更新 `bilibili_monitored_user`。
6. 写入 `bilibili_follower_snapshot`。

定时采集时：

1. `BilibiliFollowerMonitorScheduler` 每隔 `scheduler-delay-ms` 扫描到期用户。
2. `repository.findDueUsers(...)` 按 `next_collect_at` 找到 ACTIVE 用户。
3. 服务层请求 card 接口；若 card 在解析、网络、服务端错误时失败，会尝试 `x/relation/stat` 作为粉丝数兜底。
4. 成功后更新用户当前状态、下一次采集时间和历史快照。
5. 失败后记录错误类型、错误信息和失败退避后的下一次采集时间。

## B站直播监控链路

```text
前端 BilibiliLiveView
  -> frontend/src/api/bilibiliLive.ts
  -> /api/bilibili/live-monitor/*
  -> BilibiliLiveMonitorController
  -> BilibiliLiveMonitorService
  -> BilibiliLiveApiClient
  -> B站直播公开接口
  -> BilibiliLiveMonitorRepository
  -> PostgreSQL
```

添加直播间时：

1. 前端提交 `roomId` 或 `uid`，以及可选 `intervalSeconds`。
2. 后端校验 `roomId >= 1` 或 `uid >= 1`，`intervalSeconds` 夹在 `1` 到 `2592000` 秒之间。
3. 如果输入房间号，先通过 `room/v1/Room/room_init` 转换真实房间号和主播 UID。
4. 通过直播接口获取房间标题、封面、关键帧、分区、直播状态、在线/热度等信息。
5. 写入或更新 `bilibili_live_room_monitor`。
6. 写入 `bilibili_live_room_snapshot`，并按快照差异写入 `bilibili_live_status_event`。

定时采集时：

1. `BilibiliLiveMonitorScheduler` 每隔 `scheduler-delay-ms` 扫描到期直播间。
2. `repository.findDueRooms(...)` 按 `next_collect_at` 找到 ACTIVE 直播间。
3. 服务层优先批量按 UID 查询直播状态，再按需补单房间详情。
4. 成功后更新当前状态、下一次采集时间和历史快照。
5. 失败后记录错误类型、错误信息和失败退避后的下一次采集时间。

## B站指定用户工作台链路

```text
前端 SubjectListView / SubjectWorkbenchView
  -> frontend/src/api/subjects.ts
  -> /api/subjects/*
  -> SubjectController
  -> SubjectService / SubjectWorkbenchService
  -> 复用 BilibiliFollowerMonitorService / BilibiliLiveMonitorService / BilibiliLiveDanmakuService
  -> SubjectRepository + 既有 B站业务 repository
  -> PostgreSQL
```

创建 Subject 时：

1. 前端提交 `mid`、可选 `roomId`、可选 `danmuEnabled`。
2. `SubjectService` 优先复用已有 `bilibili_monitored_user`；如果没有，则调用粉丝监控服务创建用户。
3. 如果有 `roomId`，优先复用或创建对应直播间监控；如果只有 `mid`，尝试按 UID 复用或创建直播间监控。
4. 如果直播间监控创建失败，不阻塞 Subject 创建，后端写 warning，工作台仍显示粉丝数据。
5. Subject 的昵称和头像优先来自粉丝监控，其次来自直播间监控。
6. 工作台使用已有快照表和弹幕表聚合指标，不复制历史趋势数据。

弹幕实时链路：

```text
前端 BilibiliLiveDanmuWidget
  -> 每 2 秒轮询 /danmaku/status 和 /danmaku/recent
  -> BilibiliLiveDanmakuController
  -> BilibiliLiveDanmakuService
  -> Java WebSocket client
  -> B站弹幕 WebSocket
  -> 指标桶 + 最近弹幕表
```

说明：

- 后端连接支持自动协议候选，也支持前端手动指定 `protover=0/1/2/3`。
- `getDanmuInfo` 和 WebSocket 鉴权默认优先复用用户已扫码保存的 B站登录态，使用凭证里的 Cookie / buvid / UID 获取弹幕服务器和鉴权 UID；失败或触发风控时回退游客态。
- 可通过 `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_USE_LOGIN_CREDENTIAL=false` 关闭登录态复用，只使用游客态连接。
- 项目不实现验证码绕过、复杂风控绕过或登录态破解；只使用用户主动保存的正常登录态。
- 新弹幕昵称优先展示 B站返回的完整昵称；如果回退游客态或旧历史数据里只有脱敏昵称，仍可能显示“昵称待补全”。
- 用户工作台右侧 `BilibiliLiveDanmuWidget.vue` 现在还复用直播榜单 API 展示 `房间观众` 和 `大航海` 两类快照；这只是读取/刷新 `bilibili_live_rank_*` 表，不复制榜单数据到 Subject 表。

## API 路由

所有响应都使用 [`ApiResponse<T>`](../social-data-monitor/backend/src/main/java/com/socialmonitor/common/response/ApiResponse.java) 包装。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/bilibili/follower-monitor/users` | 获取监控用户列表，包含最近快照。 |
| `POST` | `/api/bilibili/follower-monitor/users` | 添加或重新启用监控用户。 |
| `PATCH` | `/api/bilibili/follower-monitor/users/{userId}/status` | 启用或停用。 |
| `PATCH` | `/api/bilibili/follower-monitor/users/{userId}/settings` | 更新采集间隔。 |
| `POST` | `/api/bilibili/follower-monitor/users/{userId}/refresh` | 立即采集。 |
| `DELETE` | `/api/bilibili/follower-monitor/users/{userId}` | 删除监控用户及其快照。 |
| `GET` | `/api/bilibili/follower-monitor/users/{userId}/history` | 获取单用户趋势数据。 |
| `GET` | `/api/bilibili/follower-monitor/trends` | 获取多个用户趋势数据。 |

直播监控 API：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/bilibili/live-monitor/rooms` | 获取直播间监控列表，包含近期趋势点。 |
| `POST` | `/api/bilibili/live-monitor/rooms` | 按 `roomId` 或 `uid` 添加直播间监控。 |
| `PATCH` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}` | 更新采集间隔或启用状态。 |
| `DELETE` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}` | 删除直播间监控及其快照、事件。 |
| `POST` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}/refresh` | 立即采集直播间。 |
| `GET` | `/api/bilibili/live-monitor/summary` | 获取直播间数量、直播中数量、异常数、今日开播数等摘要。 |
| `GET` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}/trends` | 获取单直播间趋势数据。 |
| `GET` | `/api/bilibili/live-monitor/trends` | 获取多个直播间趋势数据。 |
| `GET` | `/api/bilibili/live-monitor/events` | 获取直播状态事件。 |

直播榜单 API：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/summary` | 获取房间观众和大航海最新榜单摘要，优先读取已保存快照。 |
| `GET` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/latest` | 按 `family`、`type`、`rankSwitch` 查询最新单类榜单快照。 |
| `POST` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/refresh` | 手动刷新榜单快照，可传 `families`、`maxPages`、`force`。 |

弹幕 API：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku/start` | 手动启动弹幕 WebSocket，可带 `protocolVersion=0/1/2/3`。 |
| `POST` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku/stop` | 停止弹幕 WebSocket。 |
| `GET` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku/status` | 查询弹幕连接和分钟级统计。 |
| `GET` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku/recent` | 查询最近弹幕。 |
| `GET` | `/api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku/metrics` | 查询弹幕指标桶。 |

Subject API：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/subjects` | 获取用户监控 Subject 列表。 |
| `POST` | `/api/subjects` | 创建 Subject，可传 `mid` 自动绑定 B站粉丝/直播监控。 |
| `GET` | `/api/subjects/{subjectId}` | 获取 Subject 基础信息。 |
| `PATCH` | `/api/subjects/{subjectId}` | 更新 Subject 昵称、头像、备注、标签或启用状态。 |
| `DELETE` | `/api/subjects/{subjectId}` | 删除 Subject 及其绑定和布局；不会删除被绑定的底层粉丝/直播监控。 |
| `POST` | `/api/subjects/{subjectId}/bilibili-binding` | 创建或更新 B站绑定。 |
| `PATCH` | `/api/subjects/{subjectId}/bilibili-binding` | 更新 B站绑定。 |
| `GET` | `/api/subjects/{subjectId}/workbench` | 获取工作台聚合数据。 |
| `GET` | `/api/subjects/{subjectId}/trends` | 获取 Subject 趋势数据，默认 `metrics=follower,live_online&range=24h&bucket=5m`。 |
| `PUT` | `/api/subjects/{subjectId}/layout` | 保存 Widget 布局配置。 |

其他已存在 API：

- `/api/dev/health`
- `/api/dev/overview`
- `/api/analytics/summary`
- `/api/platforms/adapters`
- `/api/collect/tasks/run-once`
- `/api/ai/mock-summary`

## 前端结构

入口：

- [`../social-data-monitor/frontend/src/main.ts`](../social-data-monitor/frontend/src/main.ts)
- [`../social-data-monitor/frontend/src/App.vue`](../social-data-monitor/frontend/src/App.vue)
- [`../social-data-monitor/frontend/src/layouts/MainLayout.vue`](../social-data-monitor/frontend/src/layouts/MainLayout.vue)

路由：

- [`../social-data-monitor/frontend/src/router/index.ts`](../social-data-monitor/frontend/src/router/index.ts)

主要页面：

- `/dashboard`：概览。
- `/bilibili`：B站粉丝趋势监控。
- `/bilibili/live`：B站直播间监控。
- `/subjects`：B站指定用户监控列表。
- `/subjects/:subjectId`：指定用户聚合工作台。
- `/platform`、`/tasks`、`/data`、`/analytics`、`/ai`、`/identity`、`/settings`：多平台监控规划下的页面骨架或基础功能。

B站粉丝页面：

- [`../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue`](../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue)
- [`../social-data-monitor/frontend/src/api/bilibili.ts`](../social-data-monitor/frontend/src/api/bilibili.ts)
- [`../social-data-monitor/frontend/src/components/charts/TrendChart.vue`](../social-data-monitor/frontend/src/components/charts/TrendChart.vue)

B站直播页面：

- [`../social-data-monitor/frontend/src/views/bilibili-live/BilibiliLiveView.vue`](../social-data-monitor/frontend/src/views/bilibili-live/BilibiliLiveView.vue)
- [`../social-data-monitor/frontend/src/api/bilibiliLive.ts`](../social-data-monitor/frontend/src/api/bilibiliLive.ts)
- 直播页面目前自带 ECharts 图表配置、浅色/深色主题、横向卡片和详情区交互。

用户监控页面：

- [`../social-data-monitor/frontend/src/views/subjects/SubjectListView.vue`](../social-data-monitor/frontend/src/views/subjects/SubjectListView.vue)
- [`../social-data-monitor/frontend/src/views/subjects/SubjectWorkbenchView.vue`](../social-data-monitor/frontend/src/views/subjects/SubjectWorkbenchView.vue)
- [`../social-data-monitor/frontend/src/views/subjects/components/SubjectHeader.vue`](../social-data-monitor/frontend/src/views/subjects/components/SubjectHeader.vue)
- [`../social-data-monitor/frontend/src/views/subjects/components/SubjectWidgetBoard.vue`](../social-data-monitor/frontend/src/views/subjects/components/SubjectWidgetBoard.vue)
- [`../social-data-monitor/frontend/src/views/subjects/widgets/BilibiliFollowerLiveHeatWidget.vue`](../social-data-monitor/frontend/src/views/subjects/widgets/BilibiliFollowerLiveHeatWidget.vue)
- [`../social-data-monitor/frontend/src/views/subjects/widgets/BilibiliLiveDanmuWidget.vue`](../social-data-monitor/frontend/src/views/subjects/widgets/BilibiliLiveDanmuWidget.vue)
- [`../social-data-monitor/frontend/src/api/subjects.ts`](../social-data-monitor/frontend/src/api/subjects.ts)

其中 `BilibiliLiveDanmuWidget.vue` 是用户工作台右侧复合卡片：`弹幕` 视图消费弹幕 API，`房间观众` 和 `大航海` 视图消费直播榜单 API。

## 设计边界

- `BilibiliApiClient` 只负责外部接口、响应解析和外部错误归类。
- `BilibiliFollowerMonitorService` 负责业务策略：间隔校验、失败兜底、退避、快照写入、列表和趋势组装。
- `BilibiliFollowerMonitorRepository` 负责 SQL，不应放业务策略。
- `BilibiliLiveApiClient` 只负责直播外部接口、响应解析和外部错误归类；不要把登录态、验证码或复杂风控绕过放进这里。
- `BilibiliLiveMonitorService` 负责直播监控业务策略：间隔校验、批量采集、失败退避、快照写入和事件生成。
- `BilibiliLiveMonitorRepository` 负责 SQL，不应放业务策略。
- `BilibiliLiveDanmakuService` 负责 WebSocket 生命周期、协议候选、弹幕指标和最近消息，不应承担直播房间资料采集职责。
- `BilibiliLiveRankService` 负责直播房间观众和大航海榜单快照，不应承担弹幕 WebSocket 生命周期或直播间基础状态采集职责。
- `SubjectService` 只负责 Subject 生命周期、B站绑定和布局；底层粉丝/直播监控仍由各自 service 维护。
- `SubjectWorkbenchService` 负责聚合视图，不复制底层历史数据。
- 前端 `BilibiliView.vue` 当前承担页面状态、布局切换和局部子组件定义；如果页面继续膨胀，下一步可以把 `UserHeader`、`IntervalEditor`、`UserActions` 拆成独立组件，但不要在没有需求时大拆。
- 前端 `BilibiliLiveView.vue` 当前是直播监控主界面，样式细节近期改动较多；继续优化视觉时优先小范围改 CSS 和局部计算逻辑。
- 前端 `subjects/` 已经拆成工作台组件，继续优化用户监控时优先按组件边界修改，不要回到单文件大页面。
