# 下次接手提示

最后更新：2026-06-19

这份文档给下次的你，也给下次接手的 Codex。目标是用最少时间重新建立上下文。

## 最短路径

1. 先读 [`README.md`](README.md) 确认文档地图。
2. 要运行项目，读 [`runbook.md`](runbook.md)。
3. 要改 B站粉丝监控，读 [`architecture.md`](architecture.md)、[`feature-status.md`](feature-status.md)、[`bilibili-api-notes.md`](bilibili-api-notes.md)。
4. 要改 B站直播监控，读 [`architecture.md`](architecture.md)、[`feature-status.md`](feature-status.md)、[`bilibili-live-room-api-research.md`](bilibili-live-room-api-research.md)。
5. 要改“用户监控”聚合工作台，读 [`feature-status.md`](feature-status.md)、[`architecture.md`](architecture.md)、[`frontend-notes.md`](frontend-notes.md)、[`data-model.md`](data-model.md)。
6. 要改数据库，读 [`data-model.md`](data-model.md)。
7. 要改页面，读 [`frontend-notes.md`](frontend-notes.md)。
8. 要继续 B站扫码登录/登录态功能，先读 [`../social-data-monitor/docs/bilibili-qr-login-design.md`](../social-data-monitor/docs/bilibili-qr-login-design.md)，再看 [`bilibili-login-research.md`](bilibili-login-research.md)。

## 当前可以认为是真的

- 当前可运行应用在 `social-data-monitor/`。
- B站粉丝数采集逻辑已经实现，不要重写。
- B站头像和基础资料目前也走 `x/web-interface/card`。
- `x/relation/stat` 只是已有用户刷新时的粉丝数兜底，不提供头像。
- B站直播间监控已经实现，不要再按早期方案稿从零落地。
- 直播间状态监控当前走匿名公开直播接口组合，不需要登录态或 CSRF；直播弹幕模块会在有扫码登录态时优先复用登录态，失败时回退游客态。
- 直播页详情区已经接入“房间观众与大航海”榜单，快照存储在 `bilibili_live_rank_snapshot` 和 `bilibili_live_rank_entry`。
- B站指定用户监控工作台首版已经实现，入口是 `/subjects` 和 `/subjects/:subjectId`，不要再按 `bilibili-user-monitor-workbench-technical-plan.md` 从零落地。
- Subject 创建时传 `mid` 会自动复用或创建粉丝监控，并尝试复用或创建直播间监控。
- 用户工作台已经接入直播弹幕 WebSocket 状态和最近弹幕，前端每 2 秒轮询刷新。
- 用户工作台右侧 `BilibiliLiveDanmuWidget.vue` 已支持 `弹幕`、`房间观众`、`大航海` 三视图切换；榜单视图复用直播榜单 `summary/refresh` API，不复制数据到 Subject 表。
- 弹幕 WebSocket 支持 `protover=0/1/2/3`，默认自动候选优先 `3`。
- 弹幕 WebSocket 现在优先用已保存登录态调用 `getDanmuInfo`，并在鉴权包里写同一账号 mid；`/danmaku/status` 暴露 `authMode` 和 `authUid` 方便确认当前是否为 `LOGIN`。
- 弹幕昵称获取不再只靠游客态；游客态仍作为登录态不可用、过期或触发风控时的回退。
- 用户工作台弹幕列表在鼠标未悬停弹幕监控区域时自动追最新；鼠标悬停在整个弹幕监控卡片区域时暂停自动下滑，移出后恢复。
- 工作台头像自动回填；前端头像图片需要保留 `referrerpolicy="no-referrer"`。
- 采集间隔最小支持 `1` 秒，最大支持 `2592000` 秒。
- B站 Web 扫码登录获取登录态的首期代码已经实现并通过真实扫码验收，入口在 `/bilibili` 页面顶部的登录态面板。
- 登录态后端接口集中在 `/api/bilibili/auth/**`，后端会生成二维码、轮询扫码状态、提取 Cookie、用 `x/web-interface/nav` 校验，并把登录态加密保存到 `platform_credential`。
- 登录态完整值按项目当前约定完整返回和展示，不做脱敏；生产前必须给 `/api/bilibili/auth/**` 接入管理员鉴权。
- 已验证真实扫码成功、凭据入库、完整登录态字段可解密读取、后端重启后仍可通过 `nav` 校验。
- 后端、前端和数据库在本机已经能跑起来。
- `/api/**` 当前开发期放行，无登录鉴权。

## 开始修改前先看这些文件

后端：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/client/BilibiliApiClient.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/client/BilibiliApiClient.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/service/BilibiliFollowerMonitorService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/service/BilibiliFollowerMonitorService.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/repository/BilibiliFollowerMonitorRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/repository/BilibiliFollowerMonitorRepository.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/controller/BilibiliFollowerMonitorController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/controller/BilibiliFollowerMonitorController.java)
- [`../social-data-monitor/backend/src/main/resources/application.yml`](../social-data-monitor/backend/src/main/resources/application.yml)
- [`../social-data-monitor/backend/src/main/resources/db/migration/`](../social-data-monitor/backend/src/main/resources/db/migration/)

直播后端：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/client/BilibiliLiveApiClient.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/client/BilibiliLiveApiClient.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/service/BilibiliLiveMonitorService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/service/BilibiliLiveMonitorService.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/repository/BilibiliLiveMonitorRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/repository/BilibiliLiveMonitorRepository.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/controller/BilibiliLiveMonitorController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/controller/BilibiliLiveMonitorController.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/controller/BilibiliLiveRankController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/controller/BilibiliLiveRankController.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/service/BilibiliLiveRankService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/service/BilibiliLiveRankService.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/repository/BilibiliLiveRankRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/rank/repository/BilibiliLiveRankRepository.java)
- [`../social-data-monitor/backend/src/main/resources/db/migration/V4__bilibili_live_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V4__bilibili_live_monitor.sql)
- [`../social-data-monitor/backend/src/main/resources/db/migration/V8__bilibili_live_rank_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V8__bilibili_live_rank_monitor.sql)

用户监控和弹幕后端：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/controller/SubjectController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/controller/SubjectController.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectService.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectWorkbenchService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/service/SubjectWorkbenchService.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/repository/SubjectRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/subject/repository/SubjectRepository.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/service/BilibiliLiveDanmakuService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/service/BilibiliLiveDanmakuService.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuPacketCodec.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuPacketCodec.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuEventParser.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/danmaku/parser/BilibiliLiveDanmakuEventParser.java)
- [`../social-data-monitor/backend/src/main/resources/db/migration/V5__subject_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V5__subject_monitor.sql)
- [`../social-data-monitor/backend/src/main/resources/db/migration/V6__bilibili_live_danmaku_monitor.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V6__bilibili_live_danmaku_monitor.sql)

登录态后端：

- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/controller/BilibiliAuthController.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/controller/BilibiliAuthController.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/service/BilibiliAuthService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/service/BilibiliAuthService.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/client/BilibiliPassportClient.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/client/BilibiliPassportClient.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/service/BilibiliCredentialCipher.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/service/BilibiliCredentialCipher.java)
- [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/repository/BilibiliCredentialRepository.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/auth/repository/BilibiliCredentialRepository.java)
- [`../social-data-monitor/backend/src/main/resources/db/migration/V7__bilibili_auth_credential.sql`](../social-data-monitor/backend/src/main/resources/db/migration/V7__bilibili_auth_credential.sql)

前端：

- [`../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue`](../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue)
- [`../social-data-monitor/frontend/src/views/bilibili-live/BilibiliLiveView.vue`](../social-data-monitor/frontend/src/views/bilibili-live/BilibiliLiveView.vue)
- [`../social-data-monitor/frontend/src/views/subjects/SubjectListView.vue`](../social-data-monitor/frontend/src/views/subjects/SubjectListView.vue)
- [`../social-data-monitor/frontend/src/views/subjects/SubjectWorkbenchView.vue`](../social-data-monitor/frontend/src/views/subjects/SubjectWorkbenchView.vue)
- [`../social-data-monitor/frontend/src/views/subjects/`](../social-data-monitor/frontend/src/views/subjects/)
- [`../social-data-monitor/frontend/src/api/bilibili.ts`](../social-data-monitor/frontend/src/api/bilibili.ts)
- [`../social-data-monitor/frontend/src/api/bilibiliLive.ts`](../social-data-monitor/frontend/src/api/bilibiliLive.ts)
- [`../social-data-monitor/frontend/src/api/subjects.ts`](../social-data-monitor/frontend/src/api/subjects.ts)
- [`../social-data-monitor/frontend/src/api/bilibiliAuth.ts`](../social-data-monitor/frontend/src/api/bilibiliAuth.ts)
- [`../social-data-monitor/frontend/src/views/bilibili/components/BilibiliAuthPanel.vue`](../social-data-monitor/frontend/src/views/bilibili/components/BilibiliAuthPanel.vue)
- [`../social-data-monitor/frontend/src/views/bilibili/components/BilibiliQrLoginDialog.vue`](../social-data-monitor/frontend/src/views/bilibili/components/BilibiliQrLoginDialog.vue)
- [`../social-data-monitor/frontend/src/components/charts/TrendChart.vue`](../social-data-monitor/frontend/src/components/charts/TrendChart.vue)
- [`../social-data-monitor/frontend/src/router/index.ts`](../social-data-monitor/frontend/src/router/index.ts)

接口资料：

- [`../social-data-monitor/docs/bilibili-follower-api-research.md`](../social-data-monitor/docs/bilibili-follower-api-research.md)
- [`../bilibili-api-collect-new-research/repo/docs/user/info.md`](../bilibili-api-collect-new-research/repo/docs/user/info.md)
- [`../bilibili-api-collect-new-research/repo/docs/user/status_number.md`](../bilibili-api-collect-new-research/repo/docs/user/status_number.md)
- [`../bilibili-api-collect-new-research/repo/docs/user/relation.md`](../bilibili-api-collect-new-research/repo/docs/user/relation.md)
- [`bilibili-live-room-api-research.md`](bilibili-live-room-api-research.md)
- [`bilibili-live-room-audience-guard-rank-research.md`](bilibili-live-room-audience-guard-rank-research.md)
- [`archive/implemented-live-monitor/README.md`](archive/implemented-live-monitor/README.md)：只作历史方案参考。

## 不要轻易做的事

- 不要推翻重写 `social-data-monitor/`。
- 不要把 B站采集逻辑从现有 service/repository/client 边界里拆散。
- 不要绕过验证码、登录态限制、WBI、CSRF 或其他平台安全机制。
- 不要为了 `1` 秒间隔去移除全局限频。
- 不要删除 `.dev-data/` 或数据库表来“解决迁移问题”，除非明确备份并确认可以丢数据。
- 不要把 `multi-social-platform-monitoring/` 当成可运行工程，它主要是方案文档。
- 不要把 `archive/implemented-live-monitor/` 里的直播设计稿当成当前待办；实际实现已经在 `social-data-monitor/`。
- 不要把 `bilibili-user-monitor-workbench-technical-plan.md` 当成未完成任务清单；用户监控工作台首版已经实现，方案文档只是背景参考。
- 不要打印或泄漏 B站登录 Cookie、CSRF、refreshToken。弹幕模块只允许使用用户已扫码保存的正常登录态获取 `getDanmuInfo` 和 WebSocket 鉴权，不要实现验证码、复杂风控绕过或抓取额外隐私接口。

## 推荐验证清单

改后端后：

```powershell
cd social-data-monitor\backend
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test
```

改前端后：

```powershell
cd social-data-monitor\frontend
npm run typecheck
npm run build
```

运行态 smoke test：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/follower-monitor/users
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/live-monitor/rooms
Invoke-RestMethod http://127.0.0.1:8080/api/subjects
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/auth/status
# 如果已有直播监控 id，可额外检查榜单摘要：
# Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/summary
```

浏览器检查：

```text
http://127.0.0.1:5173/bilibili
http://127.0.0.1:5173/bilibili/live
http://127.0.0.1:5173/subjects
```

重点看：

- 新增 UID 是否能获取昵称和头像。
- 头像是否展示，失败 fallback 是否可用。
- 当前粉丝数是否刷新。
- 趋势图是否有点。
- `1` 秒间隔是否能保存。
- 多用户和移动端布局是否正常。
- 直播间页面能否添加房间号或 UID。
- 直播间卡片、详情、启用/停用、刷新和趋势图是否正常。
- 深色/浅色主题是否正常，尤其是近期反复调过的直播页深色头部和边框。
- `/subjects` 能否创建/进入用户工作台。
- 用户工作台头像是否展示，若接口有头像但页面 fallback，检查 `referrerpolicy="no-referrer"`。
- 用户工作台双指标趋势图是否有点，变化幅度是否可读。
- 弹幕监控卡片是否实时更新、是否和左侧趋势图同高。
- 用户工作台右侧卡片是否能在 `弹幕`、`房间观众`、`大航海` 三个视图之间切换；有直播监控绑定时，榜单视图是否能展示最近快照或空状态。
- 直播页详情区“房间观众与大航海”是否能切换榜单类型，并能手动刷新榜单。
- 旧弹幕脱敏名是否显示为“昵称待补全”，新弹幕是否尽量补全真实昵称。
- 鼠标悬停在弹幕监控区域时，弹幕列表是否暂停自动下滑；移出后是否恢复追最新。
- `/bilibili` 顶部登录态面板是否能显示当前登录账号，必要时再打开扫码弹窗重扫。

## 常见下一步任务

- 增加更明确的资料刷新按钮。
- 优化多用户趋势对比。
- 增加监控用户搜索、排序和分组。
- 增加数据导出。
- 增加后端集成测试。
- 为直播监控补事件筛选、分区筛选和更多批量操作。
- 为用户监控工作台补可视化布局编辑、更多 Widget、弹幕关键词统计和数据导出。
- 后续再实现 B站 Cookie 刷新链路；当前 `/api/bilibili/auth/refresh` 只做 nav 重新校验，不是完整刷新。
- 为 CI 补后端测试和前端构建流程。

## 未确认项

- 根目录 `.` 以及三个子工程目录当前都不是 Git 仓库，`git status` 返回 `fatal: not a git repository`。如果需要提交或发 PR，下次应先确认真实版本控制位置。
- 本机便携 PostgreSQL 的初始化来源未文档化，只确认当前目录存在且端口可用。
- 当前鉴权是开发期放行状态，生产部署前需要重新设计认证、权限和 CORS。
- B站扫码登录已完成真实手机扫码确认；后续仍需补完整 Cookie 刷新链路和生产级管理员鉴权。
- 当前没有确认线上部署方式、Dockerfile、CI 配置或发布环境。
- 弹幕昵称优先依赖扫码登录态下的 B站弹幕信息流；登录态失效、被风控或游客态回退时仍可能遇到 `uid=0` 或脱敏昵称。旧历史脱敏名如果没有 UID，仍无法可靠恢复。
