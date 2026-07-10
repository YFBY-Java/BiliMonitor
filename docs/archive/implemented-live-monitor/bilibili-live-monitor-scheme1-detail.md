# B站直播间监控页方案一详细设计

最后更新：2026-06-11  
方案定位：`方案一：状态卡片监控台`，作为第一版 MVP 落地方案。  
关联资料：

- 三版总方案：[`bilibili-live-monitor-page-design.md`](bilibili-live-monitor-page-design.md)
- 静态样式稿：[`bilibili-live-monitor-style.html`](bilibili-live-monitor-style.html)
- 接口研究：[`../../bilibili-live-room-api-research.md`](../../bilibili-live-room-api-research.md)
- 当前前端风格：[`../../../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue`](../../../social-data-monitor/frontend/src/views/bilibili/BilibiliView.vue)

## 1. 设计目标

方案一解决的是“我已经知道一批主播或直播间，想低成本监控它们是否开播、在线/热度变化和最近状态变化”。

第一版只做低风控匿名公开接口，不做登录态和中高风险能力：

- 不需要 Cookie。
- 不实现 WBI 签名。
- 不接 WebSocket 弹幕长连接。
- 不请求发送弹幕、送礼、直播管理、禁言、关注列表等状态变更或账号态接口。
- 不依赖匿名实测不稳定的 `-352`、`-101` 接口。

核心成功标准：

- 用户能用 UID 或房间号添加监控。
- 系统能周期批量判断直播状态。
- 页面能一眼看出谁在直播、在线/热度多少、下次什么时候采集、最近发生了什么状态变化。
- 能查看单房间详情和近 24 小时趋势。
- 风控失败时能降频、退避、记录错误，不继续撞接口。

## 2. 页面信息架构

首版建议复用当前 `/bilibili` 粉丝页的信息结构，不新建复杂二级导航。推荐路线：

```text
/bilibili
  el-tabs
    粉丝趋势
    直播间监控
```

如果产品上希望直播间单独成为入口，也可以新增：

```text
/bilibili/live
```

页面层级：

```text
B站直播间监控
  顶部标题区
  添加/刷新控制台
  风控/短间隔提示
  摘要指标 4 格
  监控直播间横向卡片
  选中直播间详情
  近 24 小时趋势
```

静态样式稿里方案一已经画好桌面和移动端预览：

- [`bilibili-live-monitor-style-preview.png`](bilibili-live-monitor-style-preview.png)
- [`bilibili-live-monitor-style-mobile-preview.png`](bilibili-live-monitor-style-mobile-preview.png)

## 3. 页面详细设计

### 3.1 顶部标题区

位置：页面最上方，沿用当前 `page-header monitor-header`。

内容：

| 元素 | 文案/行为 |
| --- | --- |
| 标题 | `B站直播间监控` |
| 副标题 | `匿名公开接口采集直播状态、在线人数和房间信息。` |
| 主题切换 | 复用当前粉丝页浅色/深色切换，第一版可先保留浅色优先 |
| 刷新按钮 | 重新拉取本地监控列表和摘要，不强制立即请求 B站 |

视觉：

- 标题字号保持当前 `page-title`，约 22px。
- 副标题保持 14px、灰色。
- 不做 hero，不做营销式介绍。

### 3.2 添加/刷新控制台

位置：标题下方，白底 `control-surface`。

控件：

| 控件 | 类型 | 说明 |
| --- | --- | --- |
| 添加类型 | segmented control | `房间号` / `UID`，默认房间号 |
| 输入框 | `el-input` | 房间号示例 `7734200`，UID 示例 `401742377` |
| 采集间隔 | `el-input-number` | 默认 300 秒，首版建议最小 60 秒，开发调试可配置为 1 秒 |
| 添加监控 | primary button + Plus 图标 | 调用添加接口 |
| 刷新 | button + Refresh 图标 | 拉取最新本地状态 |
| 右侧 meta | 文本组 | `12 个房间 / 4 个直播中 / 1 个异常 / 6 个今日开播` |

输入规则：

- 只允许正整数。
- 选择 `房间号` 时提交 `roomId`。
- 选择 `UID` 时提交 `uid`。
- `roomId` 与 `uid` 二选一，不能同时提交。
- 添加前端先做空值和数字校验，后端仍需重复校验。

添加中状态：

- 按钮 loading。
- 禁用添加类型、输入框、间隔。
- 成功后清空输入，新增卡片插到列表前面。
- 失败用 `ElMessage.error`，错误详情留在卡片或日志里。

短间隔提示：

- 当存在 `intervalSeconds < shortIntervalWarningSeconds` 的监控项时，在控制台下方显示 warning alert。
- 文案可复用粉丝页风格：

```text
已允许短间隔采集；短间隔会受全局请求节流和失败退避保护，建议只用于少量直播间的临时观察。
```

### 3.3 摘要指标

四格 `summary-grid`：

| 指标 | 计算方式 | 点击行为 |
| --- | --- | --- |
| 直播中房间 | `liveStatus == 1` 的监控数 | 过滤卡片到直播中 |
| 总在线/热度 | 直播中房间 `onlineCount` 求和 | 无，首版只展示 |
| 今日开播次数 | 当天 `LIVE_STARTED` 事件数 | 展开今日事件摘要 |
| 最近状态变化 | 最新 `status_event` 的时间差 | 选中对应房间 |

状态展示：

- 数字为空时显示 `-`。
- 总在线/热度用短数字：`128.4万`、`3.2万`。
- 最近变化超过 24 小时显示 `24 小时内无变化`。

### 3.4 监控直播间横向卡片

位置：摘要下方，白底 panel 内横向滚动 strip。

卡片宽度：

- 桌面：`minmax(228px, 1fr)`，横向滚动。
- 移动端：单列堆叠。

卡片内容：

| 区域 | 内容 |
| --- | --- |
| 顶部身份 | 头像、主播名、UID、房间号 |
| 状态 Tag | `LIVE`、`未开播`、`轮播`、`异常`、`暂停` |
| 房间标题 | 最多 2 行，超出省略 |
| 分区 | 父分区 / 子分区，可放在身份副行或标题下 |
| 指标 | 在线/热度、开播时长或上次开播 |
| 采集节奏 | 下次采集进度条 |
| 操作 | 打开直播间、趋势、暂停/恢复、立即刷新 |

状态颜色：

| 状态 | 颜色 |
| --- | --- |
| 直播中 | 绿色 |
| 轮播中 | 蓝色 |
| 未开播 | 灰色 |
| 异常 | 红色 |
| 暂停 | 灰色弱化，卡片透明度轻微降低 |

卡片交互：

- 点击卡片：展开/切换下方详情。
- `趋势`：加入趋势图，最多 4 个。
- `打开直播间`：新窗口打开 `https://live.bilibili.com/{roomId}`。
- `暂停/恢复`：更新 `monitorStatus`。
- `立即刷新`：只刷新当前房间，受全局请求节流限制。

卡片选择规则：

- 默认选中第一个直播中房间；若没有直播中，选第一个有最近快照的房间。
- 趋势图最多 4 个房间，超过后禁用未选卡片的趋势按钮。
- 异常卡片可以选中查看错误，但不默认加入趋势。

### 3.5 选中直播间详情

位置：卡片区下方，左侧详情、右侧趋势。桌面两栏，移动端堆叠。

详情内容：

| 区域 | 字段 |
| --- | --- |
| 视觉预览 | `keyframeUrl` 优先，其次 `coverUrl`，最后占位底纹 |
| 基础信息 | 主播名、标题、父分区、子分区 |
| 标识 | UID、真实房间号、短号 |
| 状态 | 直播状态、开播时间、开播时长 |
| 数据 | 在线/热度、关注数 |
| 采集 | 最后成功、最后快照、下次采集、来源接口 |
| 错误 | 最近错误码、错误信息、退避到期时间 |

详情行为：

- 切换卡片时详情平滑更新，不新开页面。
- 详情中的“打开直播间”保留。
- 异常时在详情顶部显示 `el-alert`，说明是否已退避。

### 3.6 近 24 小时趋势

复用当前 `TrendChart.vue` 的基础能力，首版只展示在线/热度。

趋势卡片：

- 标题：`近 24 小时在线趋势`
- 图例：最多 4 个选中房间。
- 折线值：`onlineCount`。
- 横轴：采集时间。
- 直播状态带：可选增强，用浅绿色背景标注直播中时间段。

趋势空态：

- 没有选中房间：`选择直播间后查看在线趋势`。
- 只有一个点：显示点和提示 `至少需要 2 次采集形成趋势`。

刷新策略：

- 页面加载后请求选中房间趋势。
- 手动刷新房间成功后，刷新对应趋势。
- 选中房间变化时，防抖请求趋势。

## 4. 前端技术设计

### 4.1 文件组织

首版建议先新增独立视图和 API 文件：

```text
frontend/src/api/bilibiliLive.ts
frontend/src/views/bilibili-live/BilibiliLiveView.vue
```

如果先做在现有 `/bilibili` 页内 tab，则仍建议把直播间逻辑单独放在 `BilibiliLiveView.vue`，由 `BilibiliView.vue` 引用，避免一个文件继续膨胀。

可选拆分组件：

```text
frontend/src/views/bilibili-live/components/LiveRoomTile.vue
frontend/src/views/bilibili-live/components/LiveRoomDetail.vue
frontend/src/views/bilibili-live/components/LiveSummaryGrid.vue
```

第一版为了快，可以先单文件实现，稳定后再拆组件。

### 4.2 前端类型

```ts
export type LiveStatus = 0 | 1 | 2
export type LiveMonitorStatus = 'ACTIVE' | 'PAUSED'

export interface BilibiliLiveRoomMonitor {
  id: number
  uid: number
  roomId: number
  shortId?: number
  uname: string
  faceUrl?: string
  title?: string
  coverUrl?: string
  keyframeUrl?: string
  areaId?: number
  areaName?: string
  parentAreaId?: number
  parentAreaName?: string
  liveStatus: LiveStatus
  liveTime?: string
  onlineCount?: number
  attentionCount?: number
  monitorStatus: LiveMonitorStatus
  intervalSeconds: number
  nextCollectAt?: string
  lastSnapshotAt?: string
  lastSuccessAt?: string
  lastErrorAt?: string
  lastErrorType?: string
  lastErrorMessage?: string
  sourceEndpoint?: string
}

export interface BilibiliLiveSummary {
  totalRooms: number
  activeRooms: number
  liveRooms: number
  roundRooms: number
  offlineRooms: number
  errorRooms: number
  totalOnlineCount: number
  todayLiveStarts: number
  latestEvent?: BilibiliLiveStatusEvent
}

export interface BilibiliLiveTrendPoint {
  roomId: number
  uid: number
  capturedAt: string
  liveStatus: LiveStatus
  onlineCount?: number
  attentionCount?: number
}
```

### 4.3 API 封装

```ts
export function fetchLiveRooms(): Promise<BilibiliLiveRoomMonitor[]>
export function fetchLiveSummary(): Promise<BilibiliLiveSummary>
export function addLiveRoomMonitor(payload: AddLiveRoomMonitorRequest): Promise<BilibiliLiveRoomMonitor>
export function updateLiveRoomMonitor(id: number, payload: UpdateLiveRoomMonitorRequest): Promise<BilibiliLiveRoomMonitor>
export function refreshLiveRoom(id: number): Promise<BilibiliLiveRoomMonitor>
export function deleteLiveRoomMonitor(id: number): Promise<void>
export function fetchLiveTrends(roomIds: number[], limit?: number): Promise<BilibiliLiveRoomTrend[]>
```

首版页面请求顺序：

1. `fetchLiveRooms()`
2. `fetchLiveSummary()`
3. 自动选择默认房间。
4. 请求选中房间趋势。

### 4.4 页面状态

```ts
const rooms = ref<BilibiliLiveRoomMonitor[]>([])
const summary = ref<BilibiliLiveSummary>()
const expandedRoomId = ref<number>()
const selectedTrendRoomIds = ref<number[]>([])
const trendPointsByRoomId = ref<Record<number, BilibiliLiveTrendPoint[]>>({})
const loading = ref(false)
const adding = ref(false)
const refreshingRoomIds = ref<Set<number>>(new Set())
```

核心 computed：

| computed | 用途 |
| --- | --- |
| `liveRooms` | 直播中卡片和摘要 |
| `errorRooms` | 异常数量、异常样式 |
| `expandedRoom` | 当前详情 |
| `selectedTrendRooms` | 趋势图图例 |
| `shortIntervalNotice` | 短间隔提示 |
| `nextCollectText` | 摘要里的最近采集时间 |

## 5. 后端技术设计

### 5.1 模块组织

新增直播间模块，避免和现有粉丝监控耦合：

```text
backend/src/main/java/com/socialmonitor/bilibili/live/
  client/BilibiliLiveApiClient.java
  config/BilibiliLiveMonitorProperties.java
  controller/BilibiliLiveMonitorController.java
  domain/BilibiliLiveRoomMonitor.java
  domain/BilibiliLiveRoomSnapshot.java
  domain/BilibiliLiveStatusEvent.java
  dto/*
  repository/BilibiliLiveRoomMonitorRepository.java
  repository/BilibiliLiveRoomSnapshotRepository.java
  repository/BilibiliLiveStatusEventRepository.java
  service/BilibiliLiveMonitorService.java
  service/BilibiliLiveMonitorScheduler.java
```

可以复用当前已有能力：

- `BilibiliFetchException` 类似异常模型。
- 当前粉丝监控里的 scheduler-delay、due-batch-size、failure-backoff 设计。
- 当前前端的趋势图、横向卡片布局和响应式策略。

### 5.2 配置项

建议配置：

```yaml
app:
  bilibili:
    live-monitor:
      enabled: ${SOCIAL_MONITOR_BILIBILI_LIVE_MONITOR_ENABLED:true}
      storage-enabled: ${SOCIAL_MONITOR_BILIBILI_LIVE_STORAGE_ENABLED:true}
      scheduler-delay-ms: ${SOCIAL_MONITOR_BILIBILI_LIVE_SCHEDULER_DELAY_MS:1000}
      due-batch-size: ${SOCIAL_MONITOR_BILIBILI_LIVE_DUE_BATCH_SIZE:20}
      status-batch-size: ${SOCIAL_MONITOR_BILIBILI_LIVE_STATUS_BATCH_SIZE:30}
      default-interval-seconds: ${SOCIAL_MONITOR_BILIBILI_LIVE_INTERVAL_SECONDS:300}
      min-interval-seconds: ${SOCIAL_MONITOR_BILIBILI_LIVE_MIN_INTERVAL_SECONDS:60}
      max-interval-seconds: ${SOCIAL_MONITOR_BILIBILI_LIVE_MAX_INTERVAL_SECONDS:2592000}
      short-interval-warning-seconds: ${SOCIAL_MONITOR_BILIBILI_LIVE_SHORT_INTERVAL_WARNING_SECONDS:120}
      failure-backoff-seconds: ${SOCIAL_MONITOR_BILIBILI_LIVE_FAILURE_BACKOFF_SECONDS:900}
      request-min-interval-ms: ${SOCIAL_MONITOR_BILIBILI_LIVE_REQUEST_MIN_INTERVAL_MS:1500}
      metadata-refresh-seconds: ${SOCIAL_MONITOR_BILIBILI_LIVE_METADATA_REFRESH_SECONDS:86400}
```

### 5.3 外部接口封装

只封装低风控接口：

| 方法 | B站接口 | 用途 |
| --- | --- | --- |
| `getStatusInfoByUids(List<Long> uids)` | `/room/v1/Room/get_status_info_by_uids` | 周期主轮询 |
| `roomInit(long roomId)` | `/room/v1/Room/room_init` | 房间号规范化 |
| `getInfo(long roomId)` | `/room/v1/Room/get_info` | 添加时补详情 |
| `getRoomBaseInfo(List<Long> roomIds)` | `/xlive/web-room/v1/index/getRoomBaseInfo` | 批量补元数据 |
| `getRoomInfoOld(long uid)` | `/room/v1/Room/getRoomInfoOld` | UID 添加兜底 |
| `getAreaList()` | `/room/v1/Area/getList` | 分区字典 |

统一请求头：

```text
User-Agent: 普通桌面浏览器 UA
Referer: https://live.bilibili.com/
Accept: application/json, text/plain, */*
```

不要加账号 Cookie。不要在服务端保存用户 Cookie。

### 5.4 数据表

#### 监控房间表

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
    backoff_until TIMESTAMPTZ,
    source_endpoint VARCHAR(160),
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (uid),
    UNIQUE (room_id),
    CHECK (uid > 0),
    CHECK (room_id > 0),
    CHECK (interval_seconds > 0)
);

CREATE INDEX idx_bilibili_live_room_monitor_due
    ON bilibili_live_room_monitor (monitor_status, next_collect_at);

CREATE INDEX idx_bilibili_live_room_monitor_status
    ON bilibili_live_room_monitor (live_status);
```

#### 快照表

```sql
CREATE TABLE bilibili_live_room_snapshot (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    uid BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    live_status SMALLINT NOT NULL,
    title TEXT,
    area_id BIGINT,
    area_name VARCHAR(120),
    parent_area_id BIGINT,
    parent_area_name VARCHAR(120),
    online_count BIGINT,
    attention_count BIGINT,
    live_time TIMESTAMPTZ,
    source_endpoint VARCHAR(160),
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bilibili_live_room_snapshot_room_time
    ON bilibili_live_room_snapshot (room_id, captured_at DESC);

CREATE INDEX idx_bilibili_live_room_snapshot_monitor_time
    ON bilibili_live_room_snapshot (monitor_id, captured_at DESC);
```

#### 状态事件表

```sql
CREATE TABLE bilibili_live_status_event (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    uid BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    from_live_status SMALLINT,
    to_live_status SMALLINT,
    title_before TEXT,
    title_after TEXT,
    online_count BIGINT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_bilibili_live_status_event_time
    ON bilibili_live_status_event (occurred_at DESC);

CREATE INDEX idx_bilibili_live_status_event_room_time
    ON bilibili_live_status_event (room_id, occurred_at DESC);
```

事件类型首版：

| 事件 | 触发条件 |
| --- | --- |
| `LIVE_STARTED` | `0/2 -> 1` |
| `LIVE_ENDED` | `1 -> 0/2` |
| `ROUND_STARTED` | `0/1 -> 2` |
| `TITLE_CHANGED` | 标题变化，且新旧都非空 |
| `ERROR_OCCURRED` | 本次采集失败 |
| `ERROR_RECOVERED` | 上次异常后本次成功 |

### 5.5 REST API

基础路径：

```text
/api/bilibili/live-monitor
```

接口：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/rooms` | 获取监控房间列表 |
| `POST` | `/rooms` | 添加监控 |
| `PATCH` | `/rooms/{id}` | 更新间隔、暂停/恢复 |
| `DELETE` | `/rooms/{id}` | 删除监控 |
| `POST` | `/rooms/{id}/refresh` | 立即刷新单房间 |
| `GET` | `/summary` | 摘要指标 |
| `GET` | `/rooms/{id}/trends?limit=500` | 单房间趋势 |
| `GET` | `/trends?roomIds=1&roomIds=2&limit=500` | 多房间趋势 |
| `GET` | `/events?limit=20` | 最近状态事件 |

添加请求：

```json
{
  "uid": 401742377,
  "roomId": null,
  "intervalSeconds": 300
}
```

响应建议直接返回完整 `BilibiliLiveRoomMonitor`，前端无需再补请求。

### 5.6 核心业务流程

#### 用房间号添加

```text
用户输入 roomId
  -> room_init(roomId)
  -> 得到真实 room_id、short_id、uid、live_status
  -> get_info(realRoomId)
  -> 合并标题、封面、分区、online、attention
  -> upsert monitor
  -> 写入第一条 snapshot
  -> 如状态变化需要，写 event
  -> 返回 monitor
```

#### 用 UID 添加

```text
用户输入 uid
  -> get_status_info_by_uids([uid])
  -> 如果返回 room_id：保存 monitor + snapshot
  -> 如果无 room_id 或字段不足：getRoomInfoOld(uid) 兜底
  -> 如仍无直播间：返回明确错误，不创建监控
```

#### 周期采集

```text
scheduler 每 scheduler-delay-ms 扫描
  -> 找到 ACTIVE 且 next_collect_at <= now 且不在 backoff 的房间
  -> 按 status-batch-size 聚合 uid
  -> get_status_info_by_uids(uids)
  -> 对每个返回结果：
       更新 monitor 当前字段
       写 snapshot
       根据旧状态生成 status_event
       next_collect_at = now + interval_seconds
       清空 last_error/backoff
  -> 对失败项：
       记录 last_error
       生成 ERROR_OCCURRED
       backoff_until = now + failure_backoff_seconds
       next_collect_at = backoff_until
```

#### 元数据低频刷新

`get_status_info_by_uids` 已经能返回大部分展示字段。为了减少请求，元数据刷新只在这些场景触发：

- 新增房间时。
- 用户手动刷新详情时。
- 距上次元数据刷新超过 `metadata-refresh-seconds`。
- 标题、分区、封面字段缺失时。

批量补元数据优先用 `getRoomBaseInfo`。

## 6. 风控与异常策略

请求控制：

- 全局 B站请求最小间隔默认 `1500ms`。
- 周期采集按 UID 批量，不逐个房间打接口。
- 默认采集间隔 `300s`。
- 首版 UI 允许配置短间隔，但生产建议最小 `60s`。
- 失败后退避默认 `900s`。

错误分类：

| 场景 | 处理 |
| --- | --- |
| `code = 0` | 正常写快照 |
| `code = -352` | 标记 `RISK_CONTROLLED`，进入退避，不立即重试 |
| `code = -101` | 标记 `LOGIN_REQUIRED`，该接口不再作为首版依赖 |
| 4xx 参数错误 | 标记 `INVALID_REQUEST`，不自动重试 |
| 5xx/网络超时 | 标记 `NETWORK_ERROR`，退避后重试 |
| 返回缺少目标 UID | 标记 `TARGET_MISSING`，单项退避 |

页面错误展示：

- 卡片右上角显示 `异常`。
- 卡片正文显示短错误，如 `接口返回 -352，已退避`。
- 详情里展示完整错误类型、时间、下一次重试时间。
- 摘要里的异常数可点击过滤。

## 7. 实施拆分

建议按 5 步做：

1. 数据库迁移
   - 新建三张表。
   - 加唯一约束和采集索引。

2. 后端接口和采集
   - 实现 `BilibiliLiveApiClient`。
   - 实现添加、列表、暂停、删除、单房间刷新。
   - 实现 scheduler 和快照/事件写入。

3. 前端 API 和页面骨架
   - 新建 `bilibiliLive.ts`。
   - 新建 `BilibiliLiveView.vue`。
   - 复用当前 `control-surface`、`summary-grid`、横向卡片和趋势图风格。

4. 趋势与事件
   - 接入趋势查询。
   - 添加默认选中逻辑和最多 4 个趋势选择。
   - 摘要接入今日开播次数和最近事件。

5. 风控保护与验证
   - 加请求限速。
   - 加 `-352`、`-101`、网络错误分类。
   - 用少量 UID/房间做低频验证。

## 8. 验收清单

页面：

- 桌面端和当前 B站粉丝页风格一致。
- 移动端所有卡片单列堆叠，不横向溢出。
- 添加、刷新、暂停、恢复、删除都有明确 loading 和错误反馈。
- 直播中、未开播、轮播、异常、暂停状态可区分。
- 趋势最多 4 个房间，超过后按钮禁用且有 tooltip。

技术：

- 添加房间号会先规范化真实房间号。
- 添加 UID 能通过批量状态接口或旧接口兜底。
- 周期采集优先批量按 UID 调用。
- 每次成功采集写快照。
- 直播状态变化写事件。
- 失败进入退避，不高频重试。

风控：

- 不需要 Cookie。
- 不实现 WBI。
- 不调用 WebSocket。
- 不调用状态变更接口。
- 命中 `-352`、`-101` 有明确错误分类和页面展示。
