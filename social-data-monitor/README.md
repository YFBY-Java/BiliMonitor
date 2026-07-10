# 多社交平台数据监控系统工程骨架

这是基于本地方案文档落地的 Java Spring Boot + Vue 3 模块化单体项目。当前 MVP 聚焦 Bilibili 数据监控闭环，已覆盖粉丝趋势、直播间状态、直播弹幕和指定用户聚合工作台；后续仍可通过 Adapter、Normalizer、AI Provider Port 和 Identity 模块继续扩展。

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
```

- `/bilibili`：B站用户粉丝数趋势监控。
- `/bilibili/live`：B站直播间监控和直播弹幕监控。
- `/subjects`：指定用户监控对象列表。
- `/subjects/{subjectId}`：指定用户聚合工作台，合并展示粉丝数、直播热度、弹幕速率和采集健康。

## 工程结构

```text
social-data-monitor
  backend    Spring Boot 后端
  frontend   Vue 3 前端
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

暂未引入：

- Redis、Kafka、Elasticsearch、ClickHouse、Kubernetes。
- Docker 或 docker-compose。
- Resilience4j。当前只保留轻量 `RetryPolicy` 和 `RateLimitService` 占位；等真实限流、重试、熔断策略变复杂后再加入。

## 环境要求

- JDK 21 推荐，JDK 17+ 可运行当前骨架。
- Node.js 20+。
- npm 10+。
- PostgreSQL 14+。

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

然后在 `.env.local` 中填写数据库连接、开发账号密码、Bilibili 凭据加密 key、前端 API 地址等本地值。不要把这些配置和密钥放到项目目录外。`SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY` 需要是 base64 编码的 32 字节随机 key。

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
