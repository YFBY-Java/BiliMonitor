# 运行指南

最后更新：2026-06-17

本指南以 Windows PowerShell 为主，因为当前工作区运行在 `.`。

## 推荐一键启动

下次开发优先用这个命令：

```powershell
cd social-data-monitor
.\scripts\dev-start.cmd
```

它会自动执行：

1. 检查 `5432`，未启动时启动便携 PostgreSQL。
2. 检查 `8080`，未启动时在后台启动 Spring Boot 后端。
3. 检查 `5173`，未启动时在后台启动 Vite 前端。
4. 后端和前端并行启动，并等待健康检查完成。

启动成功后访问：

```text
http://127.0.0.1:5173/bilibili
http://127.0.0.1:5173/bilibili/live
http://127.0.0.1:5173/subjects
```

如果只想快速发起启动、不等待健康检查：

```powershell
.\scripts\dev-start.cmd -NoWait
```

常用日志：

```text
social-data-monitor\.dev-data\postgres.log
social-data-monitor\.dev-data\backend-dev.log
social-data-monitor\.dev-data\frontend-dev.log
```

停止本地开发环境：

```powershell
cd social-data-monitor
.\scripts\dev-stop.cmd
```

`.cmd` 包装脚本会自动用 `-ExecutionPolicy Bypass` 调用同名 `.ps1`，避免被本机 PowerShell 执行策略拦截。`dev-stop.ps1` 只会停止命令行包含当前项目路径的前后端进程；数据库使用项目内的 `pg_ctl stop`。

## 前置依赖

推荐环境：

- JDK 17。
- Node.js 20 或兼容版本。
- npm。
- PostgreSQL 14+。

后端使用 Maven Wrapper，不需要单独安装 Maven。后端依赖见 [`../social-data-monitor/backend/pom.xml`](../social-data-monitor/backend/pom.xml)，前端依赖见 [`../social-data-monitor/frontend/package.json`](../social-data-monitor/frontend/package.json)。

## 项目内私有配置

统一示例文件：[`../social-data-monitor/.env.example`](../social-data-monitor/.env.example)。

真实配置和密钥统一写入 [`../social-data-monitor/.env.local`](../social-data-monitor/.env.local)。该文件在项目目录内，已被 Git 忽略；不要把数据库密码、Bilibili 登录态、加密 key 或本地私有配置放到项目目录外。

首次部署时：

```powershell
cd social-data-monitor
Copy-Item .env.example .env.local
```

然后在 `.env.local` 中填写：

```properties
SOCIAL_MONITOR_DB_URL=jdbc:postgresql://localhost:5432/social_data_monitor
SOCIAL_MONITOR_DB_USERNAME=social_monitor
SOCIAL_MONITOR_DB_PASSWORD=<your_db_password>
SOCIAL_MONITOR_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
SOCIAL_MONITOR_COLLECTOR_SCHEDULER_ENABLED=false
SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY=<base64-encoded-32-byte-key>
VITE_API_BASE_URL=http://localhost:8080
```

## B站扫码登录获取登录态

入口在 `http://127.0.0.1:5173/bilibili` 顶部的“B站登录态”面板。

当前首期已经接入：

- 后端二维码生成、轮询、Cookie 提取、`nav` 校验和加密保存。
- 前端扫码弹窗、轮询状态、完整登录态查看、刷新校验、移除登录态。
- 数据库迁移 `V7__bilibili_auth_credential.sql`，复用 `platform_credential` 和 `platform_account`。

手动验收流程：

1. 启动项目：

```powershell
cd social-data-monitor
.\scripts\dev-start.cmd
```

2. 打开：

```text
http://127.0.0.1:5173/bilibili
```

3. 点击“扫码登录”，用 Bilibili 手机客户端扫码并确认。
4. 成功后检查页面是否显示 Bilibili 头像、昵称和 UID。
5. 点击“查看完整登录态”，确认能看到完整 Cookie、CSRF 和 `refreshToken`。
6. 重启后端，再调用登录态状态接口确认仍然可校验。

相关 API：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/auth/status

Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8080/api/bilibili/auth/qr/start

Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/auth/qr/{loginId}/status
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/auth/credential

Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8080/api/bilibili/auth/refresh

Invoke-RestMethod `
  -Method Delete `
  -Uri http://127.0.0.1:8080/api/bilibili/auth
```

配置项：

```yaml
app:
  bilibili:
    auth:
      enabled: ${SOCIAL_MONITOR_BILIBILI_AUTH_ENABLED:true}
      qr-expire-seconds: ${SOCIAL_MONITOR_BILIBILI_AUTH_QR_EXPIRE_SECONDS:180}
      poll-interval-ms: ${SOCIAL_MONITOR_BILIBILI_AUTH_POLL_INTERVAL_MS:1500}
      session-cleanup-delay-ms: ${SOCIAL_MONITOR_BILIBILI_AUTH_SESSION_CLEANUP_DELAY_MS:60000}
      connect-timeout-ms: ${SOCIAL_MONITOR_BILIBILI_AUTH_CONNECT_TIMEOUT_MS:5000}
      request-timeout-ms: ${SOCIAL_MONITOR_BILIBILI_AUTH_REQUEST_TIMEOUT_MS:10000}
      refresh-check-interval-hours: ${SOCIAL_MONITOR_BILIBILI_AUTH_REFRESH_CHECK_INTERVAL_HOURS:24}
      credential-encryption-key: ${SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY:}
```

注意：开发环境没有配置 `SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY` 时，后端会使用稳定 dev key 并输出 warning。生产环境必须设置 base64 编码的 32 字节密钥，并给 `/api/bilibili/auth/**` 接入管理员鉴权。

当前已完成验收：真实扫码成功、真实凭据入库、完整登录态查看、后端重启后仍能通过 `nav` 校验。仍建议下次补充验证“删除登录态后数据库状态变为 `REVOKED`，前端显示未登录”。

如果不设置 `VITE_API_BASE_URL`，Vite 开发服务会通过 [`../social-data-monitor/frontend/vite.config.ts`](../social-data-monitor/frontend/vite.config.ts) 将 `/api` 和 `/actuator` 代理到 `http://localhost:8080`。

## B站粉丝监控配置

配置源在 [`../social-data-monitor/backend/src/main/resources/application.yml`](../social-data-monitor/backend/src/main/resources/application.yml)。

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_MONITOR_ENABLED` | `true` | 是否启用 B站粉丝监控定时器。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_STORAGE_ENABLED` | `true` | 是否启用 B站粉丝监控的数据库存储和 API。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_SCHEDULER_DELAY_MS` | `1000` | 扫描到期用户的调度间隔。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_DUE_BATCH_SIZE` | `10` | 每轮最多处理的到期用户数。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_INTERVAL_SECONDS` | `3600` | 新增用户默认采集间隔。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_MIN_INTERVAL_SECONDS` | `1` | 允许的最小单用户采集间隔。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_MAX_INTERVAL_SECONDS` | `2592000` | 允许的最大单用户采集间隔，约 30 天。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_SHORT_INTERVAL_WARNING_SECONDS` | `60` | 小于该值时后端写 warning，前端也展示提醒。 |
| `SOCIAL_MONITOR_BILIBILI_FOLLOWER_FAILURE_BACKOFF_SECONDS` | `900` | 采集失败后的退避上限。 |
| `SOCIAL_MONITOR_BILIBILI_REQUEST_MIN_INTERVAL_MS` | `1500` | 对 B站接口的全局最小请求间隔。 |

注意：页面允许设置 `1` 秒采集间隔，但后端仍有全局请求间隔和失败退避。多个用户同时设置极短间隔时，不代表每个用户都能严格每秒请求一次。

## B站直播监控配置

配置源在 [`../social-data-monitor/backend/src/main/resources/application.yml`](../social-data-monitor/backend/src/main/resources/application.yml) 的 `app.bilibili.live-monitor`。

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `SOCIAL_MONITOR_BILIBILI_LIVE_MONITOR_ENABLED` | `true` | 是否启用 B站直播监控定时器。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_STORAGE_ENABLED` | `true` | 是否启用 B站直播监控的数据库存储和 API。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_SCHEDULER_DELAY_MS` | `1000` | 扫描到期直播间的调度间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DUE_BATCH_SIZE` | `20` | 每轮最多处理的到期直播间数。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_STATUS_BATCH_SIZE` | `30` | 批量按 UID 查询直播状态时的批大小。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_INTERVAL_SECONDS` | `300` | 新增直播间默认采集间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_MIN_INTERVAL_SECONDS` | `1` | 允许的最小单直播间采集间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_MAX_INTERVAL_SECONDS` | `2592000` | 允许的最大单直播间采集间隔，约 30 天。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_SHORT_INTERVAL_WARNING_SECONDS` | `120` | 小于该值时后端写 warning，前端也展示提醒。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_FAILURE_BACKOFF_SECONDS` | `900` | 采集失败后的退避上限。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_REQUEST_MIN_INTERVAL_MS` | `1500` | 对 B站直播接口的全局最小请求间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_CONNECT_TIMEOUT_MS` | `5000` | 连接超时。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_READ_TIMEOUT_MS` | `10000` | 读取超时。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_MAX_ATTEMPTS` | `3` | 单次采集最大尝试次数。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_RETRY_BACKOFF_MS` | `1500` | 重试退避。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_USER_AGENT` | `SocialDataMonitor/0.1 ...` | 请求 B站直播接口时使用的 User-Agent。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_REFERER` | `https://live.bilibili.com/` | 请求 B站直播接口时使用的 Referer。 |

### 直播弹幕配置

弹幕 WebSocket 配置在 `app.bilibili.live-monitor.danmaku`。

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_ENABLED` | `true` | 是否启用直播弹幕模块。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_AUTO_START_ENABLED` | `true` | 是否自动为启用弹幕的直播中房间保持连接。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_SCHEDULER_DELAY_MS` | `5000` | 自动连接同步间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_MAX_CONNECTIONS` | `10` | 同时弹幕 WebSocket 连接上限。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_HEARTBEAT_SECONDS` | `30` | WebSocket 心跳间隔。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_CONNECT_TIMEOUT_MS` | `8000` | WebSocket 连接超时。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_BUCKET_SECONDS` | `60` | 弹幕指标桶粒度。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_RECENT_LIMIT` | `200` | 单直播间保留的最近弹幕条数。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_PROTOCOL_VERSION` | `3` | 默认弹幕协议版本；前端也可手动选择 `0/1/2/3` 或自动兼容。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_WBI_CACHE_SECONDS` | `43200` | WBI 参数缓存时间。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_BUVID_CACHE_SECONDS` | `43200` | 匿名 buvid 缓存时间。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_USE_LOGIN_CREDENTIAL` | `true` | 是否优先使用已保存 B站扫码登录态获取弹幕 token 并进行 WebSocket 登录 UID 鉴权。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_WEB_LOCATION` | `444.8` | 获取弹幕信息接口使用的 `web_location`。 |
| `SOCIAL_MONITOR_BILIBILI_LIVE_DANMAKU_CLIENT_VERSION` | `1.14.3` | WebSocket 鉴权包中的客户端版本。 |

说明：弹幕模块默认优先使用已保存扫码登录态调用 `getDanmuInfo`，并在 WebSocket 鉴权包中写入同一账号 mid，从而减少游客态昵称脱敏。登录态不可用、过期或触发风控时会回退匿名公开信息流；系统不做验证码或复杂风控绕过。旧历史弹幕如果已经保存了脱敏昵称且没有 UID，无法可靠补全；新弹幕若包内带 UID，会尝试通过公开用户卡片接口补全昵称。

## 数据库

默认 profile 是 `dev`，数据库连接配置在 [`../social-data-monitor/backend/src/main/resources/application-dev.yml`](../social-data-monitor/backend/src/main/resources/application-dev.yml)。Flyway 会在后端启动时自动应用 [`../social-data-monitor/backend/src/main/resources/db/migration/`](../social-data-monitor/backend/src/main/resources/db/migration/) 下的迁移。

日常开发通常不需要手动启动数据库，直接执行 [`../social-data-monitor/scripts/dev-start.cmd`](../social-data-monitor/scripts/dev-start.cmd) 即可。下面命令只在排障或单独维护数据库时使用。

如果使用本机已有的便携 PostgreSQL，可在 `social-data-monitor/` 下执行：

```powershell
.\.dev-tools\postgresql\pgsql\bin\pg_ctl.exe -D .\.dev-data\postgres -l .\.dev-data\postgres.log start
```

停止便携 PostgreSQL：

```powershell
.\.dev-tools\postgresql\pgsql\bin\pg_ctl.exe -D .\.dev-data\postgres stop
```

如果换机器没有 `.dev-tools/` 和 `.dev-data/`，请使用正式 PostgreSQL 创建数据库和用户：

```sql
CREATE USER social_monitor WITH PASSWORD '<your_db_password>';
CREATE DATABASE social_data_monitor OWNER social_monitor;
```

## 安装依赖

后端第一次运行通常会由 Maven Wrapper 下载依赖：

```powershell
cd social-data-monitor\backend
.\mvnw.cmd -DskipTests compile
```

前端安装依赖：

```powershell
cd social-data-monitor\frontend
npm install
```

当前工作区已经有前端依赖和可运行服务，但换机器或清理后应重新执行。

## 启动后端

日常开发推荐使用一键启动脚本。下面命令主要用于只调试后端或排障。

推荐使用脚本：

```powershell
cd social-data-monitor
.\scripts\dev-backend.ps1
```

等价命令：

```powershell
cd social-data-monitor\backend
.\mvnw.cmd spring-boot:run
```

健康检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

预期返回包含：

```json
{"status":"UP"}
```

Swagger UI：

```text
http://127.0.0.1:8080/swagger-ui.html
```

## 启动前端

日常开发推荐使用一键启动脚本。下面命令主要用于只调试前端或排障。

推荐使用脚本：

```powershell
cd social-data-monitor
.\scripts\dev-frontend.ps1
```

等价命令：

```powershell
cd social-data-monitor\frontend
npm run dev
```

开发服务默认端口：

```text
http://127.0.0.1:5173/
```

B站粉丝监控页面：

```text
http://127.0.0.1:5173/bilibili
```

B站直播监控页面：

```text
http://127.0.0.1:5173/bilibili/live
```

B站指定用户监控页面：

```text
http://127.0.0.1:5173/subjects
```

## 添加监控用户

前端方式：

1. 打开 `http://127.0.0.1:5173/bilibili`。
2. 在顶部输入 B站 UID。
3. 设置采集间隔，最小可填 `1` 秒。
4. 点击“添加监控”。

API 方式：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8080/api/bilibili/follower-monitor/users `
  -ContentType 'application/json' `
  -Body '{"mid":2,"intervalSeconds":3600}'
```

查询监控用户：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/follower-monitor/users
```

立即采集：

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:8080/api/bilibili/follower-monitor/users/1/refresh
```

修改采集间隔：

```powershell
Invoke-RestMethod `
  -Method Patch `
  -Uri http://127.0.0.1:8080/api/bilibili/follower-monitor/users/1/settings `
  -ContentType 'application/json' `
  -Body '{"intervalSeconds":1}'
```

## 添加直播间监控

前端方式：

1. 打开 `http://127.0.0.1:5173/bilibili/live`。
2. 选择“房间号”或 `UID`。
3. 输入直播间房间号或主播 UID。
4. 设置采集间隔，最小可填 `1` 秒。
5. 点击“添加监控”。

API 方式，按房间号添加：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8080/api/bilibili/live-monitor/rooms `
  -ContentType 'application/json' `
  -Body '{"roomId":7734200,"intervalSeconds":300}'
```

API 方式，按主播 UID 添加：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8080/api/bilibili/live-monitor/rooms `
  -ContentType 'application/json' `
  -Body '{"uid":50329118,"intervalSeconds":300}'
```

查询直播间监控：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/live-monitor/rooms
```

立即采集：

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:8080/api/bilibili/live-monitor/rooms/1/refresh
```

修改采集间隔或启用状态：

```powershell
Invoke-RestMethod `
  -Method Patch `
  -Uri http://127.0.0.1:8080/api/bilibili/live-monitor/rooms/1 `
  -ContentType 'application/json' `
  -Body '{"intervalSeconds":1,"enabled":true}'
```

## 添加指定用户监控工作台

前端方式：

1. 打开 `http://127.0.0.1:5173/subjects`。
2. 输入 B站 UID。
3. 点击创建用户监控。
4. 进入 `http://127.0.0.1:5173/subjects/{subjectId}` 查看聚合工作台。

创建时后端会自动查找或创建对应的 B站粉丝监控，并尝试按 UID 查找或创建直播间监控。直播间无法解析时，不会阻塞粉丝监控创建。

API 方式：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8080/api/subjects `
  -ContentType 'application/json' `
  -Body '{"mid":254662438,"danmuEnabled":true}'
```

查询用户监控列表：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/subjects
```

查询单用户工作台：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/subjects/1/workbench
```

查询粉丝数与直播热度趋势：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/subjects/1/trends?metrics=follower,live_online&range=24h&bucket=5m'
```

弹幕状态和最近弹幕接口：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/live-monitor/rooms/1/danmaku/status
Invoke-RestMethod 'http://127.0.0.1:8080/api/bilibili/live-monitor/rooms/1/danmaku/recent?limit=30'
```

## 构建与验证

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

近期功能实现和样式迭代已跑通过后端测试、前端类型检查和前端构建。2026-06-16 扫码登录首期实现后已执行 `.\mvnw.cmd test`、`npm run typecheck` 和 `npm run build`，并验证二维码生成、未扫码轮询、前端扫码弹窗、真实扫码保存登录态、数据库加密保存和后端重启后 `nav` 校验。2026-06-17 弹幕登录态复用实现后再次执行 `.\mvnw.cmd test`，并验证 `/danmaku/status` 返回 `authMode=LOGIN`；弹幕自动滚动交互修改后执行 `npm run typecheck`。下次改业务代码后应重新执行。

## 启动后应检查的端口

- `5432`：PostgreSQL。
- `8080`：Spring Boot 后端。
- `5173`：Vite 前端。

健康检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/auth/status
```

预期返回 `UP`。

## 常见问题

### 后端启动失败，提示数据库连接失败

先确认 PostgreSQL 是否启动：

```powershell
Get-NetTCPConnection -LocalPort 5432 -State Listen
```

再确认环境变量或默认账号是否与 `application-dev.yml` 一致。

### Flyway 迁移失败

查看后端日志，重点检查 `V1__init_schema.sql`、`V2__bilibili_follower_monitor.sql`、`V3__bilibili_interval_range.sql`、`V4__bilibili_live_monitor.sql` 是否已经部分执行。不要手动删除业务表，先备份数据库。

### 前端页面无法访问 API

确认后端端口 `8080` 正常，确认 Vite 代理或 `VITE_API_BASE_URL`。前端 API 封装在 [`../social-data-monitor/frontend/src/api/http.ts`](../social-data-monitor/frontend/src/api/http.ts)。

### B站采集失败

查看后端日志中的 `BilibiliFetchException`、`RISK_CONTROL`、`AUTH_REQUIRED`、`RATE_LIMITED`、`PARSE_ERROR` 等错误类型。项目不会绕过验证码或复杂风控；如接口变化，应先更新 [`bilibili-api-notes.md`](bilibili-api-notes.md) 和接口研究文档，再改代码。

直播监控失败时重点看 `BilibiliLiveFetchException`、直播监控业务表里的 `last_error_type` / `last_error_message`，以及 [`bilibili-live-room-api-research.md`](bilibili-live-room-api-research.md)。直播监控同样不做验证码、登录态或复杂风控绕过。

### 用户监控工作台头像不显示

先确认接口返回里有头像：

```powershell
(Invoke-RestMethod http://127.0.0.1:8080/api/subjects/1/workbench).data.subject.avatarUrl
```

如果接口有头像但页面显示 fallback，检查前端 `<img>` 是否保留 `referrerpolicy="no-referrer"`。B站 `i*.hdslb.com` 图片外链在部分浏览器里会受 Referer 影响。

### 弹幕昵称显示“昵称待补全”

这通常说明历史记录里只有 B站返回的脱敏昵称，且没有可用于回填的 UID。2026-06-17 后弹幕模块会优先使用已保存扫码登录态进入弹幕信息流，减少新弹幕昵称脱敏；如果登录态不可用或回退游客态，仍可能遇到脱敏。新弹幕包带 UID 时会尝试用公开用户卡片接口补全；已入库的旧脱敏记录无法保证恢复。
