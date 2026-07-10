# Bilibili 直播房间观众与大航海榜单采集技术方案

调研日期：2026-06-18  
适用项目：`social-data-monitor`  
上游资料：`../../docs/bilibili-live-room-audience-guard-rank-research.md`、`../../bilibili-api-collect-new-research/repo`

## 1. 结论

截图里的「房间观众」和「大航海」数据可以采集，且可以接入当前项目的直播间监控体系。

如果优先考虑请求量和实现成本，先看低成本分层方案：`bilibili-live-rank-low-cost-collection-research.md`。推荐第一版采用 `lazy` 模式：后台只采集在线贡献榜摘要和大航海摘要，详情榜单由前端按需加载。

可稳定落库的数据：

- 房间观众括号数：从 `queryContributionRank.data.count/count_text` 获取；在线贡献榜备用口可从 `getOnlineGoldRank.data.onlineNum/onlineNumText` 获取。
- 房间观众在线榜贡献值：从 `queryContributionRank(type=online_rank,switch=contribution_rank)` 获取。
- 房间观众进房时间榜：从 `queryContributionRank(type=online_rank,switch=entry_time_rank)` 获取，通常只有顺序意义，`score` 多数为 0。
- 房间观众日榜、周榜、月榜：从 `queryContributionRank` 的 `daily_rank/weekly_rank/monthly_rank` 获取。
- 大航海括号数：从 `guardTab/topListNew.data.info.num` 获取。
- 大航海周榜、月榜、陪伴榜、Top3、上一周期 Top1 提示：从 `guardTab/topListNew` 获取。

需要注意的限制：

- `queryContributionRank` 当前需要 WBI 签名；已有弹幕模块里的 `BilibiliWbiSigner` 可以复用或上移成通用组件。
- `guardTab/topListNew` 当前无 Cookie、无 WBI 也可取公开榜单。
- `guardTab/topListNew` 的周/月/陪伴榜顺序可取，但响应里的 `score` 实测恒为 `0`，不能还原页面文案里的「亲密度」具体分值，只能保存排名、顺序、陪伴天数、用户信息和粉丝牌/舰长等级。
- `room/v1/Room/get_info.data.online` 不是截图中「房间观众(2598)」的同一指标，不能混用。
- 登录态字段如 `my_follow_info`、当前登录用户自己的排名、自动续费等，不登录时通常为空；本方案默认只采集公开榜单。

## 2. 当前项目落点

现有直播监控模块已经具备接入基础：

- 后端包：`com.socialmonitor.bilibili.live`
- REST 入口：`BilibiliLiveMonitorController`，路径 `/api/bilibili/live-monitor`
- 现有采集服务：`BilibiliLiveMonitorService`
- 现有调度：`BilibiliLiveMonitorScheduler`
- 现有限频：`RateLimitService.acquireMinInterval`
- 现有快照表：`bilibili_live_room_snapshot`，保留 `raw_payload_json`
- 现有监控主表：`bilibili_live_room_monitor`，保留 `extension_json`
- 已有 WBI 能力：`com.socialmonitor.bilibili.live.danmaku.client.BilibiliWbiSigner`
- 已有前端 API：`frontend/src/api/bilibiliLive.ts`
- 已有直播页：`frontend/src/views/bilibili-live/BilibiliLiveView.vue`
- 已有 subject workbench：`SubjectWorkbenchService` + `SubjectWorkbenchView`

推荐做成直播监控的子模块，而不是塞进现有直播状态采集：

- 状态采集继续负责开播状态、标题、热度、封面等低成本接口。
- 榜单采集新增独立 `rank` 子模块，单独限频、单独调度、单独失败退避。
- subject 工作台只读最新摘要，不在工作台请求链路里触发大分页采集。

## 3. 接口设计

### 3.1 房间归一化

所有榜单请求前先确保有真实房间号和主播 UID：

```http
GET https://api.live.bilibili.com/room/v1/Room/room_init?id={input_room_id}
```

项目当前 `BilibiliLiveApiClient.fetchRoomInit` 已覆盖这个能力。后续榜单参数统一使用：

- `room_id` / `roomid`：`room_init.data.room_id`
- `ruid`：`room_init.data.uid`

### 3.2 房间观众榜

推荐主接口：

```http
GET https://api.live.bilibili.com/xlive/general-interface/v1/rank/queryContributionRank
```

公共参数：

| 参数 | 值 |
| --- | --- |
| `ruid` | 主播 UID |
| `room_id` | 真实房间 ID |
| `page` | 页码 |
| `page_size` | 建议 50 或 100 |
| `platform` | `web` |
| `web_location` | `444.8` |
| `wts` / `w_rid` | WBI 签名结果 |

榜单映射：

| UI | `type` | `switch` | 采集优先级 |
| --- | --- | --- | --- |
| 在线榜-贡献值 | `online_rank` | `contribution_rank` | P0 |
| 在线榜-进房时间 | `online_rank` | `entry_time_rank` | P1 |
| 日榜-当日 | `daily_rank` | `today_rank` | P1 |
| 日榜-昨日 | `daily_rank` | `yesterday_rank` | P2 |
| 周榜-本周 | `weekly_rank` | `current_week_rank` | P1 |
| 周榜-上周 | `weekly_rank` | `last_week_rank` | P2 |
| 月榜-本月 | `monthly_rank` | `current_month_rank` | P1 |
| 月榜-上月 | `monthly_rank` | `last_month_rank` | P2 |

关键字段：

- `data.count/count_text`：房间观众括号数。
- `data.item[]`：榜单明细。
- `item.rank`：排名。
- `item.uid/name/face`：用户 UID、昵称、头像。
- `item.score`：贡献值，在线贡献榜、日周月贡献榜有展示意义。
- `item.medal_info` / `item.uinfo.medal`：粉丝牌信息。
- `item.guard_level` / `item.uinfo.guard.level`：大航海等级。
- `item.wealth_level`：财富/荣耀等级。
- `data.config.value_text`：页面右侧指标文案，实测为「贡献值」。

备用接口：

```http
GET https://api.live.bilibili.com/xlive/general-interface/v1/rank/getOnlineGoldRank
```

只用于在线贡献榜降级：

- 参数：`roomId`、`ruid`、`page`、`pageSize`
- 输出：`onlineNum/onlineNumText`、`OnlineRankItem[]`
- 不覆盖日榜、周榜、月榜、进房时间榜。

### 3.3 大航海榜

推荐接口：

```http
GET https://api.live.bilibili.com/xlive/app-room/v2/guardTab/topListNew
```

参数：

| 参数 | 值 |
| --- | --- |
| `roomid` | 真实房间 ID |
| `ruid` | 主播 UID |
| `page` | 页码 |
| `page_size` | 建议 30 |
| `typ` | 榜单类型 |

2026-06-18 实测映射：

| UI | `typ` | 说明 |
| --- | --- | --- |
| 周榜 | `4` | 周大航海亲密度 |
| 月榜 | `3` | 月大航海亲密度 |
| 陪伴榜 | `1` | 上船后累计陪伴天数 |
| 默认/旧榜 | `0` 或 `5` | 旧口径；不作为主采集目标 |

注意：`bilibili-api-collect-new-research/repo/docs/live/guard.md` 旧文档写 `typ=3,4,5` 分别为周/月/总亲密度，但当前页面实测是 `4=周榜`、`3=月榜`、`1=陪伴榜`。实现时应读取响应 `data.remind_msg` 做一次启动自检，若文案与预期不符，记录接口变更事件并暂停对应类型的自动采集。

关键字段：

- `data.info.num`：大航海总数，即截图「大航海(80)」。
- `data.info.page/now`：总页数、当前页。
- `data.top3[]`：榜单前三。
- `data.list[]`：后续列表；`page=1` 时通常从第 4 名开始。
- `data.extop[]`：上一周期 Top1 提示。
- `rank`：排名。
- `accompany`：陪伴天数。
- `uinfo.uid/base.name/base.face`：用户信息。
- `uinfo.medal`：粉丝牌。
- `uinfo.guard.level`：大航海等级。
- `data.remind_msg`：榜单说明文案，可用于校验 `typ`。

## 4. 后端模块方案

新增包建议：

```text
com.socialmonitor.bilibili.live.rank
├── client
│   ├── BilibiliLiveRankApiClient
│   └── dto/raw response records
├── config
│   └── BilibiliLiveRankProperties
├── controller
│   └── BilibiliLiveRankController
├── domain
│   ├── BilibiliLiveRankSnapshot
│   └── BilibiliLiveRankEntry
├── dto
│   ├── BilibiliLiveRankSnapshotView
│   ├── BilibiliLiveRankEntryView
│   └── BilibiliLiveRankSummaryView
├── repository
│   └── BilibiliLiveRankRepository
└── service
    ├── BilibiliLiveRankService
    └── BilibiliLiveRankScheduler
```

### 4.1 WBI 复用

当前 `BilibiliWbiSigner` 在 `live.danmaku.client` 下，并依赖 `BilibiliLiveDanmakuProperties` 里的 `wbiCacheSeconds`。

推荐改法：

1. 把 `BilibiliWbiSigner` 上移到 `com.socialmonitor.bilibili.client` 或 `com.socialmonitor.bilibili.live.client`。
2. 新增通用配置 `app.bilibili.wbi.cache-seconds`，保留旧 danmaku 配置兼容，或者让 danmaku/rank 都注入同一个 signer。
3. `queryContributionRank` 使用 `sign(params, false)` 生成签名。
4. 遇到 `-352/-412`、签名失效、返回 `v_voucher` 时，清理 WBI 缓存并强制刷新后重试一次。

### 4.2 Client 方法

`BilibiliLiveRankApiClient` 建议提供：

```java
BilibiliLiveAudienceRankResponse fetchAudienceRank(
        long roomId,
        long ruid,
        AudienceRankType type,
        AudienceRankSwitch rankSwitch,
        int page,
        int pageSize,
        boolean forceRefreshWbi
);

BilibiliLiveAudienceRankResponse fetchOnlineGoldRankFallback(
        long roomId,
        long ruid,
        int page,
        int pageSize
);

BilibiliLiveGuardRankResponse fetchGuardRank(
        long roomId,
        long ruid,
        GuardRankType type,
        int page,
        int pageSize
);
```

错误处理沿用 `BilibiliLiveApiClient.ensureSuccess` 的风格：

- `code=0`：成功。
- `-101`：登录态缺失；公开榜单通常不需要登录，但 WBI nav 可能返回 `-101` 仍带 keys，应按现有 signer 逻辑接受。
- `-352/-412`：风控或请求异常；记录 API 调用日志，触发退避。
- HTTP 5xx / 网络错误：走 `RetryPolicy`。

### 4.3 Service 与调度

不要把榜单采集放进 `BilibiliLiveMonitorService.collectDueRooms()` 主循环，避免榜单分页拖慢开播状态采集。

新增 `BilibiliLiveRankScheduler`：

- 默认 `fixedDelay=5000ms`。
- 每次挑选少量 due task。
- 每个 room + rank kind 单独计算 next due。
- 使用 `RateLimitService.acquireMinInterval("BILIBILI", "live-rank", requestMinInterval)` 做全局限频。
- 同一房间多榜单之间加入 10%-20% 抖动，避免请求集中。

采集频率建议：

| 数据 | 开播中 | 未开播 | 默认页数 |
| --- | --- | --- | --- |
| 在线贡献榜 | 60 秒 | 300 秒 | 1 页，最多 100 人 |
| 进房时间榜 | 120 秒 | 600 秒 | 1 页 |
| 日榜当前 | 5 分钟 | 30 分钟 | 1-3 页 |
| 周榜/月榜当前 | 10 分钟 | 60 分钟 | 1-3 页 |
| 上一日/上一周/上一月 | 60 分钟 | 6 小时 | 1-3 页 |
| 大航海周/月/陪伴榜 | 10-30 分钟 | 60 分钟 | 1-3 页 |

默认先抓 Top100 或 Top90；全量翻页做成显式配置，不默认打开。

## 5. 数据库设计

新增迁移：`V8__bilibili_live_rank_monitor.sql`

### 5.1 快照表

```sql
CREATE TABLE IF NOT EXISTS bilibili_live_rank_snapshot (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    room_id BIGINT NOT NULL,
    ruid BIGINT NOT NULL,
    rank_family VARCHAR(32) NOT NULL,
    rank_type VARCHAR(64) NOT NULL,
    rank_switch VARCHAR(64),
    period_scope VARCHAR(32),
    page_no INTEGER NOT NULL,
    page_size INTEGER NOT NULL,
    total_count BIGINT,
    count_text VARCHAR(64),
    value_text VARCHAR(64),
    remind_msg VARCHAR(512),
    source_endpoint VARCHAR(256) NOT NULL,
    signed_required BOOLEAN NOT NULL DEFAULT FALSE,
    captured_at TIMESTAMPTZ NOT NULL,
    captured_bucket TIMESTAMPTZ NOT NULL,
    raw_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bilibili_live_rank_snapshot_bucket
    ON bilibili_live_rank_snapshot (
        monitor_id,
        rank_family,
        rank_type,
        (COALESCE(rank_switch, '')),
        (COALESCE(period_scope, '')),
        captured_bucket,
        page_no
    );
```

`rank_family`：

- `AUDIENCE`
- `GUARD`

`rank_type` 示例：

- `online_rank`
- `daily_rank`
- `weekly_rank`
- `monthly_rank`
- `guard_weekly`
- `guard_monthly`
- `guard_accompany`

`period_scope` 示例：

- `REALTIME`
- `CURRENT`
- `PREVIOUS`

### 5.2 明细表

```sql
CREATE TABLE IF NOT EXISTS bilibili_live_rank_entry (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES bilibili_live_rank_snapshot(id) ON DELETE CASCADE,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    room_id BIGINT NOT NULL,
    ruid BIGINT NOT NULL,
    user_uid BIGINT,
    rank_no INTEGER,
    entry_kind VARCHAR(32) NOT NULL,
    display_name VARCHAR(512),
    face_url TEXT,
    score BIGINT,
    guard_level INTEGER,
    wealth_level INTEGER,
    medal_name VARCHAR(128),
    medal_level INTEGER,
    medal_ruid BIGINT,
    medal_is_light INTEGER,
    guard_expired_text VARCHAR(128),
    accompany_days INTEGER,
    raw_entry_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

`entry_kind`：

- `TOP3`
- `LIST`
- `EXTOP`
- `OWN`

索引：

```sql
CREATE INDEX idx_bilibili_live_rank_snapshot_room_latest
    ON bilibili_live_rank_snapshot (monitor_id, rank_family, rank_type, captured_at DESC);

CREATE INDEX idx_bilibili_live_rank_entry_snapshot
    ON bilibili_live_rank_entry (snapshot_id, rank_no);

CREATE INDEX idx_bilibili_live_rank_entry_user
    ON bilibili_live_rank_entry (user_uid, room_id, created_at DESC);
```

### 5.3 最新摘要

为页面查询效率，建议加一张最新摘要表，或先写入 `bilibili_live_room_monitor.extension_json`：

```json
{
  "rankSummary": {
    "audienceCount": 2598,
    "audienceCountText": "2598",
    "guardCount": 80,
    "guardCountText": "80",
    "audienceOnlineTop": [
      { "rank": 1, "uid": 123, "name": "...", "score": 4206 }
    ],
    "guardWeeklyTop": [
      { "rank": 1, "uid": 456, "name": "...", "guardLevel": 2 }
    ],
    "updatedAt": "2026-06-18T12:00:00+08:00"
  }
}
```

一期推荐：

- 完整快照和明细入新表。
- `extension_json` 只存最新摘要，供列表页和 subject workbench 快速展示。

## 6. 后端 REST API

新增 controller 路径沿用当前风格：

```http
GET  /api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/latest
POST /api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/refresh
GET  /api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/history
GET  /api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks/summary
```

`latest` 查询参数：

- `family=AUDIENCE|GUARD`
- `type=online_rank|daily_rank|weekly_rank|monthly_rank|guard_weekly|guard_monthly|guard_accompany`
- `switch=contribution_rank|entry_time_rank|...`
- `limit=100`

`refresh` body：

```json
{
  "families": ["AUDIENCE", "GUARD"],
  "types": ["online_rank", "guard_weekly"],
  "maxPages": 1,
  "force": true
}
```

subject workbench 接入：

- `SubjectWorkbenchView` 可新增 `bilibiliLiveRankSummary` 字段。
- `SubjectWorkbenchService` 只查最新摘要，不触发远程采集。
- 若 rank 模块未启用，返回 `null`，不影响现有粉丝/直播/弹幕。

## 7. 前端接入

### 7.1 直播页

在 `BilibiliLiveView.vue` 的展开房间详情里新增榜单区域：

- 一级 tabs：`房间观众` / `大航海`
- 房间观众二级 tabs：`在线榜`、`日榜`、`周榜`、`月榜`
- 在线榜内 segmented：`贡献值`、`进房时间`
- 大航海二级 tabs：`周榜`、`月榜`、`陪伴榜`
- 表格列：排名、头像昵称、粉丝牌、大航海等级、贡献值/陪伴天数、采集时间
- 操作：刷新当前榜单

新增 API 类型和函数放在 `frontend/src/api/bilibiliLive.ts`：

- `BilibiliLiveRankSummary`
- `BilibiliLiveRankSnapshot`
- `BilibiliLiveRankEntry`
- `fetchBilibiliLiveRankLatest`
- `refreshBilibiliLiveRanks`

### 7.2 Subject 工作台

新增 widget：

- 文件：`frontend/src/views/subjects/widgets/BilibiliLiveRankWidget.vue`
- 展示最新房间观众数、大航海数、在线贡献榜 Top5、大航海周榜 Top5。
- 数据来自 `SubjectWorkbench.bilibiliLiveRankSummary`。

## 8. 配置建议

新增配置前缀：

```yaml
app:
  bilibili:
    live-monitor:
      rank:
        enabled: true
        scheduler-delay-ms: 5000
        due-batch-size: 5
        page-size: 50
        max-pages: 1
        request-min-interval-ms: 1500
        audience-online-interval-seconds: 60
        audience-period-interval-seconds: 600
        guard-interval-seconds: 900
        offline-multiplier: 5
        failure-backoff-seconds: 1800
        wbi-cache-seconds: 43200
        web-location: "444.8"
```

若 WBI signer 上移为通用组件，也可使用：

```yaml
app:
  bilibili:
    wbi:
      cache-seconds: 43200
```

## 9. 降级与异常策略

- `queryContributionRank` 首次失败且错误像 WBI 失效：刷新 WBI key 后重试一次。
- 重试后仍失败：记录 API 调用日志，当前榜单进入退避，不影响其他榜单。
- 在线贡献榜主接口失败：尝试 `getOnlineGoldRank` 备用接口，并在 snapshot 上标记 `source_endpoint=getOnlineGoldRank`。
- `guardTab/topListNew` `remind_msg` 与配置映射不一致：暂停该 `typ` 自动采集，记录事件，保留 raw payload。
- 分页重复：大航海 `top3` 与 `list` 合并时按 `rank + uid` 去重。
- 字段为空：`uinfo`、`medal`、`wealth`、`guard` 均按可空解析，raw JSON 完整保存。
- 退避维度：按 `room + rank_family + rank_type + rank_switch` 退避，避免一个榜单失败拖住整个房间。

## 10. 实施顺序

1. 上移或复用 `BilibiliWbiSigner`，补一个固定 key 的签名单测。
2. 新增 `BilibiliLiveRankApiClient`，用保存的 JSON fixture 做解析单测。
3. 新增 `V8__bilibili_live_rank_monitor.sql`，建 snapshot/entry 表和索引。
4. 新增 repository、service、scheduler、properties。
5. 新增 REST API 和 DTO。
6. `BilibiliLiveView.vue` 接榜单 tabs。
7. `SubjectWorkbenchView` 增加 rank summary，新增 subject widget。
8. 加手动刷新入口和错误提示。
9. 跑后端单测、前端 typecheck/build。

## 11. 验证清单

后端：

- `BilibiliWbiSigner` 对官方文档固定样例生成相同 `w_rid`。
- `queryContributionRank` 参数包含 `platform=web`、`web_location=444.8`、`wts`、`w_rid`。
- `topListNew` 的 `typ=4/3/1` 分别通过 `remind_msg` 校验周/月/陪伴。
- 大航海 `top3 + list` 合并后排名连续且不重复。
- `getOnlineGoldRank` 只在在线贡献榜降级时使用。
- `raw_payload_json` 与 `raw_entry_json` 完整保留原始响应。

前端：

- 直播页展开房间后，房间观众和大航海 tabs 能独立刷新。
- 没有最新榜单时显示空态，不影响已有直播状态详情。
- subject workbench 没有 rank summary 时不报错。
- 长昵称、长粉丝牌名、空头像、空 medal 均不撑破表格。

## 12. 参考资料

- `../../docs/bilibili-live-room-audience-guard-rank-research.md`
- `../../bilibili-api-collect-new-research/repo/docs/live/guard.md`
- `../../bilibili-api-collect-new-research/repo/docs/live/user.md`
- `../../bilibili-api-collect-new-research/repo/docs/misc/sign/wbi.md`
- `social-data-monitor/docs/project-conventions.md`
