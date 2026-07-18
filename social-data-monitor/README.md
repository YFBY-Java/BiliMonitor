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

这是基于本地方案文档落地的 Java Spring Boot + Vue 3 模块化单体项目。当前业务监控闭环聚焦 Bilibili；抖音首期只提供独立登录态基础设施，用本人抖音扫码后保存完整 Web 浏览器会话，并预留官方 OAuth 登录态，不采集抖音业务数据。

对应方案文档在：

- [multi-social-platform-monitoring/docs/technical-framework.md](../multi-social-platform-monitoring/docs/technical-framework.md)
- [multi-social-platform-monitoring/docs/iteration-summary.md](../multi-social-platform-monitoring/docs/iteration-summary.md)
- [docs/README.md](../docs/README.md)

## 当前已实现的主要入口

```text
http://127.0.0.1:5173/bilibili
http://127.0.0.1:5173/bilibili/live
http://127.0.0.1:5173/subjects
http://127.0.0.1:5173/subjects/{subjectId}
http://127.0.0.1:5173/douyin  # 仅抖音独立启动入口开启
```

- `/bilibili`：B站用户粉丝数趋势监控。
- `/bilibili/live`：B站直播间监控和直播弹幕监控。
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
- 通用 Docker 部署；抖音登录态提供独立、可选的 Compose 入口。
- Resilience4j。当前只保留轻量 `RetryPolicy` 和 `RateLimitService` 占位；等真实限流、重试、熔断策略变复杂后再加入。

## 环境要求

- JDK 21 推荐，JDK 17+ 可运行当前骨架。
- Node.js 20+。
- npm 10+。
- PostgreSQL 14+。
- 抖音扫码需要 Chromium；独立启动脚本会安装到项目内 `.dev-tools/playwright`。

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

然后在 `.env.local` 中填写数据库连接、开发账号密码、Bilibili 凭据加密 key、抖音凭据加密 key、前端 API 地址等本地值。`SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY` 和 `SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY` 都需要是 base64 编码的 32 字节随机 key；两个 key 相互独立。

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

脚本会自动检查并启动：

- 便携 PostgreSQL：`5432`
- Spring Boot 后端：`8080`
- Vite 前端：`5173`

后端和前端会并行启动，并自动加载 `.env.local`。脚本会等待 `http://127.0.0.1:8080/actuator/health` 与 `http://127.0.0.1:5173/bilibili` 可用。

启动后常用页面：

```text
http://127.0.0.1:5173/bilibili
http://127.0.0.1:5173/bilibili/live
http://127.0.0.1:5173/subjects
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

### Windows 一键启动

先复制并填写项目内配置：

```powershell
cd social-data-monitor
Copy-Item .env.example .env.local
```

生成抖音凭据加密 key：

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

把结果写入 `.env.local` 的 `SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY`，然后执行。启用抖音登录态时该 key 必填且必须保持不变；缺失或格式错误会直接拒绝启动，避免重启后已保存状态无法读取：

```powershell
.\scripts\dev-start-douyin.cmd
```

这个新增入口会执行以下操作：

1. 加载项目内 `.env.local`。
2. 仅在当前启动链路启用 `dev,douyin`、`SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED=true` 和 `VITE_DOUYIN_ENABLED=true`。
3. 首次运行时安装 `douyin-worker` 依赖和项目内 Chromium。
4. 启动 `8787` Worker，再复用原 PostgreSQL、后端和前端启动流程。
5. 等待 Worker、后端和前端可用后输出 `http://127.0.0.1:5173/douyin`。

进入页面后点击“开始扫码”，用自己的抖音 App 扫码并确认。若抖音要求滑块或额外确认，本地 Worker 会保留可见浏览器窗口并把页面状态显示为 `USER_ACTION_REQUIRED`；在浏览器中手工完成后继续轮询即可。

停止整套本地环境：

```powershell
.\scripts\dev-stop-douyin.cmd
```

原 `dev-start.cmd`、`dev-stop.cmd` 的默认行为不变：不会启用抖音、不启动 Worker，也不显示抖音菜单。

### 关键配置

| 配置 | 用途 | Web 扫码建议值 |
| --- | --- | --- |
| `SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED` | 后端抖音组件开关 | 新启动脚本自动设为 `true` |
| `SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY` | 登录态持久化加密 key | base64 编码的 32 字节 |
| `SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL` | 后端访问 Worker | `http://127.0.0.1:8787` |
| `SOCIAL_MONITOR_DOUYIN_WORKER_TOKEN` | 后端与 Worker 的共享 token | 两边使用同一值或同时留空 |
| `DOUYIN_WORKER_HEADLESS` | 是否隐藏本地 Chromium | 本地设为 `false` |
| `VITE_DOUYIN_ENABLED` | 编译/开发时注册菜单与路由 | 新启动脚本自动设为 `true` |
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
```

其中 `V5` 新增指定用户聚合层相关表，`V6` 新增直播弹幕监控会话、指标桶和最近弹幕表。不要手工改生产数据库结构，后续变更继续新增 Flyway migration。

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
