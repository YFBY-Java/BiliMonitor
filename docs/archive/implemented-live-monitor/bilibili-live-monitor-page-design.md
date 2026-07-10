# B站直播间监控页三版设计方案

最后更新：2026-06-11  
关联资料：

- 当前工程：[`../../../social-data-monitor/`](../../../social-data-monitor/)
- 直播间接口研究：[`../../bilibili-live-room-api-research.md`](../../bilibili-live-room-api-research.md)
- 当前前端风格说明：[`../../frontend-notes.md`](../../frontend-notes.md)
- 早期方案文档：[`../../../multi-social-platform-monitoring/`](../../../multi-social-platform-monitoring/)

## 设计边界

本轮只考虑无登录、无 WBI、无验证码、无状态变更的接口。第一阶段不接：

- `getDanmuInfo`、WebSocket 实时弹幕：需要 WBI，留到后续。
- 关注列表、我的勋章、直播管理、禁言、发送弹幕、送礼：需要账号态或会改变状态。
- 人气红包、盲盒概率：匿名实测存在 `-352` 或 `-101`，暂不依赖。

可用接口只取低风控匿名查询：

| 用途 | 接口 | 页面使用 |
| --- | --- | --- |
| 批量按 UID 查直播状态 | `GET /room/v1/Room/get_status_info_by_uids` | 主轮询入口 |
| 房间号规范化 | `GET /room/v1/Room/room_init` | 添加房间号时解析真实房间号和 UID |
| 单房间详情 | `GET /room/v1/Room/get_info` | 添加、详情刷新 |
| 批量房间详情 | `GET /xlive/web-room/v1/index/getRoomBaseInfo` | 列表补全标题、封面、分区、在线数 |
| 单 UID 兜底 | `GET /room/v1/Room/getRoomInfoOld` | 单目标异常兜底 |
| 主播资料 | `GET /live_user/v1/Master/info`、`GET /live_user/v1/UserInfo/get_anchor_in_room` | 低频补头像、认证、粉丝数 |
| 分区字典 | `GET /room/v1/Area/getList` | 分区筛选和标签 |
| 推荐发现 | `GET /xlive/web-interface/v1/webMain/getMoreRecList` | 仅方案三使用 |

## 当前风格约束

沿用 [`../../../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue`](../../../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue) 的风格：

- 管理后台工具页，不做营销式首屏。
- 左侧固定导航，主内容区浅灰背景。
- 顶部 `page-header` + 控制面板 `control-surface`。
- 8px 左右小圆角卡片，信息密度中高。
- Element Plus 控件、图标按钮、标签、开关、输入框。
- 摘要指标用 4 列 `summary-grid`。
- 卡片可横向滚动或表格化扫描，重要详情在右侧或下方展开。
- 保留浅色优先，可继续复用当前 B站页的深色主题变量。

## 共同技术底座

无论选哪版，后端建议新增独立直播间模块，不污染现有粉丝监控：

```text
backend/src/main/java/com/socialmonitor/bilibili/live/
  client/BilibiliLiveApiClient.java
  config/BilibiliLiveMonitorProperties.java
  controller/BilibiliLiveMonitorController.java
  domain/BilibiliLiveRoomMonitor.java
  domain/BilibiliLiveRoomSnapshot.java
  domain/BilibiliLiveStatusEvent.java
  dto/*
  repository/BilibiliLiveMonitorRepository.java
  service/BilibiliLiveMonitorService.java
  service/BilibiliLiveMonitorScheduler.java
```

前端建议新增：

```text
frontend/src/api/bilibiliLive.ts
frontend/src/views/bilibili-live/BilibiliLiveView.vue
frontend/src/components/charts/TrendChart.vue  // 复用
```

路由：

```text
/bilibili/live
```

侧边栏可将当前 `Bilibili` 保留为一级入口，第一阶段也可以在现有 `/bilibili` 页内加 `el-tabs`：

- `粉丝趋势`
- `直播间监控`

如果不想改导航结构，推荐先用 `el-tabs`，后续功能稳定后再拆独立路由。

### 建议数据表

```sql
CREATE TABLE bilibili_live_room_monitor (
    id BIGSERIAL PRIMARY KEY,
    uid BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    short_id BIGINT,
    uname VARCHAR(160),
    face_url TEXT,
    title TEXT,
    cover_url TEXT,
    keyframe_url TEXT,
    area_id BIGINT,
    area_name VARCHAR(120),
    parent_area_id BIGINT,
    parent_area_name VARCHAR(120),
    live_status SMALLINT,
    live_time TIMESTAMPTZ,
    online_count BIGINT,
    attention_count BIGINT,
    monitor_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    interval_seconds INTEGER NOT NULL DEFAULT 300,
    next_collect_at TIMESTAMPTZ,
    last_snapshot_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_error_at TIMESTAMPTZ,
    last_error_type VARCHAR(80),
    last_error_message TEXT,
    source_endpoint VARCHAR(160),
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (uid),
    UNIQUE (room_id),
    CHECK (uid > 0),
    CHECK (room_id > 0),
    CHECK (interval_seconds BETWEEN 30 AND 2592000)
);

CREATE INDEX idx_bilibili_live_room_due
    ON bilibili_live_room_monitor (monitor_status, next_collect_at);

CREATE TABLE bilibili_live_room_snapshot (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    uid BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    live_status SMALLINT NOT NULL,
    online_count BIGINT,
    attention_count BIGINT,
    title TEXT,
    area_id BIGINT,
    area_name VARCHAR(120),
    parent_area_id BIGINT,
    parent_area_name VARCHAR(120),
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    captured_bucket TIMESTAMPTZ NOT NULL,
    source_endpoint VARCHAR(160) NOT NULL,
    raw_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (monitor_id, captured_bucket)
);

CREATE TABLE bilibili_live_status_event (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    uid BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    previous_status SMALLINT,
    current_status SMALLINT,
    title TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    snapshot_id BIGINT REFERENCES bilibili_live_room_snapshot(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

说明：

- `bilibili_live_status_event` 是后端从快照差异派生出来的事件，不依赖 WebSocket。
- 第一阶段 `interval_seconds` 建议最小 30 秒或 60 秒，不沿用粉丝页的 1 秒下限。
- 原始 payload 只存低风控接口响应，不存账号 Cookie 或用户隐私凭证。

### 建议 REST API

```text
GET    /api/bilibili/live-monitor/rooms
POST   /api/bilibili/live-monitor/rooms
PATCH  /api/bilibili/live-monitor/rooms/{roomMonitorId}/status
PATCH  /api/bilibili/live-monitor/rooms/{roomMonitorId}/settings
POST   /api/bilibili/live-monitor/rooms/{roomMonitorId}/refresh
DELETE /api/bilibili/live-monitor/rooms/{roomMonitorId}
GET    /api/bilibili/live-monitor/rooms/{roomMonitorId}/history
GET    /api/bilibili/live-monitor/events
GET    /api/bilibili/live-monitor/summary
GET    /api/bilibili/live-monitor/areas
```

添加监控请求：

```json
{
  "uid": 401742377,
  "roomId": null,
  "intervalSeconds": 300
}
```

后端规则：

- `uid` 和 `roomId` 二选一。
- 有 `roomId`：先 `room_init` 得到真实 `room_id` 和 `uid`，再 `get_info` 补详情。
- 有 `uid`：先批量状态接口查单 UID，若没有房间再用 `getRoomInfoOld` 兜底。
- 周期采集：优先按 UID 批量调用 `get_status_info_by_uids`，再对变化目标低频补 `getRoomBaseInfo`。

## 方案一：状态卡片监控台

适合第一版 MVP。核心目标是“快速知道哪些主播正在直播、状态变化、在线人数趋势”。

### 技术设计

接口组合：

| 场景 | 调用 |
| --- | --- |
| 添加房间号 | `room_init` -> `get_info` |
| 添加 UID | `get_status_info_by_uids` 单 UID -> `getRoomInfoOld` 兜底 |
| 周期采集 | `get_status_info_by_uids` 批量 |
| 详情补全 | `getRoomBaseInfo` 批量，添加时和每日低频刷新 |
| 分区字典 | `Area/getList`，启动或每天刷新 |

后端策略：

- 新增 `BilibiliLiveMonitorScheduler`，默认 60 秒扫描到期房间。
- 每次按 UID 批量请求，批大小 20 到 50，失败后按当前粉丝监控的退避策略记录错误。
- 快照记录 `live_status`、`online_count`、`title`、`area`、`captured_at`。
- 当 `live_status` 从 `0/2 -> 1` 生成 `LIVE_STARTED`，从 `1 -> 0/2` 生成 `LIVE_ENDED`。

前端数据：

```ts
interface BilibiliLiveRoomMonitor {
  id: number
  uid: number
  roomId: number
  shortId?: number
  uname: string
  faceUrl?: string
  title?: string
  coverUrl?: string
  keyframeUrl?: string
  areaName?: string
  parentAreaName?: string
  liveStatus: 0 | 1 | 2
  liveTime?: string
  onlineCount?: number
  attentionCount?: number
  monitorStatus: 'ACTIVE' | 'PAUSED'
  intervalSeconds: number
  nextCollectAt?: string
  lastSnapshotAt?: string
  lastErrorType?: string
  lastErrorMessage?: string
  recentTrend: BilibiliLivePoint[]
}
```

### 页面方案

布局沿用当前粉丝页：

```text
页面标题：B站直播间监控
副标题：匿名公开接口采集直播状态、在线人数和房间信息

[添加监控控制台]
  输入：UID / 房间号 segmented control
  输入框：例如 401742377 或 7734200
  间隔：300 秒
  按钮：添加监控、刷新
  右侧 meta：12 个房间 / 4 个直播中 / 1 个异常 / 6 个今日开播

[摘要 4 格]
  直播中房间 | 总在线/热度 | 今日开播次数 | 最近状态变化

[直播间横向卡片 strip]
  头像 + 主播名 + 房间号
  LIVE/未开播/轮播 标签
  标题、分区
  在线数、开播时长、下次采集进度条
  操作：打开直播间、趋势、暂停、刷新

[展开详情]
  封面/关键帧缩略图
  标题、分区、主播 UID、真实房间号、开播时间
  在线数、关注数、最近状态变化、最后采集

[趋势区]
  选中最多 4 个直播间
  折线：在线数/热度
  状态带：直播中时间段用浅色背景
```

优点：

- 和当前粉丝页结构最接近，开发成本最低。
- 很适合 1 到 30 个房间。
- 不引入新的复杂交互模型。

缺点：

- 房间超过几十个时横向卡片会变长。
- 告警和事件流不是主视觉。

推荐程度：第一阶段首选。

## 方案二：运行态调度看板

适合要监控较多房间、关注异常和状态变化的版本。核心目标是“像运维台一样扫状态”。

### 技术设计

接口组合和方案一相同，但后端更强调事件与聚合：

| 能力 | 设计 |
| --- | --- |
| 状态聚合 | `/summary` 返回直播中、未开播、轮播、异常、到期任务数 |
| 事件流 | 后端从快照差异生成 `LIVE_STARTED/LIVE_ENDED/TITLE_CHANGED/AREA_CHANGED/ERROR_RECOVERED` |
| 列表查询 | 支持 `status`、`areaId`、`keyword`、`sort=onlineCount,lastSnapshotAt,liveTime` |
| 批量操作 | 暂停、恢复、批量刷新、批量改间隔 |
| 趋势查询 | 单房间详情侧边栏再请求历史 |

新增 API：

```text
GET /api/bilibili/live-monitor/rooms?status=LIVE&areaId=...
GET /api/bilibili/live-monitor/events?limit=100
PATCH /api/bilibili/live-monitor/rooms/batch/settings
POST /api/bilibili/live-monitor/rooms/batch/refresh
```

### 页面方案

页面结构更像监控运维面板：

```text
[顶部控制台]
  添加 UID/房间号
  状态筛选 segmented：全部 / 直播中 / 未开播 / 轮播 / 异常
  分区下拉、关键词、刷新

[摘要 5 格]
  直播中 | 未开播 | 异常 | 平均在线 | 最近开播

[主体两栏]
  左 65%：房间表格
    主播/房间 | 状态 | 标题 | 分区 | 在线 | 开播时长 | 下次采集 | 操作
    行内使用头像、状态 tag、在线数变化小标记

  右 35%：详情侧栏
    当前选中房间封面
    核心指标
    近 24h 在线趋势
    最近事件 timeline

[底部或右侧事件流]
  01:32  原神 开播
  01:20  央视新闻 标题更新
  01:05  某房间采集失败，15 分钟后重试
```

视觉细节：

- 表格区域使用当前项目的白底卡片和浅灰分割线。
- 状态 tag：直播中绿色，轮播蓝色，未开播灰色，异常红色。
- 右侧详情不做大卡片套卡片，使用一个白色面板内分区。
- 批量操作按钮使用图标：刷新、暂停、播放、设置。

优点：

- 大量房间时更可扫描。
- 事件流清楚，适合“监控页”语义。
- 后续可以扩展告警规则。

缺点：

- 比方案一更重，需要分页、筛选、批量操作。
- 单房间视觉吸引力弱，偏运维。

推荐程度：如果目标是监控 30 个以上直播间，选这一版。

## 方案三：发现与监控一体页

适合想从 B站公开推荐里发现直播间，再加入监控。核心目标是“发现样本、挑选、纳入监控”。

### 技术设计

接口组合：

| 场景 | 调用 |
| --- | --- |
| 推荐发现 | `getMoreRecList?platform=web&page=1` |
| 分区筛选 | `Area/getList` 缓存分区字典 |
| 添加推荐房间 | 推荐结果中的 `roomid/uid` -> `room_init/get_info` 确认 |
| 已监控列表 | 同方案一 |
| 状态采集 | `get_status_info_by_uids` |

新增 API：

```text
GET /api/bilibili/live-monitor/discovery/recommendations?page=1
GET /api/bilibili/live-monitor/areas
POST /api/bilibili/live-monitor/rooms/from-discovery
```

后端注意：

- 推荐接口只作为发现入口，不作为全站扫描。
- 推荐结果不长期入库，除非用户点击“加入监控”。
- 对推荐接口设置更低频缓存，例如 5 到 10 分钟。

### 页面方案

使用页内 tabs：

```text
[顶部]
  B站直播间监控
  Tabs：我的监控 / 直播发现 / 分区字典

[我的监控]
  使用方案一的卡片监控台，但更紧凑

[直播发现]
  左侧筛选：分区、关键词、在线人数区间
  中间推荐房间网格：
    封面/关键帧
    主播名、标题、分区、在线数
    按钮：加入监控、打开直播间
  右侧候选篮：
    已选择 5 个房间
    批量设置间隔
    批量加入

[分区字典]
  父分区横向 tabs
  子分区小表格：名称、热度状态、已监控数量
```

视觉细节：

- 推荐卡片可以有封面图，但保持 8px 圆角和紧凑信息层级。
- 不做大面积 hero 图。
- 候选篮是工具面板，不是卡片套卡片。
- 分区字典用表格或紧凑标签，不做装饰性瀑布流。

优点：

- 能解决“我不知道该监控谁”的问题。
- 用公开低风控推荐接口即可实现。
- 对演示和运营选样友好。

缺点：

- 产品边界从“监控”扩展到“发现”，功能更散。
- 推荐接口不是稳定全量发现来源，不能承诺覆盖。
- 比前两版更容易引入用户级展示数据，需要存储最小化。

推荐程度：作为第二阶段功能，不建议第一版就做。

## 三版对比

| 维度 | 方案一：状态卡片监控台 | 方案二：运行态调度看板 | 方案三：发现与监控一体页 |
| --- | --- | --- | --- |
| 首要目标 | 快速实现直播状态监控 | 大量房间运维扫描 | 从推荐/分区发现房间 |
| 页面形态 | 当前粉丝页同构 | 表格 + 详情侧栏 + 事件流 | Tabs + 推荐网格 + 监控台 |
| 后端复杂度 | 中低 | 中高 | 中 |
| 前端复杂度 | 中低 | 中高 | 高 |
| 可用接口风险 | 最低 | 最低 | 低，但推荐接口稳定性一般 |
| 适合数量 | 1 到 30 个房间 | 30 个以上房间 | 发现候选，不限 |
| 首版推荐 | 强推荐 | 次推荐 | 不建议首版 |

## 推荐落地顺序

推荐先做“方案一 + 方案二的事件模型”：

1. 数据模型直接包含 `snapshot` 和 `status_event`，避免后续迁移重做。
2. 页面第一版按方案一做，保持和当前 B站粉丝页一致。
3. 列表超过 30 个房间后，再把主体区域切到方案二的表格/侧栏模式。
4. 方案三的发现功能等监控闭环稳定后再做。

## 第一版验收清单

功能：

- 可用 UID 或房间号添加监控。
- 添加时自动解析真实房间号、主播 UID、标题、封面、分区。
- 可批量轮询直播状态。
- 可展示直播中、未开播、轮播、异常。
- 可保存状态快照和状态变化事件。
- 可查看单房间在线数趋势。
- 可暂停、恢复、删除、立即刷新。

风控边界：

- 不需要 Cookie。
- 不实现 WBI。
- 不实现 WebSocket。
- 不请求发送弹幕、送礼、直播管理等状态变更接口。
- 命中 `-352`、`-101` 时记录错误并停止重试。

视觉：

- 页面整体与当前 `/bilibili` 粉丝页同一套密度、圆角、按钮、摘要卡片、图表风格。
- 桌面端信息密度优先，移动端单列堆叠。
- 文案不解释产品功能，不做教程式提示，只展示必要状态和操作。
