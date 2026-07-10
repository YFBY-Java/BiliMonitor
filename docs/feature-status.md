# 当前功能状态

最后更新：2026-06-19

## 已实现

### B站用户粉丝数趋势监控

入口：`http://127.0.0.1:5173/bilibili`。

已支持：

- 输入 B站 UID 添加监控用户。
- 自动从 B站公开接口获取昵称、头像、当前粉丝数、关注数和空间链接。
- 多用户监控。
- 启用、停用、删除。
- 手动立即采集。
- 定时采集到期用户。
- 保存历史粉丝数快照。
- 趋势图展示。
- 单用户采集间隔配置。
- 采集间隔范围为 `1` 秒到 `2592000` 秒。
- 过短采集间隔在前端提示，后端写 warning。
- card 接口失败时，在部分错误场景下使用 `x/relation/stat` 兜底粉丝数。
- 头像加载失败时前端显示文字 fallback。

核心代码：

- 后端 API：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/controller/BilibiliFollowerMonitorController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/controller/BilibiliFollowerMonitorController.java)
- 后端服务：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/service/BilibiliFollowerMonitorService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/service/BilibiliFollowerMonitorService.java)
- B站客户端：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/client/BilibiliApiClient.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/client/BilibiliApiClient.java)
- 数据访问：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/repository/BilibiliFollowerMonitorRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/repository/BilibiliFollowerMonitorRepository.java)
- 前端页面：[`../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue`](../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue)
- 前端 API：[`../social-data-monitor/frontend/src/api/bilibili.ts`](../social-data-monitor/frontend/src/api/bilibili.ts)
- 图表组件：[`../social-data-monitor/frontend/src/components/charts/TrendChart.vue`](../social-data-monitor/frontend/src/components/charts/TrendChart.vue)

### B站直播间监控

入口：`http://127.0.0.1:5173/bilibili/live`。

已支持：

- 按 B站直播房间号或主播 UID 添加监控。
- 自动获取主播 UID、房间号、短号、主播昵称、头像、标题、封面、关键帧、分区、直播状态、在线/热度、直播开始时间等公开信息。
- 多直播间监控。
- 启用、停用、删除。
- 手动立即采集。
- 定时采集到期直播间。
- 保存直播间历史快照。
- 生成直播状态事件，例如开播、下播、轮播、标题变化。
- 单直播间采集间隔配置，范围为 `1` 秒到 `2592000` 秒。
- 趋势图展示在线/热度变化，支持最多 4 个直播间选择。
- 直播间卡片横向展示，点击展开详情。
- 直播间详情区已接入“房间观众与大航海”榜单，支持房间观众在线榜、进房、日榜、周榜、月榜，以及大航海周榜、月榜、陪伴榜。
- 直播榜单支持手动刷新快照，保存到 `bilibili_live_rank_snapshot` 和 `bilibili_live_rank_entry`，避免每次页面切换都重新打外部接口。
- 浅色/深色主题切换，主题状态保存在 `localStorage`。
- 深色主题已经按近期反馈做过多轮颜色、文字间距和边框优化。

核心代码：

- 后端 API：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/controller/BilibiliLiveMonitorController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/controller/BilibiliLiveMonitorController.java)
- 后端服务：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/service/BilibiliLiveMonitorService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/service/BilibiliLiveMonitorService.java)
- B站直播客户端：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/client/BilibiliLiveApiClient.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/client/BilibiliLiveApiClient.java)
- 数据访问：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/repository/BilibiliLiveMonitorRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/repository/BilibiliLiveMonitorRepository.java)
- 配置：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/config/BilibiliLiveMonitorProperties.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/config/BilibiliLiveMonitorProperties.java)
- 直播榜单 API：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/controller/BilibiliLiveRankController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/controller/BilibiliLiveRankController.java)
- 直播榜单服务：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/service/BilibiliLiveRankService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/service/BilibiliLiveRankService.java)
- 数据迁移：[`../social-data-monitor/backend/src/main/resources/db/migration/V4__bilibili_live_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V4__bilibili_live_monitor.sql)
- 榜单数据迁移：[`../social-data-monitor/backend/src/main/resources/db/migration/V8__bilibili_live_rank_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V8__bilibili_live_rank_monitor.sql)
- 前端页面：[`../social-data-monitor/frontend/src/views/bilibili-live/BilibiliLiveView.vue`](../social-data-monitor/frontend/src/views/bilibili-live/BilibiliLiveView.vue)
- 前端 API：[`../social-data-monitor/frontend/src/api/bilibiliLive.ts`](../social-data-monitor/frontend/src/api/bilibiliLive.ts)

接口资料：

- 主研究记录：[`bilibili-live-room-api-research.md`](bilibili-live-room-api-research.md)
- 第三方 SDK 风控参考：[`bilibili-api-libraries-risk-control-research.md`](bilibili-api-libraries-risk-control-research.md)
- 已实现前的设计稿已归档到 [`archive/implemented-live-monitor/README.md`](archive/implemented-live-monitor/README.md)。

### B站扫码登录获取登录态

入口：`http://127.0.0.1:5173/bilibili` 顶部登录态面板。

当前状态：首期代码已经实现，并已通过真实扫码成功闭环验收。

已支持：

- 前端显示 B站登录态状态，支持打开扫码登录弹窗。
- 后端生成 Bilibili Web 二维码，并返回 `loginId`、`qrUrl`、过期时间和轮询间隔。
- 前端使用 `qrcode` 渲染二维码，并按后端返回的轮询间隔查询扫码状态。
- 后端支持扫码状态映射：`WAITING`、`SCANNED`、`EXPIRED`、`SUCCESS`、`FAILED`。
- 登录成功时后端会从扫码会话 Cookie、`Set-Cookie` 和回调 URL 中提取 `SESSDATA`、`bili_jct`、`DedeUserID`、`refresh_token` 等字段。
- 后端使用 `https://api.bilibili.com/x/web-interface/nav` 校验登录态。
- 凭据保存到 `platform_credential`，`encrypted_payload` 使用 AES-GCM 加密。
- 账号展示信息保存/更新到 `platform_account`。
- 前端支持查看完整登录态、刷新校验和移除登录态。
- 登录态字段按当前项目约定完整返回和展示，不做脱敏、截断或 hash。
- 后端重启后可以从数据库恢复凭据，并重新通过 Bilibili `nav` 校验。
- 直播弹幕模块会在配置允许时复用该登录态调用 `getDanmuInfo`，并在 WebSocket 鉴权包中使用同一账号 mid；这能减少游客态昵称脱敏问题。

核心代码：

- 后端 API：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/controller/BilibiliAuthController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/controller/BilibiliAuthController.java)
- 后端服务：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/service/BilibiliAuthService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/service/BilibiliAuthService.java)
- B站扫码客户端：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/client/BilibiliPassportClient.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/client/BilibiliPassportClient.java)
- 凭据加密：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/service/BilibiliCredentialCipher.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/service/BilibiliCredentialCipher.java)
- 数据访问：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/repository/BilibiliCredentialRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/repository/BilibiliCredentialRepository.java)
- 数据迁移：[`../social-data-monitor/backend/src/main/resources/db/migration/V7__bilibili_auth_credential.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V7__bilibili_auth_credential.sql)
- 前端 API：[`../social-data-monitor/frontend/src/api/bilibiliAuth.ts`](../social-data-monitor/frontend/src/api/bilibiliAuth.ts)
- 前端面板：[`../social-data-monitor/frontend/src/views/bilibili/components/BilibiliAuthPanel.vue`](../social-data-monitor/frontend/src/views/bilibili/components/BilibiliAuthPanel.vue)
- 扫码弹窗：[`../social-data-monitor/frontend/src/views/bilibili/components/BilibiliQrLoginDialog.vue`](../social-data-monitor/frontend/src/views/bilibili/components/BilibiliQrLoginDialog.vue)

### B站指定用户监控工作台

入口：

- 用户列表：`http://127.0.0.1:5173/subjects`
- 单用户工作台：`http://127.0.0.1:5173/subjects/{subjectId}`

该功能是一个 Subject 聚合层，不替代也不重写已有 B站粉丝监控和直播间监控。它把同一个 B站 UID 对应的粉丝监控、直播间监控、直播弹幕监控聚合到一个“指定用户工作台”里。

已支持：

- 左侧菜单入口“用户监控”。
- 输入 B站 UID 创建 Subject。
- 创建 Subject 时自动查找或创建对应的 B站粉丝监控。
- 创建 Subject 时尝试按 UID 查找或创建对应的直播间监控；如果目标 UID 没有可解析直播间，会保留粉丝监控并记录日志，不强行失败。
- Subject 自动保存或回填昵称、头像 URL。
- 头像前端使用 `referrerpolicy="no-referrer"` 加载，避免 B站图片外链 Referer 导致显示 fallback。
- Subject 列表、详情、绑定资源、删除。
- 工作台顶部展示用户资料、直播状态、模块数量和采集健康。
- 四个核心指标：总粉丝数、直播间热度、弹幕速率、最近成功采集。
- 左侧主卡片“粉丝数与直播热度监控”，把粉丝数和直播间热度放在一个双指标趋势图内。
- 右侧“直播间弹幕监控”卡片，和左侧趋势图同一行等高展示。
- 右侧卡片现在可在 `弹幕`、`房间观众`、`大航海` 三种视图间切换：弹幕视图保留实时 WebSocket 指标和最近弹幕，房间观众/大航海视图复用直播榜单 `summary/refresh` API 展示已保存快照。
- 弹幕卡片固定高度，弹幕列表内部滚动。
- 弹幕卡片前端每 2 秒轮询 `/danmaku/status` 和 `/danmaku/recent`，实现实时更新，不刷新整个页面。
- 弹幕 WebSocket 支持自动协议兼容，也可手动指定 `protover=0/1/2/3`。
- 支持手动开启未开播直播间的弹幕监控。
- 弹幕信息流优先使用已保存 B站登录态获取 token 和认证 WebSocket，状态 API 会返回 `authMode`、`authUid` 便于确认当前是 `LOGIN` 还是 `ANONYMOUS`。
- 登录态不可用、过期或触发 B站风控时，弹幕模块自动回退到游客态，不做验证码或复杂风控绕过。
- 弹幕消息保存最近消息和分钟级指标桶。
- 弹幕昵称不做项目侧脱敏；如果 B站弹幕包只给 `***` 脱敏名且包内带 UID，后端会用公开用户卡片接口补全真实昵称，并做 12 小时缓存。
- 老历史弹幕如果已经只保存脱敏名且没有 UID，前端显示“昵称待补全”，不会继续直接展示 `***`。
- 弹幕列表在鼠标没有悬停于弹幕监控区域时自动滚到最新；鼠标悬停在弹幕监控卡片区域时暂停自动下滑，移出后恢复追最新。
- 趋势图高度已增大，纵轴范围按当前数据动态计算；小幅变化会比早期版本更明显，但仍保留缓冲区避免过度夸张。
- 下方有“采集健康与事件”和“待添加模块”预留位。

核心代码：

- 后端 API：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/controller/SubjectController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/controller/SubjectController.java)
- Subject 服务：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectService.java)
- Workbench 聚合服务：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectWorkbenchService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectWorkbenchService.java)
- Subject 数据访问：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/repository/SubjectRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/repository/SubjectRepository.java)
- 弹幕服务：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/service/BilibiliLiveDanmakuService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/service/BilibiliLiveDanmakuService.java)
- 弹幕包解析：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuPacketCodec.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuPacketCodec.java)
- 弹幕事件解析：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuEventParser.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuEventParser.java)
- 直播榜单服务：[`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/service/BilibiliLiveRankService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/service/BilibiliLiveRankService.java)
- 前端 API：[`../social-data-monitor/frontend/src/api/subjects.ts`](../social-data-monitor/frontend/src/api/subjects.ts)
- 用户列表页：[`../social-data-monitor/frontend/src/views/subjects/SubjectListView.vue`](../social-data-monitor/frontend/src/views/subjects/SubjectListView.vue)
- 工作台页：[`../social-data-monitor/frontend/src/views/subjects/SubjectWorkbenchView.vue`](../social-data-monitor/frontend/src/views/subjects/SubjectWorkbenchView.vue)
- 工作台组件目录：[`../social-data-monitor/frontend/src/views/subjects/`](../social-data-monitor/frontend/src/views/subjects/)
- 数据迁移：[`../social-data-monitor/backend/src/main/resources/db/migration/V5__subject_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V5__subject_monitor.sql)、[`../social-data-monitor/backend/src/main/resources/db/migration/V6__bilibili_live_danmaku_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V6__bilibili_live_danmaku_monitor.sql)

### 多平台监控基础骨架

已存在但不是当前完整业务闭环：

- 平台适配器注册表。
- 采集任务执行骨架。
- API 调用日志、原始载荷、任务 checkpoint。
- B站平台适配器。
- AI mock 分析接口。
- 身份映射服务雏形。
- 前端多个页面入口。

这些能力更多是为后续多平台扩展准备，当前请以 B站粉丝趋势监控和 B站直播间监控这两条专项功能为主线。

## 当前验证状态

近期功能实现和样式迭代中已经通过：

- 后端 `.\mvnw.cmd test`。
- 前端 `npm run typecheck`。
- 前端 `npm run build`。
- 本地开发脚本 `.\scripts\dev-start.cmd` 可以启动 PostgreSQL、后端和前端。
- `GET http://127.0.0.1:8080/actuator/health` 可返回 `UP`。
- `/bilibili` 粉丝监控页可添加用户、刷新数据、展示头像和趋势图。
- `/bilibili/live` 直播监控页可添加直播间、刷新直播状态、展示直播间卡片、详情和趋势图。
- `/subjects` 用户监控列表可打开。
- `/subjects/{subjectId}` 用户工作台可展示头像、粉丝数、直播热度、弹幕指标、双指标趋势图和弹幕列表。
- 使用系统 Chrome 验证过 `1440x900` 和 `1024x900` 视口；头像正常加载，弹幕卡片与趋势图同高，窄桌面下工作台上下堆叠可用。
- 2026-06-16 验证过 B站扫码登录首期：
  - 后端 `.\mvnw.cmd test` 通过。
  - 前端 `npm run typecheck` 通过。
  - 前端 `npm run build` 通过。
  - `GET /api/bilibili/auth/status` 在无凭据时返回 `loggedIn=false`、`status=NONE`。
  - `POST /api/bilibili/auth/qr/start` 能返回二维码登录会话。
  - `GET /api/bilibili/auth/qr/{loginId}/status` 未扫码时返回 `WAITING`。
  - 外置 Chrome 截图验证过 `/bilibili` 登录态面板和扫码弹窗不是空白。
  - 用户真实扫码后，`GET /api/bilibili/auth/status` 返回 `loggedIn=true`、`status=ACTIVE`。
  - `GET /api/bilibili/auth/credential` 可解密返回 `SESSDATA`、`bili_jct`、`DedeUserID`、`DedeUserID__ckMd5`、`sid`、CSRF 和 `refreshToken`。
- 数据库存在 1 条 `BILIBILI_WEB_COOKIE` 的 `ACTIVE` 凭据，关联账号 UID `<bilibili_uid>`。
- 数据库 `encrypted_payload` 不包含明文 `SESSDATA` 或 `bili_jct`。
- 重启后端后，`GET /api/bilibili/auth/status` 仍返回 `ACTIVE/loggedIn=true`，`lastValidatedAt` 会更新。
- 2026-06-17 验证过弹幕登录态复用：
  - 后端 `.\mvnw.cmd test` 通过。
  - `GET /api/bilibili/auth/status` 返回 `loggedIn=true`、`status=ACTIVE`。
  - 手动启动直播间 `roomMonitorId=5` 的弹幕连接后，`GET /api/bilibili/live-monitor/rooms/5/danmaku/status` 返回 `status=CONNECTED`、`protocolVersion=3`、`authMode=LOGIN`、`authUid=<bilibili_uid>`。
  - 25 秒内新采弹幕 16 条，空昵称或打码昵称计数为 0。
- 2026-06-17 验证过前端弹幕滚动交互修改：`npm run typecheck` 通过。此前同日弹幕滚动初版已跑过 `npm run build`。
- 2026-06-19 验证过用户工作台右侧卡片多视图修改：
  - 前端 `npm run typecheck` 通过。
  - 前端 `npm run build` 通过。
  - `GET http://127.0.0.1:5173/subjects` 返回 200。
  - `GET /api/subjects/7/workbench` 可返回工作台聚合数据和直播监控绑定。
  - `GET /api/bilibili/live-monitor/rooms/7/ranks/summary` 可返回房间观众和大航海榜单快照。
  - 使用系统 Chrome headless 检查过 `http://127.0.0.1:5173/subjects/7`，右侧卡片默认弹幕视图、三段切换控件和整体布局没有空白或明显错位。

最近一次业务修改验证时间：2026-06-19。

## 已知限制和风险

### B站接口稳定性

当前采用公开 Web card 接口，风控复杂度最低，但 B站接口仍可能调整字段、返回码或访问策略。系统不会、也不应实现验证码绕过、登录态抓取或复杂风控规避。

直播监控采用匿名公开直播接口组合，当前不依赖登录态、CSRF 或 WBI。B站仍可能调整直播接口字段或访问策略，异常时先查 [`bilibili-live-room-api-research.md`](bilibili-live-room-api-research.md) 和后端日志。

直播弹幕 WebSocket 当前优先复用用户扫码保存的登录态调用 `getDanmuInfo` 并发送登录 UID 鉴权包，失败时回退匿名连接。项目支持 `protover=0/1/2/3`，但不会实现验证码绕过或复杂风控绕过。B站弹幕事件字段变化时，优先检查 [`bilibili-live-danmaku-research.md`](bilibili-live-danmaku-research.md)、`BilibiliLiveDanmakuPacketCodec`、`BilibiliLiveDanmuInfoClient` 和 `BilibiliLiveDanmakuEventParser`。

直播房间观众与大航海榜单依赖 B站公开榜单接口和当前可用的签名/请求策略。`ranks/summary` 优先读取已保存快照，`ranks/refresh` 才会主动刷新外部榜单；后续如果 B站调整榜单字段、WBI 策略或分页规则，需要先看 [`bilibili-live-room-audience-guard-rank-research.md`](bilibili-live-room-audience-guard-rank-research.md) 和 `BilibiliLiveRankApiClient`，不要做验证码或复杂风控绕过。

### `1` 秒间隔不是无限高频保证

系统允许单用户配置 `1` 秒采集间隔，数据库约束也允许。但后端还有：

- 全局请求间隔 `SOCIAL_MONITOR_BILIBILI_REQUEST_MIN_INTERVAL_MS`，默认 `1500` ms。
- 到期用户批量大小限制。
- 失败退避。
- B站接口自身的限流或风控。

所以短间隔适合开发演示或少量用户观察，不适合大量用户长期高频请求。

### 删除用户会删除历史快照

`bilibili_follower_snapshot.monitored_user_id` 使用 `ON DELETE CASCADE`。删除监控用户会连带删除其历史趋势数据。如果只想暂停，请使用停用。

直播监控同样如此：`bilibili_live_room_snapshot` 和 `bilibili_live_status_event` 关联 `bilibili_live_room_monitor(id)`，删除直播间监控会删除对应快照和事件。只想停止采集时请停用。

### 弹幕昵称补全不是历史修复

2026-06-13 后端已改为优先保存未脱敏昵称，并在遇到脱敏名且弹幕包带 UID 时尝试用公开用户卡片接口补全真实昵称。限制：

- 已经入库的旧弹幕如果只保存了 `***` 脱敏名且没有 UID，无法可靠反查。
- 如果 B站弹幕包不带 UID，或公开用户卡片接口失败，仍只能显示前端 fallback“昵称待补全”。
- 补全逻辑带 12 小时成功缓存和 10 分钟失败缓存，避免每条弹幕都请求 B站接口。

### 前端 B站页面较集中

`BilibiliView.vue` 当前包含页面逻辑和几个局部子组件。功能继续增长时，可以再拆组件，但不要为了“看起来更架构化”提前大改。

### 本机运行态不是源码

`.dev-tools/`、`.dev-data/`、日志和截图是本机运行态或验证产物。换机器时不能假设它们存在。

### 扫码登录后续能力

B站扫码登录首期闭环已经验证通过。后续仍需注意：

- 完整 Cookie 刷新链路尚未实现；当前刷新接口主要是重新校验当前登录态。
- 当前 `/api/**` 仍是开发期放行，生产前必须给 `/api/bilibili/auth/**` 接入管理员鉴权。
- 如果 Bilibili 端退出登录或 Cookie 失效，系统应提示重新扫码。

## 后续 TODO

- 为 B站监控服务补更细的单元测试和 repository 集成测试。
- 为前端 B站粉丝页和直播页补组件测试或端到端 smoke test。
- 增加“刷新基础资料但不采集粉丝”的轻量操作，目前资料随 card 采集刷新。
- 为删除操作提供“停用优先”的二次确认文案，减少误删历史。
- 如果监控用户规模扩大，引入更明确的队列和速率策略。
- 直播监控后续可补直播状态事件筛选、分区筛选、批量刷新和更明确的数据导出。
- 用户监控工作台后续可补可视化布局编辑、更多 Widget、弹幕关键词/事件统计、用户级数据导出。
- 如果接口返回结构变化，先更新 [`bilibili-api-notes.md`](bilibili-api-notes.md) 和 [`../social-data-monitor/docs/bilibili-follower-api-research.md`](../social-data-monitor/docs/bilibili-follower-api-research.md)，再改客户端解析。
- 扫码登录下一步优先补 Cookie 刷新链路和生产级管理员鉴权。
