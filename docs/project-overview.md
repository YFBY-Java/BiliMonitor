# 项目总览

最后更新：2026-06-13

## 仓库结构

```text
.
├── bilibili-api-collect-new-research/
├── docs/
├── multi-social-platform-monitoring/
└── social-data-monitor/
```

## 子工程职责

### `social-data-monitor/`

当前主要应用工程，包含后端、前端、数据库迁移、开发脚本，以及 B站粉丝数、直播间、直播弹幕和指定用户聚合工作台相关实现。

关键目录：

- [`../social-data-monitor/backend/`](../social-data-monitor/backend/)：Spring Boot 后端。
- [`../social-data-monitor/frontend/`](../social-data-monitor/frontend/)：Vue 3 + Vite 前端。
- [`../social-data-monitor/scripts/`](../social-data-monitor/scripts/)：Windows 本地开发脚本。
- [`../social-data-monitor/docs/`](../social-data-monitor/docs/)：应用内文档。
- [`../social-data-monitor/backend/src/main/resources/db/migration/`](../social-data-monitor/backend/src/main/resources/db/migration/)：Flyway 数据库迁移。

当前核心功能：

- B站用户粉丝数采集。
- 多用户粉丝趋势监控。
- 用户昵称、头像、空间链接保存与展示。
- 当前粉丝数、涨跌趋势、最近更新时间展示。
- 单用户与多用户趋势图。
- 单用户采集间隔配置，范围 `1` 秒到 `2592000` 秒。
- 启用、停用、删除、立即采集。
- B站直播间监控，支持按房间号或 UID 添加。
- 直播状态、在线/热度、房间标题、封面、分区、开播时间采集。
- 直播间历史快照、状态事件和趋势图。
- 直播页浅色/深色主题、横向卡片、详情展开和多直播间趋势选择。
- B站直播弹幕监控，支持手动开启未开播直播间监控。
- B站 WebSocket 弹幕协议兼容 `protover=0/1/2/3`，包含普通包、zlib 包和 brotli 包解析。
- 指定用户监控工作台，入口为 `/subjects` 和 `/subjects/{subjectId}`。
- 用户监控输入 UID 后自动创建或绑定粉丝监控与直播间监控。
- 用户工作台展示头像、采集健康、总粉丝数、直播间热度、弹幕速率、最近成功采集。
- 用户工作台主图为粉丝数与直播热度双指标趋势图，右侧为实时弹幕监控卡片。

### `bilibili-api-collect-new-research/`

B站接口文档本地副本。当前项目主要参考其中这些文档：

- [`../bilibili-api-collect-new-research/repo/docs/user/info.md`](../bilibili-api-collect-new-research/repo/docs/user/info.md)：用户信息、卡片和空间资料相关接口。
- [`../bilibili-api-collect-new-research/repo/docs/user/status_number.md`](../bilibili-api-collect-new-research/repo/docs/user/status_number.md)：用户关注数、粉丝数等状态数。
- [`../bilibili-api-collect-new-research/repo/docs/user/relation.md`](../bilibili-api-collect-new-research/repo/docs/user/relation.md)：关系链、粉丝列表、关注列表等接口。
- [`../bilibili-api-collect-new-research/repo/docs/user/space.md`](../bilibili-api-collect-new-research/repo/docs/user/space.md)：用户空间相关接口。
- [`../bilibili-api-collect-new-research/repo/docs/dynamic/space.md`](../bilibili-api-collect-new-research/repo/docs/dynamic/space.md)：空间动态流。
- [`../bilibili-api-collect-new-research/repo/docs/dynamic/basicInfo.md`](../bilibili-api-collect-new-research/repo/docs/dynamic/basicInfo.md)：动态基础资料相关内容。
- 直播间接口来源还包括 `bilibili-api-collect-new-research/repo/docs/live/` 相关文档，以及第三方 SDK 研究副本，详见 [`bilibili-live-room-api-research.md`](bilibili-live-room-api-research.md)。
- 弹幕 WebSocket 研究详见 [`bilibili-live-danmaku-research.md`](bilibili-live-danmaku-research.md)。

不要在代码中实现验证码绕过、复杂风控绕过、登录态抓取或平台安全机制规避。当前项目只采用公开、低频、无须登录或低风控的接口，并对短间隔采集保留提示。

### `docs/`

当前仓库的接手文档目录。优先从这里了解项目状态、运行方式和下一步开发建议：

- [`README.md`](README.md)：文档索引。
- [`runbook.md`](runbook.md)：启动、停止、验证和排障。
- [`feature-status.md`](feature-status.md)：当前功能完成度和风险。
- [`architecture.md`](architecture.md)：架构与模块关系。
- [`data-model.md`](data-model.md)：数据库表与迁移说明。
- [`frontend-notes.md`](frontend-notes.md)：前端页面和交互说明。
- [`handoff.md`](handoff.md)：下次接手提示。

### `multi-social-platform-monitoring/`

早期规划文档工程，不是当前启动入口。它记录了多平台监控、采集适配器、原始载荷、归一化、AI 分析、身份映射等设计方向。

可参考：

- [`../multi-social-platform-monitoring/README.md`](../multi-social-platform-monitoring/README.md)
- [`../multi-social-platform-monitoring/docs/technical-framework.md`](../multi-social-platform-monitoring/docs/technical-framework.md)
- [`../multi-social-platform-monitoring/docs/iteration-summary.md`](../multi-social-platform-monitoring/docs/iteration-summary.md)

## 运行态目录

以下目录或文件是本机开发运行产生的状态，不应被当作业务源码：

- `social-data-monitor/.dev-tools/`：本机便携 PostgreSQL 工具目录，当前机器可能存在。
- `social-data-monitor/.dev-data/`：本机 PostgreSQL 数据和日志目录，当前机器可能存在。
- `social-data-monitor/backend/backend-dev.log`
- `social-data-monitor/backend/backend-dev.err.log`
- `social-data-monitor/frontend/frontend-dev.log`
- `social-data-monitor/frontend/frontend-dev.err.log`
- `social-data-monitor/frontend/dist/`

如果换机器运行，优先按 [`runbook.md`](runbook.md) 准备和启动环境，而不是假设这些本机运行态目录一定存在。

## 当前状态一句话

`social-data-monitor/` 已经有可运行的 B站粉丝趋势监控、直播间监控、直播弹幕监控和指定用户聚合工作台。下次工作应在现有 Spring Boot 服务、Flyway 表结构、Vue 页面和 API 封装上继续小步迭代，不要推翻重写。
