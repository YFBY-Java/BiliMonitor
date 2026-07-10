# 已归档：B站直播间监控设计稿

归档时间：2026-06-12

本目录保存已经落地到 `social-data-monitor/` 的 B站直播间监控早期设计资料。它们不再作为当前开发入口，保留用途是回看方案取舍、页面草图和接口判断。

## 归档文件

| 文件 | 说明 |
| --- | --- |
| [`bilibili-live-monitor-page-design.md`](bilibili-live-monitor-page-design.md) | 直播间监控页三版方案，当前已按方案一落地并继续迭代。 |
| [`bilibili-live-monitor-scheme1-detail.md`](bilibili-live-monitor-scheme1-detail.md) | 方案一详细设计，实际代码已在此基础上调整。 |
| [`bilibili-live-monitor-style.html`](bilibili-live-monitor-style.html) | 早期静态样式稿。 |
| [`bilibili-live-monitor-style-preview.png`](bilibili-live-monitor-style-preview.png) | 桌面预览图。 |
| [`bilibili-live-monitor-style-mobile-preview.png`](bilibili-live-monitor-style-mobile-preview.png) | 移动端预览图。 |

## 当前实现入口

- 当前运行与启动：[`../../runbook.md`](../../runbook.md)
- 当前功能状态：[`../../feature-status.md`](../../feature-status.md)
- 当前架构说明：[`../../architecture.md`](../../architecture.md)
- 当前前端实现：[`../../../social-data-monitor/frontend/src/views/bilibili-live/BilibiliLiveView.vue`](../../../social-data-monitor/frontend/src/views/bilibili-live/BilibiliLiveView.vue)
- 当前后端模块：[`../../../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/`](../../../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/live/)

如果后续继续改直播间监控，请优先阅读当前实现和根目录文档；这里的方案稿只作为历史参考。
