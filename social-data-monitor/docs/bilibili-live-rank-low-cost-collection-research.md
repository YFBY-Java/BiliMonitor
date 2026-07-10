# Bilibili 直播房间观众与大航海低成本采集研究

调研日期：2026-06-18  
样本房间：`26854650`，主播 UID：`3493118494116797`  
关联方案：`bilibili-live-room-audience-guard-rank-technical-plan.md`

## 1. 结论

有更低成本的采集方式，但要按数据目标分层。

最低成本可行方案：

- 房间观众在线人数/在线贡献榜：优先用旧接口 `getOnlineGoldRank?pageSize=1`，匿名、无 WBI，能拿 `onlineNum/onlineNumText` 和 Top1。
- 大航海总数/榜首摘要：用 `topListNew?page_size=1`，匿名、无 WBI，能拿 `info.num`、`top3`、第 4 名、`extop` 和 `remind_msg`。
- 日榜、周榜、月榜、进房时间榜：仍需要 `queryContributionRank`。可以把它降为“打开页面时按需请求”或低频轮询，而不是后台高频采集。
- 如果弹幕 WebSocket 已经启用，可以顺带消费 `GUARD_BUY`、`USER_TOAST_MSG`、`ONLINE_RANK_COUNT`、`ONLINE_RANK_V2/V3` 等事件，作为实时增量信号；但 WebSocket 不能替代页面右侧完整历史榜单。

不建议的低成本假设：

- 不要用 `room/v1/Room/get_info.data.online` 替代截图里的「房间观众」括号数，它不是同一指标。
- 不要把 `page_size=0` 当作“只取计数”。实测 `getOnlineGoldRank?pageSize=0` 返回 `-400`；`topListNew?page_size=0` 会返回默认 10 条，反而比 `page_size=1` 更大。
- 不要为了低成本默认抓全量分页；全量榜单适合人工刷新、定时低频快照或离线回填。

## 2. 实测结果

测试方式：匿名请求，普通浏览器 UA，`Referer: https://live.bilibili.com/`。响应大小为未压缩 body 近似值，直播间实时人数会波动。

### 2.1 在线贡献榜旧接口

```http
GET https://api.live.bilibili.com/xlive/general-interface/v1/rank/getOnlineGoldRank
```

| 参数 | 响应大小 | 返回 | 结论 |
| --- | ---: | --- | --- |
| `pageSize=0` | 46 B | `code=-400` | 不可用 |
| `pageSize=1` | 1.8 KB | `onlineNum` + 1 条 | 最低成本在线观众/Top1 |
| `pageSize=5` | 7.4 KB | `onlineNum` + 5 条 | Top5 可用 |
| `pageSize=20` | 24.6 KB | `onlineNum` + 18 条 | 数据量明显增加 |

优势：

- 不需要 WBI。
- 不需要 Cookie。
- 能拿截图在线榜贡献值和括号数。
- 适合作为后台常规轮询入口。

缺口：

- 只覆盖在线贡献榜。
- 不支持进房时间榜、日榜、周榜、月榜。

### 2.2 房间观众新接口

```http
GET https://api.live.bilibili.com/xlive/general-interface/v1/rank/queryContributionRank
```

`online_rank/contribution_rank` 实测：

| 参数 | 响应大小 | 返回 | 结论 |
| --- | ---: | --- | --- |
| `page_size=1` | 2.0 KB | `count` + 1 条 | 低成本可用，但需要 WBI |
| `page_size=5` | 8.5 KB | `count` + 5 条 | Top5 可用 |
| `page_size=20` | 31.2 KB | `count` + 20 条 | 数据量更高 |

`online_rank/entry_time_rank` 实测：

| 参数 | 响应大小 | 返回 | 结论 |
| --- | ---: | --- | --- |
| `page_size=1` | 2.1 KB | 1 条，`count=0` | 只能拿列表顺序，不能拿观众括号数 |
| `page_size=5` | 7.0 KB | 5 条，`count=0` | 按需展示即可 |
| `page_size=20` | 26.2 KB | 20 条，`count=0` | 不适合高频轮询 |

WBI key 请求本次约 259 B，返回 `code=-101` 但包含 `wbi_img`，可匿名使用。WBI key 缓存后，主要成本仍在榜单接口本身。

### 2.3 大航海接口

```http
GET https://api.live.bilibili.com/xlive/app-room/v2/guardTab/topListNew
```

周榜 `typ=4` 实测：

| 参数 | 响应大小 | 返回 | 结论 |
| --- | ---: | --- | --- |
| `page_size=0` | 15.0 KB | `top3` + 10 条 | 不要用，默认返回更多 |
| `page_size=1` | 6.2 KB | `info.num` + `top3` + 1 条 + `extop` | 最低成本摘要 |
| `page_size=5` | 10.1 KB | `top3` + 5 条 + `extop` | Top8 可用 |
| `page_size=30` | 34.9 KB | `top3` + 30 条 + `extop` | 接近页面完整页 |

月榜 `typ=3` 与周榜大小接近。陪伴榜 `typ=1` 因无 `extop`，`page_size=1` 约 5.0 KB。

关键点：

- 任意 `typ` 都会返回 `info.num`，即大航海总数。
- `top3` 独立返回，`list` 从第 4 名开始；所以 `page_size=1` 实际可展示 Top4。
- 如果只想要 Top5，`page_size=2` 就够。
- 如果只想要大航海总数，选一个 `typ` 请求即可，不需要周/月/陪伴三种都请求。

## 3. 分层采集方案

### 3.1 L0：最低成本摘要

目标：列表页、subject 工作台、房间卡片只显示括号数和少量榜首。

每个直播中房间：

- 在线观众：`getOnlineGoldRank?pageSize=1`，60-120 秒一次。
- 大航海总数：`topListNew?typ=4&page_size=1`，10-30 分钟一次。
- 房间真实 ID / ruid：使用 monitor 表已有值，不每次 `room_init`。

每 10 分钟请求量示例：

- 在线观众每 60 秒：10 次。
- 大航海每 10 分钟：1 次。
- 合计：约 11 次/房间/10 分钟，无 WBI，无 Cookie。

拿不到：

- 日榜、周榜、月榜观众贡献榜。
- 进房时间完整列表。
- 大航海月榜、陪伴榜摘要，除非额外请求对应 `typ`。

### 3.2 L1：页面按需加载

目标：用户打开房间详情或切换 tab 时展示榜单。

后台只跑 L0。前端打开详情时：

- 在线贡献榜：先展示 L0 缓存；用户点“更多”再拉 `getOnlineGoldRank?pageSize=20/50`。
- 日/周/月榜：切到对应 tab 才调 `queryContributionRank?page_size=20`。
- 大航海周/月/陪伴：切到对应 tab 才调 `topListNew?typ=4/3/1&page_size=7`，即可得到 Top10。

优点：

- 没有人看的房间不拉大列表。
- WBI 只在需要贡献周期榜时使用。
- 页面首屏仍有摘要。

### 3.3 L2：重点房间低频历史

目标：对少量重点房间做历史榜单趋势。

建议频率：

- 在线贡献榜 Top20：5 分钟一次。
- 日榜当前 Top20：15-30 分钟一次。
- 周榜/月榜当前 Top20：30-60 分钟一次。
- 大航海周/月/陪伴 Top10：30-60 分钟一次。

入库策略：

- 每次采集的原始响应完整写入 `raw_payload_json`。
- 明细只解析本次请求实际返回的条目。
- 可对 `rank_family + rank_type + rank_switch + page + payload_hash` 做去重，payload 未变化时不重复写明细，只更新最新摘要时间。

### 3.4 L3：全量分页

目标：完整榜单分析或离线回填。

触发方式：

- 手动刷新。
- 每天/每小时低频任务。
- 指定重点房间白名单。

不建议默认开启，原因：

- 大航海每页最多 30，人数较多时需要多次请求。
- 周/月/陪伴三个榜单全量抓取请求数会翻倍。
- 观众日/周/月多个 `type/switch` 全量抓取需要 WBI，风控和调度复杂度更高。

## 4. WebSocket 是否更低成本

如果项目已经为房间开启弹幕 WebSocket，边际成本很低，可以补充实时事件：

- `GUARD_BUY`：用户购买舰长/提督/总督。
- `USER_TOAST_MSG`：上舰庆祝消息，包含陪伴天数、当前大航海数量等字段。
- `ONLINE_RANK_COUNT`：在线高能榜人数。
- `ONLINE_RANK_V2/V3`：在线高能榜用户摘要。
- `INTERACT_WORD/INTERACT_WORD_V2`：进房/关注等交互。

但它不等价于 REST 榜单：

- 无法稳定补齐当前完整周榜/月榜/陪伴榜。
- 无法补齐离线期间错过的事件。
- 建连本身需要 `getDanmuInfo`，该接口需要 WBI 和匿名设备标识。

建议：

- 已经启用弹幕模块的重点房间：用 WebSocket 做实时增量，REST 做低频校准。
- 没有弹幕需求的房间：不要为了榜单单独建 WebSocket，L0 REST 更简单。

## 5. 推荐项目配置

新增 rank 模式：

```yaml
app:
  bilibili:
    live-monitor:
      rank:
        mode: summary
        online-summary-interval-seconds: 60
        guard-summary-interval-seconds: 900
        summary-online-page-size: 1
        summary-guard-page-size: 1
        detail-page-size: 20
        guard-detail-list-size: 7
        enable-period-rank-scheduler: false
        enable-full-crawl: false
```

模式定义：

| 模式 | 行为 |
| --- | --- |
| `off` | 不自动采集榜单 |
| `summary` | 只跑 L0 摘要 |
| `lazy` | L0 摘要 + 前端按需加载详情 |
| `focused` | 对重点房间跑 L2 低频历史 |
| `full` | 开启全量分页，需显式白名单 |

## 6. 最终建议

项目第一版采用 `lazy`：

1. 后台默认只采集 `getOnlineGoldRank?pageSize=1` 和 `topListNew?typ=4&page_size=1`。
2. 在 `bilibili_live_room_monitor.extension_json.rankSummary` 存最新摘要。
3. 用户打开房间详情时再请求当前 tab 的榜单。
4. 只有用户显式标记的重点房间才开启 L2 历史。
5. 全量分页只做手动任务，不进入默认 scheduler。

这样可以拿到截图里最显眼的两个括号数和榜首摘要，同时避免 WBI 高频请求、避免大分页、避免后台为无人查看的 tab 做重复采集。

## 7. 参考资料

- `../../docs/bilibili-live-room-audience-guard-rank-research.md`
- `bilibili-live-room-audience-guard-rank-technical-plan.md`
- `../../docs/bilibili-live-danmaku-research.md`
- `../../bilibili-api-collect-new-research/repo/docs/live/user.md`
- `../../bilibili-api-collect-new-research/repo/docs/live/guard.md`
- `../../bilibili-api-collect-new-research/repo/docs/live/message_stream.md`
