# 当前项目工程详细总结

最后更新：2026-07-06

本文是 `.` 当前工程的一页式深度总结，面向“下次的我”和“下次接手的 Codex”。它不替代已有专题文档，而是把当前真实工程状态、运行入口、代码结构、核心链路、数据表、API、前端页面和已知风险串起来。

## 结论先行

- 当前真正可运行的应用在 `social-data-monitor/`。
- 技术栈是 Spring Boot 3.3.6 + PostgreSQL + Flyway + MyBatis-Plus，前端是 Vue 3 + Vite + TypeScript + Element Plus + ECharts。
- 当前已完整落地三条 B站主线：
  - `/bilibili`：B站用户粉丝趋势监控。
  - `/bilibili/live`：B站直播间监控、榜单、房间详情和直播弹幕辅助能力。
  - `/subjects`、`/subjects/:subjectId`：指定用户聚合工作台，把同一 B站用户的粉丝、直播热度、弹幕、榜单和采集健康聚合展示。
- B站扫码登录态已经接入 `/bilibili` 页面和 `/api/bilibili/auth/**`，可保存 Cookie、CSRF、refreshToken，弹幕 `getDanmuInfo` 会优先复用登录态，失败时回退游客态。
- 弹幕 WebSocket 支持 `protover=0/1/2/3`，自动模式候选顺序优先配置值、`3`、`2`、`1`、`0`。
- 直播间榜单已支持房间观众和大航海数据，榜单刷新接口前端超时时间单独放宽到 120 秒。
- 采集间隔配置范围大，粉丝和直播监控都支持最小 `1` 秒、最大 `2592000` 秒，但仍有全局请求间隔和失败退避。
- 根目录当前不是 Git 仓库，`git status --short` 返回 `fatal: not a git repository`。如果要提交，请先确认真实版本控制位置。
- 当前后端 `/api/**` 开发期放行，没有生产级鉴权。登录态完整字段也可在本机页面查看并一键复制，生产前必须加权限保护。

## 仓库目录

```text
.
├── bilibili-api-collect-new-research/     B站接口文档本地副本
├── design-mockups/                        早期/辅助视觉稿
├── docs/                                  根级交接文档和接口研究
├── external-research/                     第三方项目或调研副本
├── multi-social-platform-monitoring/      早期多平台监控方案文档
├── social-data-monitor/                   当前可运行主工程
└── package.json                           根目录占位 package，无实际运行脚本
```

重点目录：

- `social-data-monitor/backend/`：Spring Boot 后端。
- `social-data-monitor/frontend/`：Vue 前端。
- `social-data-monitor/scripts/`：本地启动/停止脚本。
- `social-data-monitor/.dev-tools/`：本机便携 PostgreSQL 工具。
- `social-data-monitor/.dev-data/`：本机 PostgreSQL 数据、后端/前端日志。
- `docs/`：根级文档地图、运行指南、架构、数据模型、B站接口研究、交接提示。
- `bilibili-api-collect-new-research/repo/docs/`：接口字段和请求参数的原始参考来源。

## 运行方式

推荐一键启动：

```powershell
cd social-data-monitor
.\scripts\dev-start.cmd
```

启动成功后访问：

```text
http://127.0.0.1:5173/bilibili
http://127.0.0.1:5173/bilibili/live
http://127.0.0.1:5173/subjects
```

停止：

```powershell
cd social-data-monitor
.\scripts\dev-stop.cmd
```

脚本行为：

- `scripts/dev-start.ps1` 会先检查 `5432`，未监听时启动 `.dev-tools/postgresql/pgsql/bin/pg_ctl.exe`。
- 后端 `8080` 和前端 `5173` 会并行启动。
- 默认等待 `http://127.0.0.1:8080/actuator/health` 和 `http://127.0.0.1:5173/bilibili` 可访问。
- 可用 `.\scripts\dev-start.cmd -NoWait` 只发起启动，不等待健康检查。
- `scripts/dev-stop.ps1` 会停止当前项目路径下的前端、后端和便携 PostgreSQL。

日志位置：

```text
social-data-monitor\.dev-data\postgres.log
social-data-monitor\.dev-data\backend-dev.log
social-data-monitor\.dev-data\backend-dev.err.log
social-data-monitor\.dev-data\frontend-dev.log
social-data-monitor\.dev-data\frontend-dev.err.log
```

## 技术栈

后端：

- Java 17 编译目标。
- Spring Boot 3.3.6。
- Spring Web、Security、Validation、Scheduling、Actuator。
- MyBatis-Plus 3.5.9。
- Flyway + PostgreSQL。
- springdoc OpenAPI。
- `org.brotli:dec:0.1.2`，用于 B站直播弹幕 `protover=3` Brotli 解包。

前端：

- Vue 3.5.13。
- Vite 6.4.3。
- TypeScript 5.6.3。
- Vue Router 4。
- Pinia。
- Element Plus 2.9.1。
- ECharts 5.5.1。
- Axios。
- qrcode。

## 后端配置

主要配置文件：

- `social-data-monitor/backend/src/main/resources/application.yml`
- `social-data-monitor/backend/src/main/resources/application-dev.yml`

默认 profile 是 `dev`。数据库默认连接：

```yaml
spring:
  datasource:
    url: ${SOCIAL_MONITOR_DB_URL}
    username: ${SOCIAL_MONITOR_DB_USERNAME}
    password: ${SOCIAL_MONITOR_DB_PASSWORD}
```

关键环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SOCIAL_MONITOR_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 前端允许来源。 |
| `SOCIAL_MONITOR_COLLECTOR_SCHEDULER_ENABLED` | `false` | 通用采集调度默认关闭。 |
| `SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY` | 空 | 生产应设置 base64 32 字节密钥。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_INTERVAL_SECONDS` | `3600` | 粉丝监控新增默认采集间隔。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_MIN_INTERVAL_SECONDS` | `1` | 粉丝监控最小采集间隔。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_MAX_INTERVAL_SECONDS` | `2592000` | 粉丝监控最大采集间隔。 |
| `SOCIAL_MONITOR_BILIBILI_REQUEST_MIN_INTERVAL_MS` | `1500` | 粉丝接口全局最小请求间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_INTERVAL_SECONDS` | `300` | 直播监控新增默认采集间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_MIN_INTERVAL_SECONDS` | `1` | 直播监控最小采集间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_MAX_INTERVAL_SECONDS` | `2592000` | 直播监控最大采集间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_REQUEST_MIN_INTERVAL_MS` | `1500` | 直播接口全局最小请求间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_PROTOCOL_VERSION` | `3` | 默认弹幕协议版本。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_USE_LOGIN_CREDENTIAL` | `true` | 弹幕信息流优先复用扫码登录态。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_RECENT_LIMIT` | `200` | 每个房间保留最近弹幕条数。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_RANK_PAGE_SIZE` | `50` | 房间观众榜单页大小。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_RANK_GUARD_PAGE_SIZE` | `30` | 大航海榜单页大小。 |

安全配置：

- `SecurityConfig` 当前对 `/api/**`、Swagger、Actuator 健康等开发接口 `permitAll`。
- CSRF 关闭，CORS 开启。
- 开发内存账号从本地 `SOCIAL_MONITOR_SECURITY_DEV_USERNAME` / `SOCIAL_MONITOR_SECURITY_DEV_PASSWORD` 读取，但当前 API 没有强制登录。
- 生产前必须补管理员鉴权，尤其是 `/api/bilibili/auth/**`。

## 数据库和 Flyway

迁移目录：

```text
social-data-monitor/backend/src/main/resources/db/migration/
```

当前迁移：

| 迁移 | 作用 |
| --- | --- |
| `V1__init_schema.sql` | 基础平台、采集、原始载荷、社媒标准模型、AI、Identity 等表。 |
| `V2__bilibili_follower_monitor.sql` | B站粉丝监控用户和粉丝快照。 |
| `V3__bilibili_interval_range.sql` | 扩大采集间隔范围，支持 `1` 秒到 `2592000` 秒。 |
| `V4__bilibili_live_monitor.sql` | B站直播间监控、直播快照、状态事件。 |
| `V5__subject_monitor.sql` | 指定用户聚合层 Subject、B站绑定、组件布局。 |
| `V6__bilibili_live_danmaku_monitor.sql` | 弹幕 WebSocket 会话、指标桶、最近弹幕。 |
| `V7__bilibili_auth_credential.sql` | B站扫码登录态索引，复用 `platform_credential`。 |
| `V8__bilibili_live_rank_monitor.sql` | 直播间房间观众和大航海榜单快照、榜单明细。 |

关键业务表：

- `bilibili_monitored_user`：被监控的 B站 UID、昵称、头像、当前粉丝数、间隔、状态、错误信息。
- `bilibili_follower_snapshot`：粉丝数历史快照。
- `bilibili_live_room_monitor`：被监控直播间、主播 UID、标题、封面、分区、直播状态、热度、间隔、错误信息。
- `bilibili_live_room_snapshot`：直播状态和在线/热度历史快照。
- `bilibili_live_status_event`：开播、下播、标题变化等事件。
- `bilibili_live_danmaku_session`：弹幕 WebSocket 会话。
- `bilibili_live_danmaku_metric_bucket`：弹幕速率、点赞、看过人数、心跳人气等指标桶。
- `bilibili_live_danmaku_recent`：最近弹幕。
- `bilibili_live_rank_snapshot`：房间观众、大航海榜单快照。
- `bilibili_live_rank_entry`：榜单明细，包含用户昵称、头像、排名、贡献值、舰长等级、陪伴天数等。
- `monitored_subject`：指定用户聚合对象。
- `subject_bilibili_binding`：Subject 与粉丝监控、直播监控的绑定关系。
- `subject_widget_layout`：Subject 工作台组件布局。
- `platform_credential`、`platform_account`：扫码登录态和平台账号。
- `api_call_log`：接口调用日志。

## 后端模块地图

核心包：

```text
com.socialmonitor
├── common          通用响应、错误码、异常
├── security        CORS、Spring Security 开发期配置
├── platform        平台适配器、凭证和通用平台接口
├── collector       限频、重试、采集任务占位
├── bilibili        B站粉丝、直播、登录态、弹幕、榜单
├── subject         指定用户聚合工作台
├── analytics       分析摘要占位
├── ai              AI 摘要占位
└── admin           开发健康接口
```

B站粉丝链路：

- 控制器：`BilibiliFollowerMonitorController`
- 服务：`BilibiliFollowerMonitorService`
- 仓储：`BilibiliFollowerMonitorRepository`
- 客户端：`BilibiliApiClient`
- 主接口：`GET https://api.bilibili.com/x/web-interface/card?mid={mid}&photo=true`
- 兜底接口：`GET https://api.bilibili.com/x/relation/stat?vmid={mid}`

粉丝接口选择：

- 主接口 `x/web-interface/card` 同时拿到 `data.card.name`、`data.card.face`、`data.follower`、`data.card.fans`。
- 不需要登录、CSRF、WBI，低频公开轮询复杂度最低。
- `x/relation/stat` 只返回粉丝/关注数，不返回头像昵称，只作为已有用户刷新时的兜底。

B站直播链路：

- 控制器：`BilibiliLiveMonitorController`
- 服务：`BilibiliLiveMonitorService`
- 仓储：`BilibiliLiveMonitorRepository`
- 客户端：`BilibiliLiveApiClient`
- 房间号规范化：`room/v1/Room/room_init`
- 单房间详情：`room/v1/Room/get_info`
- 批量状态主入口：`room/v1/Room/get_status_info_by_uids`
- 旧接口兜底：`room/v1/Room/getRoomInfoOld`

直播弹幕链路：

- 控制器：`BilibiliLiveDanmakuController`
- 服务：`BilibiliLiveDanmakuService`
- 信息流配置客户端：`BilibiliLiveDanmuInfoClient`
- WBI：`BilibiliWbiSigner`
- 匿名 buvid：`BilibiliAnonymousCookieProvider`
- 解包：`BilibiliLiveDanmakuPacketCodec`
- 事件解析：`BilibiliLiveDanmakuEventParser`
- 接口：`GET https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo`
- WebSocket：`wss://broadcastlv.chat.bilibili.com/sub` 或接口返回的 host。

弹幕协议：

- `protover=0/1`：普通 JSON 包。
- `protover=2`：zlib 压缩命令包。
- `protover=3`：Brotli 压缩命令包。
- 自动模式会依次尝试配置值、`3`、`2`、`1`、`0`。
- `DANMU_MSG`、`WATCHED_CHANGE`、`LIKE_INFO_V3_UPDATE`、`ROOM_REAL_TIME_MESSAGE_UPDATE`、礼物和醒目留言等会被解析为最近弹幕或指标桶。
- 如果弹幕事件里有 UID，会尝试通过公开用户卡片接口补全昵称；没有 UID 的历史脱敏昵称无法可靠恢复。

直播榜单链路：

- 控制器：`BilibiliLiveRankController`
- 服务：`BilibiliLiveRankService`
- 仓储：`BilibiliLiveRankRepository`
- 客户端：`BilibiliLiveRankApiClient`
- 房间观众：`xlive/general-interface/v1/rank/queryContributionRank`，需要 WBI。
- 房间观众兜底：`xlive/general-interface/v1/rank/getOnlineGoldRank`。
- 大航海：`xlive/app-room/v2/guardTab/topListNew`。
- 默认支持在线贡献榜、进房时间榜、日榜、周榜、月榜、大航海周榜、月榜、陪伴榜。
- 返回给前端的 summary 默认合并最多 100 条明细。

登录态链路：

- 控制器：`BilibiliAuthController`
- 服务：`BilibiliAuthService`
- 客户端：`BilibiliPassportClient`
- 加密：`BilibiliCredentialCipher`
- 仓储：`BilibiliCredentialRepository`
- 前端入口：`/bilibili` 顶部的 `BilibiliAuthPanel`
- 支持扫码开始、轮询状态、状态查询、nav 校验、完整登录态读取、移除登录态。
- 完整登录态抽屉支持一键复制全部字段，只建议在可信本机环境使用。

Subject 聚合链路：

- 控制器：`SubjectController`
- 服务：`SubjectService`、`SubjectWorkbenchService`
- 仓储：`SubjectRepository`
- 创建 Subject 时，如果传 `mid`，会自动复用或创建粉丝监控，并尝试复用或创建直播间监控。
- 如果传 `roomId`，会自动复用或创建直播间监控。
- Subject 会尽量从粉丝监控或直播监控回填头像和展示名。
- 工作台趋势接口按时间桶合并粉丝快照和直播热度快照。

## 后端 API 摘要

粉丝监控：

- `GET /api/bilibili/follower-monitor/users`
- `POST /api/bilibili/follower-monitor/users`
- `PATCH /api/bilibili/follower-monitor/users/{userId}/status`
- `PATCH|PUT /api/bilibili/follower-monitor/users/{userId}/settings`
- `POST /api/bilibili/follower-monitor/users/{userId}/refresh`
- `DELETE /api/bilibili/follower-monitor/users/{userId}`
- `GET /api/bilibili/follower-monitor/users/{userId}/history`
- `GET /api/bilibili/follower-monitor/trends`

直播监控：

- `GET /api/bilibili/live-monitor/rooms`
- `POST /api/bilibili/live-monitor/rooms`
- `PATCH /api/bilibili/live-monitor/rooms/{roomMonitorId}`
- `DELETE /api/bilibili/live-monitor/rooms/{roomMonitorId}`
- `POST /api/bilibili/live-monitor/rooms/{roomMonitorId}/refresh`
- `GET /api/bilibili/live-monitor/summary`
- `GET /api/bilibili/live-monitor/rooms/{roomMonitorId}/trends`
- `GET /api/bilibili/live-monitor/trends`
- `GET /api/bilibili/live-monitor/events`

直播弹幕：

- `POST /api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku/start?protocolVersion={0|1|2|3}`
- `POST /api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku/stop`
- `GET /api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku/status`
- `GET /api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku/recent?limit={n}`
- `GET /api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku/metrics?range={range}`

直播榜单：

- `GET /api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/summary`
- `GET /api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/latest?family=&type=&rankSwitch=&limit=`
- `POST /api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/refresh`

登录态：

- `POST /api/bilibili/auth/qr/start`
- `GET /api/bilibili/auth/qr/{loginId}/status`
- `GET /api/bilibili/auth/status`
- `POST /api/bilibili/auth/refresh`
- `GET /api/bilibili/auth/credential`
- `DELETE /api/bilibili/auth`

Subject：

- `GET /api/subjects`
- `POST /api/subjects`
- `GET /api/subjects/{subjectId}`
- `PATCH /api/subjects/{subjectId}`
- `DELETE /api/subjects/{subjectId}`
- `POST /api/subjects/{subjectId}/bilibili-binding`
- `PATCH /api/subjects/{subjectId}/bilibili-binding`
- `GET /api/subjects/{subjectId}/workbench`
- `GET /api/subjects/{subjectId}/trends?metrics=follower,live_online&range=24h&bucket=5m`
- `PUT /api/subjects/{subjectId}/layout`

其他开发接口：

- `GET /api/dev/health`
- `GET /api/dev/overview`
- `GET /api/platforms/adapters`
- `POST /api/collect/tasks/run-once`
- `GET /api/analytics/summary`
- `POST /api/ai/mock-summary`

## 前端结构

路由文件：

- `social-data-monitor/frontend/src/router/index.ts`

当前主要路由：

- `/dashboard`
- `/bilibili`
- `/bilibili/live`
- `/subjects`
- `/subjects/:subjectId`
- `/platform`
- `/tasks`
- `/data`
- `/analytics`
- `/ai`
- `/identity`
- `/settings`

布局：

- `MainLayout.vue`：左侧菜单固定 236px，高度 `100vh`，右侧 `.main` 独立滚动。
- 菜单包含 Dashboard、Bilibili、直播监控、用户监控、平台管理、采集任务、数据中心、分析看板、AI 分析、Identity、系统设置。

前端 API 封装：

- `frontend/src/api/http.ts`：Axios 实例，默认 `baseURL` 为空，Vite 代理 `/api` 到后端。
- `frontend/src/api/bilibili.ts`：粉丝监控 API。
- `frontend/src/api/bilibiliLive.ts`：直播、弹幕、榜单 API。
- `frontend/src/api/bilibiliAuth.ts`：B站扫码登录 API。
- `frontend/src/api/subjects.ts`：Subject 聚合 API。

前端页面和组件：

- `frontend/src/views/bilibili/BilibiliView.vue`
  - 粉丝趋势监控。
  - 顶部登录态面板。
  - 横向用户小卡片。
  - 用户详情展开。
  - 选择最多 4 个用户查看趋势。
  - 趋势图支持拖动换位。
  - 浅色/深色主题，状态写入 localStorage。
  - 趋势图使用动态 x/y 轴、compact 数字格式。

- `frontend/src/views/bilibili-live/BilibiliLiveView.vue`
  - 直播间监控。
  - 横向直播间卡片。
  - 展开详情展示封面、房间字段、采集间隔、数据完整性。
  - 直播封面按图片自然比例展示，避免强行拉伸。
  - 榜单模块支持房间观众和大航海两列展示、排序方式、升降序、最多展示 100 条。
  - 支持手动刷新榜单，前端超时 120 秒。
  - 浅色/深色主题。

- `frontend/src/views/subjects/SubjectListView.vue`
  - 指定用户监控对象列表。
  - 创建 Subject。
  - 创建时可传 UID/房间信息，后端自动补齐粉丝和直播绑定。

- `frontend/src/views/subjects/SubjectWorkbenchView.vue`
  - 指定用户工作台。
  - 顶部用户信息和采集健康。
  - 四个 KPI：总粉丝数、直播间热度、弹幕速率、最近成功采集。
  - 绑定资源弹窗。
  - 下方由 `SubjectWidgetBoard` 编排组件。

- `frontend/src/views/subjects/components/SubjectHeader.vue`
  - 用户头像、UID、直播状态、模块数、弹幕状态、健康分。

- `frontend/src/views/subjects/components/SubjectWidgetBoard.vue`
  - 左侧粉丝/热度双指标趋势。
  - 右侧弹幕/榜单卡片。
  - 左侧主卡支持拖拽调整宽高，右侧同步跟随。

- `frontend/src/views/subjects/widgets/BilibiliFollowerLiveHeatWidget.vue`
  - ECharts 双 y 轴趋势图。
  - 粉丝数和直播热度按时间桶对齐。
  - 对小波动做动态 axis domain，避免过分夸张或完全看不出变化。

- `frontend/src/views/subjects/widgets/BilibiliLiveDanmuWidget.vue`
  - 可切换 `弹幕`、`房间观众`、`大航海` 三种数据视图。
  - 弹幕视图每 2 秒轮询状态和最近弹幕。
  - 鼠标悬停整个弹幕监控区域时暂停自动追最新，移出后恢复。
  - 支持手动开启未开播房间的弹幕监听。
  - 榜单视图复用直播榜单 summary/refresh API，不复制数据到 Subject 表。

- `frontend/src/views/subjects/widgets/SubjectHealthEventWidget.vue`
  - 展示采集健康和最近事件。

## 当前功能状态

### B站粉丝趋势监控

已实现：

- 添加/删除/启用/停用多个 UID。
- 采集 UID、昵称、头像、粉丝数、关注数。
- 单用户采集间隔可配置，最小 `1` 秒，最大约 30 天。
- 定时采集并保存历史快照。
- 手动刷新。
- 多用户横向卡片，小卡片 + 详情展开。
- 用户主页跳转。
- 最多选择 4 个用户绘制趋势图。
- 趋势图拖动换位。
- 浅色/深色主题。
- B站登录态面板和完整字段复制。

注意：

- `1` 秒间隔是配置下限，不等于每个用户严格每秒请求。全局请求间隔和失败退避仍会保护外部接口。
- 趋势图为了视觉可读做了动态缩放，仍可能需要根据真实数据继续调参。

### B站直播间监控

已实现：

- 按房间号或 UID 添加直播间。
- 房间号规范化，存真实 roomId 和主播 UID。
- 采集直播状态、标题、封面、关键帧、分区、在线/热度、关注数、开播时间。
- 手动刷新、启用/停用、采集间隔保存。
- 横向直播间卡片、详情区、数据完整性标签。
- 近 24 小时趋势图。
- 房间观众和大航海榜单：在线榜、进房、日榜、周榜、月榜、大航海周榜、月榜、陪伴榜。
- 榜单排序方式和升降序。
- 榜单列表最多 100 条，内部可滚动。
- 浅色/深色主题。

注意：

- 房间观众括号数来自榜单接口，不是 `Room/get_info.data.online`。
- 大航海部分接口可匿名使用，房间观众 `queryContributionRank` 需要 WBI。
- 榜单刷新可能较慢，前端已放宽为 120 秒，但仍受外部接口和限频影响。

### B站指定用户监控工作台

已实现：

- `/subjects` 列表。
- `/subjects/:subjectId` 工作台。
- Subject 聚合层，不重写粉丝和直播模块。
- 创建 Subject 时可自动创建或复用粉丝监控和直播间监控。
- 顶部用户信息、头像、直播状态、弹幕状态、采集健康。
- 四个 KPI。
- 粉丝数 + 直播热度双指标趋势图。
- 右侧弹幕监控，可切换到房间观众/大航海榜单。
- 弹幕列表实时轮询、自动追最新、悬停暂停。
- 左侧趋势组件可调整宽高，右侧弹幕/榜单组件同步适配。

注意：

- Subject 的布局表已经存在，但当前前端主布局是组件固定编排 + 局部拖拽调整，不是完整可视化布局编辑器。
- 弹幕指标和榜单数据都复用直播模块，不在 Subject 表中复制。

### B站扫码登录态

已实现：

- 生成二维码。
- 轮询扫码状态。
- 保存 Cookie、CSRF、refreshToken、账号信息。
- 使用 `x/web-interface/nav` 校验登录态。
- 登录态完整字段展示和一键复制。
- 弹幕 `getDanmuInfo` 优先使用已保存登录态。

注意：

- 当前 `/api/bilibili/auth/refresh` 更接近重新校验，不是完整 Cookie refresh 链路。
- 登录态字段是敏感数据，只应在本机可信环境查看。
- 生产前必须加管理员鉴权和审计。

## B站接口研究来源

当前实现直接参考了这些本地资料：

- `bilibili-api-collect-new-research/repo/docs/user/info.md`
- `bilibili-api-collect-new-research/repo/docs/user/status_number.md`
- `bilibili-api-collect-new-research/repo/docs/user/relation.md`
- `bilibili-api-collect-new-research/repo/docs/live/info.md`
- `bilibili-api-collect-new-research/repo/docs/live/message_stream.md`
- `bilibili-api-collect-new-research/repo/docs/live/danmaku.md`
- `bilibili-api-collect-new-research/repo/docs/live/guard.md`
- `bilibili-api-collect-new-research/repo/docs/live/user.md`
- `bilibili-api-collect-new-research/repo/docs/misc/sign/wbi.md`
- `bilibili-api-collect-new-research/repo/docs/misc/buvid3_4.md`
- `bilibili-api-collect-new-research/repo/docs/login/login_action/QR.md`
- `bilibili-api-collect-new-research/repo/docs/login/cookie_refresh.md`
- `bilibili-api-collect-new-research/repo/docs/login/login_info.md`

根目录专题文档：

- `docs/bilibili-api-notes.md`
- `docs/bilibili-live-room-api-research.md`
- `docs/bilibili-live-room-audience-guard-rank-research.md`
- `docs/bilibili-live-danmaku-research.md`
- `docs/bilibili-login-research.md`
- `docs/bilibili-api-libraries-risk-control-research.md`
- `docs/bilibili-risk-control-deep-dive.md`

实现策略：

- 优先使用公开、低频、风控复杂度低的接口。
- 只在弹幕 `getDanmuInfo`、房间观众榜单这类必须签名的接口里使用最小 WBI 能力。
- 不实现验证码绕过、复杂风控绕过或平台安全机制绕过。
- 登录态只使用用户主动扫码保存的正常登录态。

## 常用验证命令

后端编译：

```powershell
cd social-data-monitor\backend
.\mvnw.cmd -DskipTests compile
```

后端测试：

```powershell
cd social-data-monitor\backend
.\mvnw.cmd test
```

前端类型检查：

```powershell
cd social-data-monitor\frontend
npm run typecheck
```

前端构建：

```powershell
cd social-data-monitor\frontend
npm run build
```

运行态 smoke test：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/follower-monitor/users
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/live-monitor/rooms
Invoke-RestMethod http://127.0.0.1:8080/api/subjects
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/auth/status
```

## 本次文档核对过的内容

本次为了生成这份总结，已阅读或扫描：

- `social-data-monitor/` 根目录结构。
- `social-data-monitor/backend/pom.xml`
- `social-data-monitor/frontend/package.json`
- `social-data-monitor/backend/src/main/resources/application.yml`
- `social-data-monitor/backend/src/main/resources/application-dev.yml`
- `social-data-monitor/backend/src/main/resources/db/migration/`
- `social-data-monitor/scripts/dev-start.ps1`
- `social-data-monitor/scripts/dev-stop.ps1`
- 后端 Bilibili 粉丝、直播、弹幕、榜单、登录态、Subject 相关 controller/service/client/repository。
- 前端路由、主布局、Bilibili 粉丝页、直播页、Subject 列表和工作台、Subject widgets、API 封装。
- 根目录已有 `docs/README.md`、`runbook.md`、`handoff.md`、接口研究文档。

已执行的核对命令包括：

```powershell
rg --files
rg -n "@RequestMapping|@GetMapping|@PostMapping|@PatchMapping|@PutMapping|@DeleteMapping" social-data-monitor/backend/src/main/java/com/socialmonitor -g "*Controller.java"
Get-ChildItem social-data-monitor/backend/src/main/resources/db/migration
Get-Content -Encoding UTF8 social-data-monitor/backend/src/main/resources/application.yml
Get-Content -Encoding UTF8 social-data-monitor/frontend/src/router/index.ts
git status --short
```

其中 `git status --short` 在根目录返回 `fatal: not a git repository`，说明当前工作区没有可用 Git 元数据。

本次没有运行后端编译、后端测试、前端 typecheck 或 build，因为任务目标是阅读和文档沉淀，没有修改业务代码。

## 已知风险和下次建议

风险：

- 根目录当前不是 Git 仓库，提交前必须确认版本控制位置。
- `/api/**` 开发期放行，生产不可直接暴露。
- B站登录态完整展示和一键复制是本机开发能力，生产必须加权限、审计和脱敏策略。
- B站接口随时可能变更，尤其是 WBI、榜单、弹幕包结构和风控码。
- 便携 PostgreSQL 的初始化来源未完全文档化，换机器时 `.dev-tools/` 和 `.dev-data/` 可能不存在。
- 当前通用采集调度 `SOCIAL_MONITOR_COLLECTOR_SCHEDULER_ENABLED` 默认关闭，但 Bilibili 模块内部 scheduler 默认开启，排障时要分清配置域。
- 旧弹幕如果只保存了脱敏昵称且没有 UID，无法可靠恢复完整昵称。
- Subject 布局表已存在，但前端尚未做完整拖拽式布局编辑。
- 根目录 `package.json` 没有有效测试脚本，不要把它当主工程启动入口。

下次继续开发建议：

1. 如果改 B站粉丝页，先读 `BilibiliView.vue`、`frontend/src/api/bilibili.ts`、`BilibiliFollowerMonitorService`。
2. 如果改直播页，先读 `BilibiliLiveView.vue`、`frontend/src/api/bilibiliLive.ts`、`BilibiliLiveMonitorService`、`BilibiliLiveRankService`。
3. 如果改用户工作台，先读 `SubjectWorkbenchView.vue`、`SubjectWidgetBoard.vue`、`BilibiliFollowerLiveHeatWidget.vue`、`BilibiliLiveDanmuWidget.vue`、`SubjectWorkbenchService`。
4. 如果改弹幕稳定性，先读 `BilibiliLiveDanmakuService`、`BilibiliLiveDanmuInfoClient`、`BilibiliLiveDanmakuPacketCodec`、`BilibiliLiveDanmakuEventParser`。
5. 如果改登录态，先读 `BilibiliAuthPanel.vue`、`BilibiliAuthController`、`BilibiliAuthService`、`BilibiliPassportClient`、`BilibiliCredentialCipher`。
6. 如果改数据库，新增 Flyway 迁移，不要直接修改已执行迁移。
7. 修改前端后至少运行 `npm run typecheck`，改图表和布局后用 1440px、1366px、1280px、1024px 检查。
8. 修改后端后至少运行 `.\mvnw.cmd -DskipTests compile`，涉及 repository SQL 时尽量补 smoke test。

## 推荐继续阅读

- `docs/README.md`：文档地图。
- `docs/runbook.md`：运行、停止、排障。
- `docs/handoff.md`：下次接手最短路径。
- `docs/architecture.md`：架构和链路。
- `docs/data-model.md`：数据模型。
- `docs/frontend-notes.md`：前端页面和组件。
- `docs/feature-status.md`：功能状态。
- `docs/bilibili-live-danmaku-research.md`：弹幕和 WebSocket。
- `docs/bilibili-live-room-audience-guard-rank-research.md`：房间观众和大航海榜单。
