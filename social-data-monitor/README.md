# 多社交平台数据监控系统工程骨架

## 目录

- [当前已实现的主要入口](#当前已实现的主要入口)
- [工程结构](#工程结构)
- [技术栈](#技术栈)
- [环境要求](#环境要求)
- [本地 PostgreSQL 初始化](#本地-postgresql-初始化)
- [推荐一键启动](#推荐一键启动)
- [抖音扫码获取本人登录态](#抖音扫码获取本人登录态)
- [启动后端](#启动后端)
- [启动前端](#启动前端)
- [开发脚本](#开发脚本)
- [数据库迁移](#数据库迁移)
- [验证命令](#验证命令)

这是基于本地方案文档落地的 Java Spring Boot + Vue 3 模块化单体项目。当前业务监控闭环聚焦 Bilibili；抖音作为同一系统内的登录态模块，用本人抖音扫码后保存完整 Web 浏览器会话，并预留官方 OAuth 登录态，不采集抖音业务数据。

对应方案文档在：

- [multi-social-platform-monitoring/docs/technical-framework.md](../multi-social-platform-monitoring/docs/technical-framework.md)
- [multi-social-platform-monitoring/docs/iteration-summary.md](../multi-social-platform-monitoring/docs/iteration-summary.md)
- [docs/README.md](../docs/README.md)

## 当前已实现的主要入口

```text
http://127.0.0.1:5173/bilibili
http://127.0.0.1:5173/bilibili/live
http://127.0.0.1:5173/data
http://127.0.0.1:5173/analytics
http://127.0.0.1:5173/subjects
http://127.0.0.1:5173/subjects/{subjectId}
http://127.0.0.1:5173/douyin
```

- `/bilibili`：B站用户粉丝数趋势监控。
- `/bilibili/live`：B站直播间、直播弹幕、直播场次统计，以及单场 XLSX、CSV、ZIP 导出；场次面板支持自定义秒级自动刷新和立即刷新。
- `/data`：直播场次、事件明细、用户聚合和数据质量查询，支持筛选、分页与单场导出。
- `/analytics`：单场直播互动、付费、弹幕参与深度、付费用户复购与消费层级、用户分层、礼物结构和规则洞察看板，支持 1/5/15 分钟粒度及自定义刷新间隔。
- `/subjects`：指定用户监控对象列表。
- `/subjects/{subjectId}`：指定用户聚合工作台，合并展示粉丝数、直播热度、弹幕速率和采集健康。
- `/douyin`：用本人抖音扫码，保存、校验、查看、复制、导出或撤销完整登录态。

## 工程结构

```text
social-data-monitor
  backend    Spring Boot 后端
  frontend   Vue 3 前端
  douyin-worker  独立 Node.js + Playwright 抖音扫码 Worker
  deploy/douyin 抖音登录态四服务 Compose
  scripts    本地开发脚本
  docs       应用内文档
  .env.example  本地配置空模板
  README.md
  .gitignore
```

## 技术栈

后端：

- Java 21 推荐。
- Java 17 可作为保守兼容选择；当前 Maven 编译目标使用 Java 17，方便在 Java 17+ 环境验证。
- Spring Boot 3.x。
- Maven Wrapper。
- Spring Web、Spring Security、Validation、Scheduling、Actuator。
- MyBatis-Plus。
- Flyway。
- PostgreSQL Driver。
- springdoc-openapi。
- Apache POI（流式生成原生 XLSX 工作簿）。

前端：

- Vue 3。
- Vite。
- TypeScript。
- Pinia。
- Vue Router。
- Element Plus。
- ECharts。
- Axios。

抖音登录态 Worker：

- Node.js 20+。
- Playwright 1.61.1 + Chromium。
- Node.js 内置 HTTP Server 和 test runner。

暂未引入：

- Redis、Kafka、Elasticsearch、ClickHouse、Kubernetes。
- 通用 Docker 部署；当前 Compose 入口覆盖包含抖音登录态的完整系统。
- Resilience4j。当前只保留轻量 `RetryPolicy` 和 `RateLimitService` 占位；等真实限流、重试、熔断策略变复杂后再加入。

## 环境要求

- JDK 21 推荐，JDK 17+ 可运行当前骨架。
- Node.js 20+。
- npm 10+。
- PostgreSQL 14+。
- 抖音扫码需要 Chromium；统一启动脚本会安装到项目内 `.dev-tools/playwright`。

当前工程包含一个轻量 Maven Wrapper。若本机没有 Maven，执行 `backend\mvnw.cmd` 会自动下载 Maven 到 `backend\.mvn\wrapper`。

## 本地 PostgreSQL 初始化

日常开发优先使用 `scripts\dev-start.cmd` 自动准备便携 PostgreSQL。若要使用本机 PostgreSQL，请先执行：

```sql
CREATE USER social_monitor WITH PASSWORD '<your_db_password>';
CREATE DATABASE social_data_monitor OWNER social_monitor;
GRANT ALL PRIVILEGES ON DATABASE social_data_monitor TO social_monitor;
```

本地私有配置统一放在应用工程根目录的 `social-data-monitor/.env.local`，该文件在项目内，但已被 Git 忽略。首次启动前先从空模板复制一份：

```powershell
cd social-data-monitor
Copy-Item .env.example .env.local
```

然后在 `.env.local` 中填写数据库连接、开发账号密码、Bilibili 凭据加密 key、前端 API 地址等本地值。`SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY` 需要是 base64 编码的 32 字节随机 key。抖音凭据使用独立的 `SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY`；统一启动脚本会在缺失或仍为模板值时自动生成并持久化，已有合法值保持不变。

脚本启动时会自动加载 `.env.local`。如果手动启动单个服务，也建议先在同一个 PowerShell 会话里执行：

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
& .\scripts\load-env.ps1
```

## 推荐一键启动

日常开发优先使用：

```powershell
cd social-data-monitor
.\scripts\dev-start.cmd
```

`dev-start.cmd` 会统一启动 PostgreSQL、Douyin Worker、Spring Boot 和 Vite。脚本会自动检查并启动：

- 便携 PostgreSQL：`5432`
- Douyin Worker：`8787`
- Spring Boot 后端：`8080`
- Vite 前端：`5173`

Worker、后端和前端会并行启动，并自动加载 `.env.local`。脚本会等待 Worker、后端、Bilibili 页面与抖音页面可用。

启动后常用页面：

```text
http://127.0.0.1:5173/bilibili
http://127.0.0.1:5173/bilibili/live
http://127.0.0.1:5173/subjects
http://127.0.0.1:5173/douyin
```

如果只想发起启动、不等待健康检查：

```powershell
.\scripts\dev-start.cmd -NoWait
```

停止本地开发环境：

```powershell
.\scripts\dev-stop.cmd
```

日志在：

```text
.dev-data\postgres.log
.dev-data\douyin-worker-dev.log
.dev-data\backend-dev.log
.dev-data\frontend-dev.log
```

## 抖音扫码获取本人登录态

该入口获取的是启动项目的管理员本人登录态：独立 Chromium 打开抖音 Web 登录页，前端展示二维码，你用自己的抖音 App 扫码并在手机确认。成功后，后端把原始结果完整保存到 `platform_credential`，后续抖音接口通过 `DouyinCredentialProvider` 读取，不需要再次手工复制 Cookie。

### 保存内容

Web 扫码成功后原样保存：

- 全部 Cookie 及按来源生成的 Cookie Header。
- `storageState`、localStorage、sessionStorage。
- 可序列化 IndexedDB。
- 浏览器上下文信息、账号快照和 Worker 原始返回。

OAuth 启用后会单独保存 token 响应、回调参数和用户信息原文。Web 与 OAuth 是两条独立凭据，不覆盖 Bilibili 登录态。

### Windows 统一启动

直接使用系统原有的一键启动命令：

```powershell
.\scripts\dev-start.cmd
```

统一入口会执行以下操作：

1. 加载项目内 `.env.local`。
2. 抖音凭据 key 缺失或仍为模板值时，自动生成 base64 32 字节 key 并写回 `.env.local`；合法已有值不会改变，非法自定义值会拒绝启动。
3. 启用 `dev,douyin` 和 `SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED=true`。
4. 首次运行时安装 `douyin-worker` 依赖和项目内 Chromium。
5. 启动 PostgreSQL、`8787` Worker、Spring Boot 后端和 Vite 前端。
6. 等待 Worker、后端及两个平台页面可用。

进入页面后点击“开始扫码”，用自己的抖音 App 扫码并确认。若抖音要求滑块或额外确认，本地 Worker 会保留可见浏览器窗口并把页面状态显示为 `USER_ACTION_REQUIRED`；在浏览器中手工完成后继续轮询即可。

停止整套本地环境：

```powershell
.\scripts\dev-stop.cmd
```

`dev-start-douyin.cmd` 和 `dev-stop-douyin.cmd` 仅作为历史兼容别名，内部转发到相同的统一启停流程。

### 关键配置

| 配置 | 用途 | Web 扫码建议值 |
| --- | --- | --- |
| `SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED` | 后端抖音组件开关 | 统一启动脚本自动设为 `true` |
| `SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY` | 登录态持久化加密 key | 缺失时自动生成 base64 32 字节 key |
| `SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL` | 后端访问 Worker | `http://127.0.0.1:8787` |
| `SOCIAL_MONITOR_DOUYIN_WORKER_TOKEN` | 后端与 Worker 的共享 token | 两边使用同一值或同时留空 |
| `DOUYIN_WORKER_HEADLESS` | 是否隐藏本地 Chromium | 本地设为 `false` |
| `SOCIAL_MONITOR_DOUYIN_OAUTH_MODE` | 官方 OAuth 模式 | 只用 Web 扫码时保持 `disabled` |

需要官方 OAuth 时再把模式改为 `live`，并填写 client key、client secret、redirect URI 和 scope；Web 扫码不依赖这些配置。

### Docker / Linux

独立 Compose 包含 PostgreSQL、backend、frontend、worker 四个服务。先准备 `.env.local`，再从 `social-data-monitor/` 执行：

```powershell
docker compose --env-file .env.local -f deploy/douyin/compose.yml config
docker compose --env-file .env.local -f deploy/douyin/compose.yml up --build
```

页面仍为 `http://127.0.0.1:5173/douyin`。Compose 中 Worker 使用 headless Chromium；正常二维码扫描可以直接完成，出现额外人机验证时会返回 `USER_ACTION_REQUIRED`，可切回 Windows 可见浏览器入口完成登录。

停止容器：

```powershell
docker compose --env-file .env.local -f deploy/douyin/compose.yml down
```

### 登录态接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/douyin/auth/web/qr/start` | 创建本人 Web 扫码会话 |
| `GET` | `/api/douyin/auth/web/qr/{loginId}/image` | 读取当前二维码图片 |
| `GET` | `/api/douyin/auth/web/qr/{loginId}/status` | 轮询扫描、确认、验证和保存状态 |
| `POST` | `/api/douyin/auth/web/validate` | 用全新 Context 复验当前 Web 登录态 |
| `GET` | `/api/douyin/auth/credentials/web` | 读取完整 Web 登录态 |
| `GET` | `/api/douyin/auth/credentials/web/export` | 下载完整 Web 登录态 JSON |
| `DELETE` | `/api/douyin/auth/credentials/web` | 撤销当前 Web 登录态 |
| `GET` | `/api/douyin/auth/status` | 查看 Worker 与两类凭据状态 |

Worker 的 `/internal/v1/**` 只供 Spring Boot 编排；Vue 不直接访问 Worker。

## 启动后端

日常开发推荐使用上方一键启动。下面命令主要用于单独调试后端。

```powershell
cd social-data-monitor
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
& .\scripts\load-env.ps1
cd backend
.\mvnw.cmd spring-boot:run
```

后端默认端口：`8080`。

常用地址：

- 健康检查：`http://localhost:8080/actuator/health`
- 开发健康接口：`http://localhost:8080/api/dev/health`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 平台 Adapter：`http://localhost:8080/api/platforms/adapters`

## 启动前端

日常开发推荐使用上方一键启动。下面命令主要用于单独调试前端。

```powershell
cd social-data-monitor
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
& .\scripts\load-env.ps1
cd frontend
npm install
npm run dev
```

前端默认端口：`5173`。

前端会优先访问后端 `/api` 接口；若后端不可用，Dashboard 和平台页会使用本地 mock 数据。

## 开发脚本

```powershell
cd social-data-monitor
.\scripts\dev-start.cmd
.\scripts\dev-stop.cmd
.\scripts\dev-backend.ps1
.\scripts\dev-frontend.ps1
```

脚本只做本地启动，不依赖 Docker。`dev-start.cmd` 是推荐入口，会自动绕过本机 PowerShell 执行策略限制；分开的后端、前端脚本主要用于排障。

## 已预留的工程边界

后端包结构按方案创建：

```text
com.socialmonitor
  common
  config
  security
  platform
  bilibili
  subject
  collector
  ingestion
  socialdata
  analytics
  ai
  identity
  notification
  admin
```

关键预留：

- `SocialPlatformAdapter`
- `BilibiliPlatformAdapter`
- `PlatformDataNormalizer`
- `BilibiliNormalizer`
- `CollectTaskService`
- `CollectTaskScheduler`
- `CollectTaskExecutor`
- `RateLimitService`
- `RetryPolicy`
- `TaskCheckpointService`
- `RawPayloadService`
- `ApiCallLogService`
- `AiAnalysisPort`
- `MockAiAnalysisProvider`

## 数据库迁移

当前 Flyway 迁移：

```text
backend/src/main/resources/db/migration/V1__init_schema.sql
backend/src/main/resources/db/migration/V2__bilibili_follower_monitor.sql
backend/src/main/resources/db/migration/V3__bilibili_interval_range.sql
backend/src/main/resources/db/migration/V4__bilibili_live_monitor.sql
backend/src/main/resources/db/migration/V5__subject_monitor.sql
backend/src/main/resources/db/migration/V6__bilibili_live_danmaku_monitor.sql
backend/src/main/resources/db/migration/V7__bilibili_auth_credential.sql
backend/src/main/resources/db/migration/V8__bilibili_live_rank_monitor.sql
backend/src/main/resources/db/migration/V9__douyin_auth_credential.sql
backend/src/main/resources/db/migration/V10__bilibili_live_session.sql
backend/src/main/resources/db/migration/V11__bilibili_live_danmaku_recent_sender_uid.sql
```

其中 `V5` 新增指定用户聚合层，`V6` 新增直播弹幕连接/指标/最近弹幕，`V7` 保存 B站扫码登录态索引，`V8` 保存直播榜单，`V9` 保存抖音登录态，`V10` 新增直播场次、受支持事件和单场导出所需数据，`V11` 为最近弹幕补充发送者 UID。扫码登录成功后，现存游客态弹幕连接会异步重连为登录态；新收到的弹幕尽量同时保存昵称和 UID，历史缺失 UID 的脱敏记录不会猜测回填。场次统计只代表 WebSocket 在线期间成功解析并持久化的受支持事件，详细口径和 API 见 [B站直播场次、事件留存与导出](../docs/bilibili-live-session-data.md)。不要手工改生产数据库结构，后续变更继续新增 Flyway migration。

## 验证命令

后端：

```powershell
cd social-data-monitor\backend
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test
```

前端：

```powershell
cd social-data-monitor\frontend
npm install
npm run typecheck
npm run build
```

上一轮完整功能验证结果已记录在 [docs/feature-status.md](../docs/feature-status.md) 和 [docs/handoff.md](../docs/handoff.md)。
