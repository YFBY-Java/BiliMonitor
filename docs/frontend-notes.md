# 前端页面与交互说明

最后更新：2026-06-19

## 技术栈

前端工程位于 [`../social-data-monitor/frontend/`](../social-data-monitor/frontend/)。

核心依赖：

- Vue 3
- Vite
- TypeScript
- Vue Router
- Pinia
- Element Plus
- ECharts
- Axios

脚本见 [`../social-data-monitor/frontend/package.json`](../social-data-monitor/frontend/package.json)。

## 路由

路由配置在 [`../social-data-monitor/frontend/src/router/index.ts`](../social-data-monitor/frontend/src/router/index.ts)。

当前路由：

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

`/bilibili` 是 B站粉丝趋势监控页，`/bilibili/live` 是 B站直播间监控页，`/subjects` 和 `/subjects/:subjectId` 是指定用户聚合监控入口。

## API 封装

通用 HTTP 封装：

- [`../social-data-monitor/frontend/src/api/http.ts`](../social-data-monitor/frontend/src/api/http.ts)

B站粉丝监控 API：

- [`../social-data-monitor/frontend/src/api/bilibili.ts`](../social-data-monitor/frontend/src/api/bilibili.ts)

B站直播监控 API：

- [`../social-data-monitor/frontend/src/api/bilibiliLive.ts`](../social-data-monitor/frontend/src/api/bilibiliLive.ts)

Subject 用户监控 API：

- [`../social-data-monitor/frontend/src/api/subjects.ts`](../social-data-monitor/frontend/src/api/subjects.ts)

后端响应统一为 `ApiResponse<T>`，前端 `getData`、`postData`、`patchData`、`deleteData` 会直接返回 `data` 字段。

## B站粉丝监控页

主文件：

- [`../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue`](../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue)

页面能力：

- 顶部输入 UID 添加监控。
- 添加时可设置采集间隔，最小 `1` 秒。
- 展示用户头像、昵称、UID、当前粉丝数、涨跌、最近更新时间、采集间隔、状态。
- 支持启用、停用、删除、立即采集。
- 支持修改单用户采集间隔。
- 短间隔会显示提示。
- 头像加载失败时显示昵称首字或 UID fallback。

页面布局策略：

- `0` 个用户：展示空状态和添加提示。
- `1` 个用户：`single-layout`，左侧突出头像和核心数据，右侧大趋势图。
- `2` 到 `4` 个用户：`user-grid`，响应式卡片网格，每张卡片带小趋势图。
- 超过 `4` 个用户：`dense-layout`，左侧可扫描列表，右侧展示选中用户趋势图。

响应式策略：

- 桌面端使用多列 grid。
- 小屏幕下单列堆叠。
- 间隔编辑器在移动端换行，避免控件挤压。
- 头像、按钮和图表都有固定或受控尺寸，减少布局跳动。

## B站直播监控页

主文件：

- [`../social-data-monitor/frontend/src/views/bilibili-live/BilibiliLiveView.vue`](../social-data-monitor/frontend/src/views/bilibili-live/BilibiliLiveView.vue)

页面能力：

- 支持按直播房间号或主播 UID 添加监控。
- 添加时可设置采集间隔，最小 `1` 秒。
- 展示直播间头像、主播昵称、房间号、标题、分区、直播状态、在线/热度、最近变化、采集进度、最后采集时间和下次采集时间。
- 支持启用、停用、删除、立即采集。
- 支持浅色/深色主题切换，主题状态保存在 `localStorage`。
- 直播间小卡片横向排列，数量多时横向滚动；点击卡片展开详情。
- 每张小卡片有独立“趋势”选择按钮，最多 4 个直播间进入趋势图，避免点击卡片时误选趋势。
- 详情区展示更完整的直播间资料和操作按钮。
- 趋势图支持选择、取消选择和拖动交换顺序。

当前设计重点：

- 只要求桌面和常见后台宽屏体验，最近主要检查 1440px、1366px、1280px、1024px。
- 左侧菜单固定，右侧内容独立滚动。
- 深色模式最近按用户反馈调浅，加入更柔和的粉色光感和半透明卡片感。
- 页面头部近期改为方角，避免渐变背景和圆角叠加产生尖角残影。
- 继续改样式时，总体结构不要大动，优先处理间距、文字层级、边框柔和度和局部组件状态。

## B站指定用户监控页

入口：

- `http://127.0.0.1:5173/subjects`
- `http://127.0.0.1:5173/subjects/{subjectId}`

主要文件：

- [`../social-data-monitor/frontend/src/views/subjects/SubjectListView.vue`](../social-data-monitor/frontend/src/views/subjects/SubjectListView.vue)
- [`../social-data-monitor/frontend/src/views/subjects/SubjectWorkbenchView.vue`](../social-data-monitor/frontend/src/views/subjects/SubjectWorkbenchView.vue)
- [`../social-data-monitor/frontend/src/views/subjects/components/SubjectHeader.vue`](../social-data-monitor/frontend/src/views/subjects/components/SubjectHeader.vue)
- [`../social-data-monitor/frontend/src/views/subjects/components/SubjectWidgetBoard.vue`](../social-data-monitor/frontend/src/views/subjects/components/SubjectWidgetBoard.vue)
- [`../social-data-monitor/frontend/src/views/subjects/components/MonitorWidgetShell.vue`](../social-data-monitor/frontend/src/views/subjects/components/MonitorWidgetShell.vue)
- [`../social-data-monitor/frontend/src/views/subjects/widgets/BilibiliFollowerLiveHeatWidget.vue`](../social-data-monitor/frontend/src/views/subjects/widgets/BilibiliFollowerLiveHeatWidget.vue)
- [`../social-data-monitor/frontend/src/views/subjects/widgets/BilibiliLiveDanmuWidget.vue`](../social-data-monitor/frontend/src/views/subjects/widgets/BilibiliLiveDanmuWidget.vue)
- [`../social-data-monitor/frontend/src/views/subjects/widgets/SubjectHealthEventWidget.vue`](../social-data-monitor/frontend/src/views/subjects/widgets/SubjectHealthEventWidget.vue)

页面能力：

- `/subjects` 输入 B站 UID 创建 Subject。
- `/subjects/:subjectId` 展示指定用户聚合工作台。
- 头像、昵称、UID、直播状态、模块数量、采集健康展示。
- KPI：总粉丝数、直播间热度、弹幕速率、最近成功采集。
- 左侧主 Widget：粉丝数与直播热度双指标趋势图。
- 右侧 Widget：直播间数据监控，支持在 `弹幕`、`房间观众`、`大航海` 三个视图间切换。
- `弹幕` 视图保留实时状态、弹幕速率、点赞增量、看过人数和最近弹幕列表。
- `房间观众` 视图展示直播榜单快照，当前支持在线榜、进房、日榜、周榜、月榜。
- `大航海` 视图展示大航海榜单快照，当前支持周榜、月榜、陪伴榜。
- 下方 Widget：采集健康与事件、待添加模块。
- 绑定资源弹窗可绑定已有粉丝监控、直播间监控，并启用/关闭弹幕模块。

当前样式和交互状态：

- `SubjectHeader.vue` 和 `SubjectListView.vue` 的 B站头像 `<img>` 需要保留 `referrerpolicy="no-referrer"`，否则 B站外链头像可能显示 fallback。
- `SubjectWidgetBoard.vue` 使用左右两列，左侧主图、右侧弹幕卡；1024px 左右会堆叠为单列。
- `MonitorWidgetShell.vue` 让 Widget 高度可被 grid 拉齐。
- `BilibiliFollowerLiveHeatWidget.vue` 当前图表高度为 `390px`，轴范围按当前数据动态计算，小幅变化会更明显。
- `BilibiliLiveDanmuWidget.vue` 是右侧复合卡片：默认弹幕视图每 2 秒轮询弹幕状态和最近弹幕；切到房间观众/大航海时调用 `frontend/src/api/bilibiliLive.ts` 中的 `fetchBilibiliLiveRankSummary` 和 `refreshBilibiliLiveRanks`。
- 弹幕列表在鼠标未悬停于“直播间弹幕监控”区域时自动跟随最新弹幕；鼠标悬停任意内容区域时暂停自动下滑，移出后恢复并滚到最新。
- 旧弹幕如果只保存脱敏昵称，前端显示“昵称待补全”；新弹幕由后端优先通过已保存登录态获取完整昵称，失败时回退游客态。
- 2026-06-19 使用系统 Chrome headless 检查过 `/subjects/7`，右侧三视图切换控件和默认弹幕视图没有空白或明显错位。

## 图表组件

文件：

- [`../social-data-monitor/frontend/src/components/charts/TrendChart.vue`](../social-data-monitor/frontend/src/components/charts/TrendChart.vue)

说明：

- 使用 ECharts `LineChart` 和 `CanvasRenderer`。
- 支持单条 `values` 或多条 `series`。
- `compact` 模式隐藏轴标签和部分装饰，适合卡片内小趋势图。
- 组件监听窗口 resize 并调用 `chart.resize()`。

如果后续要做多用户同图对比，可优先使用 `series` 参数，而不是在 B站页面里手写第二套 ECharts。

## 样式体系

全局样式：

- [`../social-data-monitor/frontend/src/styles.css`](../social-data-monitor/frontend/src/styles.css)

布局：

- [`../social-data-monitor/frontend/src/layouts/MainLayout.vue`](../social-data-monitor/frontend/src/layouts/MainLayout.vue)

当前风格偏管理后台：克制、信息密度适中、卡片半径较小、主要使用 Element Plus 基础控件。继续优化时应保持工具型页面的扫描效率，不要改成营销页或大幅装饰型首页。

## 下次前端修改建议

- 若只改 B站粉丝页文案、布局或交互，优先在 `BilibiliView.vue` 内小范围修改。
- 若只改 B站直播页文案、布局或交互，优先在 `BilibiliLiveView.vue` 内小范围修改。
- 若只改用户监控工作台，优先按 `views/subjects/` 的组件边界修改，不要回到单文件大页面。
- 若重复使用 `UserHeader`、`IntervalEditor`、`UserActions` 到其他页面，再拆成 `components/bilibili/`。
- 若图表显示异常，先检查传入 `labels` 和 `values` 是否长度一致，再看 `TrendChart.vue`。
- 若用户工作台双指标图显示异常，检查 `BilibiliFollowerLiveHeatWidget.vue` 的 `axisBounds`、`timeBounds` 和传入的 `SubjectTrend.points`。
- 若粉丝 API 数据字段变化，先改 `frontend/src/api/bilibili.ts` 的类型，再改页面消费逻辑。
- 若直播 API 数据字段变化，先改 `frontend/src/api/bilibiliLive.ts` 的类型，再改页面消费逻辑。
- 若 Subject API 数据字段变化，先改 `frontend/src/api/subjects.ts` 的类型，再改 `views/subjects/`。
- 若用户工作台右侧榜单视图没有数据，先确认当前 Subject 是否绑定了 `bilibili_live_room_monitor_id`，再检查 `fetchBilibiliLiveRankSummary(roomMonitorId)` 的返回和 `/api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/summary`。
- 若要验证响应式，使用浏览器打开 `http://127.0.0.1:5173/bilibili`、`http://127.0.0.1:5173/bilibili/live` 或 `http://127.0.0.1:5173/subjects/{subjectId}` 后切换视口。
