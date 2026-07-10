# BiliMonitor 项目文档

最后更新：2026-06-19

本目录是 `.` 的根级交接文档，面向下次运行项目、下次继续开发，以及下次接手的 Codex。当前真正可运行的应用工程在 [`../social-data-monitor/`](../social-data-monitor/)。

## 快速入口

- 下次想快速了解当前工程全貌：先看 [`current-project-summary.md`](current-project-summary.md)。
- 下次想把项目跑起来：先看 [`runbook.md`](runbook.md)。
- 下次想继续开发：先看 [`handoff.md`](handoff.md)，再看 [`architecture.md`](architecture.md) 和 [`feature-status.md`](feature-status.md)。
- 下次想查 B站接口选型：看 [`bilibili-api-notes.md`](bilibili-api-notes.md)，详细原始研究记录在 [`../social-data-monitor/docs/bilibili-follower-api-research.md`](../social-data-monitor/docs/bilibili-follower-api-research.md)。
- 下次想查 B站直播间接口选型：看 [`bilibili-live-room-api-research.md`](bilibili-live-room-api-research.md)。
- 下次想查 B站直播房间观众/大航海榜单接口：看 [`bilibili-live-room-audience-guard-rank-research.md`](bilibili-live-room-audience-guard-rank-research.md)。
- 下次想查 B站直播弹幕和 WebSocket 信息流方案：看 [`bilibili-live-danmaku-research.md`](bilibili-live-danmaku-research.md)。当前实现是登录态优先、游客态回退。
- 下次想继续 B站扫码登录/登录态功能：先看 [`../social-data-monitor/docs/bilibili-qr-login-design.md`](../social-data-monitor/docs/bilibili-qr-login-design.md)，再看 [`bilibili-login-research.md`](bilibili-login-research.md) 和 [`handoff.md`](handoff.md)。
- 下次想改“用户监控”聚合工作台和 B站直播弹幕模块：先看 [`feature-status.md`](feature-status.md)、[`architecture.md`](architecture.md)、[`data-model.md`](data-model.md) 和 [`frontend-notes.md`](frontend-notes.md)；早期技术方案保留在 [`bilibili-user-monitor-workbench-technical-plan.md`](bilibili-user-monitor-workbench-technical-plan.md)，最终样式图见 [`mockups/bilibili-user-monitor-style-a-combined-chart.png`](mockups/bilibili-user-monitor-style-a-combined-chart.png)。
- 下次想查抖音、微博、小红书是否能抓公开用户名称/粉丝数等数据：看 [`douyin-weibo-xiaohongshu-public-user-data-research.md`](douyin-weibo-xiaohongshu-public-user-data-research.md)。
- 下次想改 B站直播间监控功能：看 [`feature-status.md`](feature-status.md)、[`architecture.md`](architecture.md)、[`frontend-notes.md`](frontend-notes.md)。
- 下次想回看已经落地的 B站直播页方案稿：看 [`archive/implemented-live-monitor/README.md`](archive/implemented-live-monitor/README.md)。
- 下次想看“指定用户多维度、多平台监控”方案：看 [`specified-user-multiplatform-monitor-design.md`](specified-user-multiplatform-monitor-design.md)，三版样式稿见 [`mockups/specified-user-monitor-styles.html`](mockups/specified-user-monitor-styles.html)，预览图见 [`mockups/specified-user-monitor-styles-preview.png`](mockups/specified-user-monitor-styles-preview.png)。
- 下次想查第三方 B站 API SDK 的风控实现：看 [`bilibili-api-libraries-risk-control-research.md`](bilibili-api-libraries-risk-control-research.md)。
- 下次想查第三方 SDK 里“如何识别/处理风控”的深挖结论：看 [`bilibili-risk-control-deep-dive.md`](bilibili-risk-control-deep-dive.md)。
- 下次想改数据库或排查历史数据：看 [`data-model.md`](data-model.md)。
- 下次想改页面布局、趋势图或交互：看 [`frontend-notes.md`](frontend-notes.md)。

## 文档地图

| 文档 | 用途 |
| --- | --- |
| [`current-project-summary.md`](current-project-summary.md) | 当前工程的一页式详细总结，串联运行入口、技术栈、数据表、API、页面、功能状态和风险。 |
| [`project-overview.md`](project-overview.md) | 仓库内各子工程、运行态目录、当前项目定位。 |
| [`runbook.md`](runbook.md) | 依赖安装、环境变量、数据库、后端、前端、构建与常用排查命令。 |
| [`architecture.md`](architecture.md) | `social-data-monitor/` 的后端模块、前端模块、采集链路和 API 边界。 |
| [`feature-status.md`](feature-status.md) | 已实现能力、当前 B站粉丝监控和直播监控功能状态、已知风险和 TODO。 |
| [`bilibili-api-notes.md`](bilibili-api-notes.md) | B站粉丝数、头像、基础资料相关接口对比和最终选型。 |
| [`bilibili-live-room-api-research.md`](bilibili-live-room-api-research.md) | B站直播间相关接口、风控程度、请求参数、可获取数据和接入优先级。 |
| [`bilibili-live-room-audience-guard-rank-research.md`](bilibili-live-room-audience-guard-rank-research.md) | B站直播间房间观众、大航海榜单接口、字段和接入边界。 |
| [`bilibili-live-danmaku-research.md`](bilibili-live-danmaku-research.md) | B站直播弹幕与 WebSocket 信息流的低成本获取方案、实测结果、事件清单和接入路线。 |
| [`bilibili-login-research.md`](bilibili-login-research.md) | B站 Web 扫码登录、Cookie、`refresh_token`、`nav` 校验和刷新链路研究。 |
| [`../social-data-monitor/docs/bilibili-qr-login-design.md`](../social-data-monitor/docs/bilibili-qr-login-design.md) | B站扫码登录获取登录态的当前设计、已实现状态、接口、数据表和验收清单。 |
| [`bilibili-user-monitor-workbench-technical-plan.md`](bilibili-user-monitor-workbench-technical-plan.md) | 指定用户监控工作台早期技术方案；当前首版已经落地，真实状态以 [`feature-status.md`](feature-status.md) 为准。 |
| [`douyin-weibo-xiaohongshu-public-user-data-research.md`](douyin-weibo-xiaohongshu-public-user-data-research.md) | 不按开放平台口径，调研抖音、微博、小红书公开用户名称、粉丝数、关注数等数据的可得性、风控依赖和接入建议。 |
| [`specified-user-multiplatform-monitor-design.md`](specified-user-multiplatform-monitor-design.md) | 指定用户多维度、多平台监控方案，包含页面、UI、三版风格和一版最终技术实现。 |
| [`mockups/specified-user-monitor-styles.html`](mockups/specified-user-monitor-styles.html) | 指定用户多平台监控三版静态样式稿；预览图见 [`mockups/specified-user-monitor-styles-preview.png`](mockups/specified-user-monitor-styles-preview.png)。 |
| [`mockups/bilibili-user-monitor-style-a-combined-chart.png`](mockups/bilibili-user-monitor-style-a-combined-chart.png) | B站指定用户监控工作台最终选定样式图，粉丝数和直播间热度合并为双指标趋势图。 |
| [`mockups/bilibili-user-monitor-workbench-styles.html`](mockups/bilibili-user-monitor-workbench-styles.html) | B站指定用户监控工作台三版静态样式稿，最终采用方案 A 合并图版本。 |
| [`bilibili-api-libraries-risk-control-research.md`](bilibili-api-libraries-risk-control-research.md) | `Nemo2011/bilibili-api` 与 `SeeleWaifu/bili_api` 的风控实现、请求难度和可借鉴边界。 |
| [`bilibili-risk-control-deep-dive.md`](bilibili-risk-control-deep-dive.md) | 两个第三方 SDK 的风控触发、处理链路、可采纳策略和不可接入边界。 |
| [`data-model.md`](data-model.md) | Flyway 迁移、B站监控表、历史快照、日志与本地数据目录。 |
| [`frontend-notes.md`](frontend-notes.md) | 前端技术栈、路由、B站监控页布局策略和图表组件说明。 |
| [`handoff.md`](handoff.md) | 给下次接手者的最短路径、不要做的事、验证清单和未确认项。 |
| [`archive/implemented-live-monitor/README.md`](archive/implemented-live-monitor/README.md) | 已落地的 B站直播监控方案稿和静态 mockup 归档。 |

## 当前主线

`social-data-monitor/` 是一个 Vue 3 + Spring Boot + PostgreSQL 的多社媒数据监控应用雏形。当前最完整、最可运行的功能有三条：B站用户粉丝数趋势监控、B站直播间监控、B站指定用户监控工作台。前者支持多 UID 粉丝趋势、头像资料、定时采集和趋势图；直播间监控支持按房间号或 UID 添加直播间、采集直播状态/在线热度/房间信息、房间观众和大航海榜单、趋势图、浅色/深色主题和直播间卡片交互；用户监控工作台把同一 B站 UID 的粉丝、直播热度、弹幕、房间观众和大航海榜单聚合到 `/subjects` 和 `/subjects/{subjectId}`。B站 Web 扫码登录获取登录态的首期代码已经接入 `/bilibili` 页面和后端 `/api/bilibili/auth/**`，并已通过真实扫码、凭据入库、解密读取和后端重启后 `nav` 校验。直播弹幕模块已复用该登录态优先获取发送者昵称，失败时回退游客态；工作台弹幕列表在鼠标不悬停时自动追最新，悬停弹幕监控区域时暂停自动下滑。

根目录另外保留了两个重要资料来源：

- [`../bilibili-api-collect-new-research/`](../bilibili-api-collect-new-research/)：B站接口文档本地副本，是接口选型和字段确认的来源。
- [`../external-research/`](../external-research/)：第三方 B站 API 项目的研究副本，仅作阅读分析，不是当前代码工程。
- [`../multi-social-platform-monitoring/`](../multi-social-platform-monitoring/)：早期架构设计与迭代说明，偏方案文档，不是当前运行入口。
