# B站直播弹幕与信息流调研报告

最后更新：2026-06-17  
资料来源：`../bilibili-api-collect-new-research/repo/docs/live/`、`../bilibili-api-collect-new-research/repo/docs/misc/`、现有项目文档、少量匿名低频实测，以及公开第三方实现说明。  
实测方式：使用普通浏览器 `User-Agent` 和 `Referer`，不携带账号 Cookie，不发送弹幕，不调用送礼、禁言、管理、开播等会改变状态的接口。

## 结论摘要

2026-06-17 更新：项目已接入 B站扫码登录态。实时弹幕现在优先使用已保存的 `SESSDATA` 登录 Cookie 调用 `getDanmuInfo`，并在 WebSocket 鉴权包中使用同一账号的 `DedeUserID`/mid 作为 `uid`。这样可以避开游客态“未注册登录用户无法查看他人昵称”的隐私限制，显著提高拿到弹幕发送者完整昵称的稳定性。若登录态不可用、过期或触发风控，后端会自动回退到原游客态链路，不做验证码或复杂风控绕过。

可以用较低成本拿到直播间实时弹幕。推荐方案是：

1. `room_init` 解析真实直播间号。
2. 匿名获取 `buvid3/buvid4`。
3. 从 `nav` 获取 WBI key，对 `getDanmuInfo` 做 WBI 签名。
4. 用 `getDanmuInfo` 返回的 `token` 和 `host_list` 连接直播信息流 WebSocket。
5. 发送鉴权包，持续心跳，解析 `DANMU_MSG` 和其他直播事件。

这条链路不需要 `SESSDATA`，也不需要账号登录。但游客态有隐私限制：部分弹幕和用户交互事件中的 `uid` 会变成 `0`，昵称会打码，不能保证拿到完整用户身份。若业务只需要弹幕正文、时间、房间维度统计和直播事件，这个限制可以接受；若业务需要完整用户画像，则会进入账号态 Cookie 和隐私合规问题，不建议作为当前低成本方案。

HTTP 轮询也有一个轻量备选：`xlive/web-room/v1/dM/gethistory` 可以拿最近历史弹幕，但窗口很小，通常只有普通用户最新 10 条和管理员最新 10 条。它适合进房间补一屏、断线后粗略补洞、筛选活跃房间，不适合作为实时弹幕主链路。

## 当前实测结果

实测日期：2026-06-13。测试房间来自公开直播间，不携带登录态。

| 测试项 | 请求或动作 | 结果 | 结论 |
| --- | --- | --- | --- |
| 房间号解析 | `GET /room/v1/Room/room_init?id=6` | `code=0`，返回真实房间号 `7734200` | 匿名可用，适合作为房间号规范化入口 |
| 单房间详情 | `GET /room/v1/Room/get_info?room_id=6` | `code=0`，返回标题、分区、封面、在线/热度等 | 匿名可用 |
| 最近历史弹幕 | `GET /xlive/web-room/v1/dM/gethistory?roomid=6` | `code=0`，该房间当时列表为空 | 匿名可用，但不是稳定完整数据源 |
| 未签名信息流配置 | `GET /xlive/web-room/v1/index/getDanmuInfo?id=6&type=0&web_location=444.8` | `code=-352` | 当前裸请求不可用 |
| WBI + buvid 信息流配置 | `getDanmuInfo` 加 `w_rid/wts`，Cookie 带 `buvid3/buvid4` | `code=0`，返回 `token` 和 6 个 comet 节点 | 游客态可拿连接配置 |
| WebSocket 连接 | `wss://{host}/sub` | 认证回复 `{"code":0}` | WSS 可连 |
| 实时事件 | 房间 `7734200` 监听 15 秒 | 收到 `WATCHED_CHANGE`、`LIKE_INFO_V3_UPDATE`、`NOTICE_MSG` 等 | 信息流可用 |
| 实时弹幕 | 房间 `6963590`，鉴权包带 `buvid`，`protover=2`，监听 12 秒 | 解出 44 条事件，其中 `DANMU_MSG` 3 条 | 低成本实时弹幕链路成立 |

额外观察：

- `wss://{host}/sub` 默认 443 端口在当前网络环境中比 `wss://{host}:2245/sub` 更稳定。
- 鉴权包不带 `buvid` 也能认证成功，但事件更少，且容易只收到未登录提示。
- `protover=2` 在本次活跃房间测试中稳定收到 `DANMU_MSG`；`protover=3` 也能认证成功，但本次样本中没有收到普通弹幕。第一版实现建议先支持 `protover=2` 和 zlib 解压，随后再补 `protover=3` 的 Brotli 解压兼容。
- 游客态弹幕样本中既有正常 `uid/昵称`，也有 `uid=0` 和昵称打码。这符合直播信息流文档中对未登录隐私限制的描述。

## 实时弹幕方案

### 1. 解析真实房间号

```text
GET https://api.live.bilibili.com/room/v1/Room/room_init?id={roomIdOrShortId}
```

用途：

- 把短号转换为真实 `room_id`。
- 获取主播 `uid`、`live_status`、是否隐藏/锁定/加密、特殊房间标记。

建议：

- 用户输入房间号后先调用一次。
- 后续 WebSocket 鉴权使用真实 `room_id`，不要用短号。

### 2. 获取匿名设备 Cookie

```text
GET https://api.bilibili.com/x/frontend/finger/spi
```

返回：

- `data.b_3`：`buvid3`
- `data.b_4`：`buvid4`

用途：

- `getDanmuInfo` 当前要求 Cookie 中 `buvid3` 不为空。
- WebSocket 鉴权包建议带 `buvid`，值用 `buvid3`。

注意：

- 这是匿名设备标识，不是账号登录态。
- 可以按进程或短周期缓存，避免每次连接都重新获取。

### 3. WBI 签名

WBI key 从：

```text
GET https://api.bilibili.com/x/web-interface/nav
```

中读取 `data.wbi_img.img_url` 和 `data.wbi_img.sub_url`，取文件名作为 `img_key` 和 `sub_key`，按 WBI 文档生成 `w_rid` 与 `wts`。

用途：

- `getDanmuInfo` 从 2025-05-26 起强制 WBI，从 2025-06-27 起要求 `buvid3`。
- 不签名当前会返回 `-352`。

建议：

- WBI key 做内存缓存，按天或失败时刷新。
- 遇到 `-352`、`-403`、返回 `v_voucher` 时不要自动解验证码，只做降级、退避或提示。

### 4. 获取 WebSocket token 和 host

```text
GET https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo
```

参数：

| 参数 | 必要 | 说明 |
| --- | --- | --- |
| `id` | 是 | 真实直播间号 |
| `type` | 建议 | 实测用 `0` |
| `web_location` | 建议 | 实测用 `444.8` |
| `wts` | 是 | WBI 秒级时间戳 |
| `w_rid` | 是 | WBI 签名 |

请求 Cookie：

```text
buvid3={b_3}; buvid4={b_4}
```

返回核心字段：

- `data.token`：WebSocket 鉴权 key。
- `data.host_list[]`：comet 节点列表，含 `host`、`port`、`wss_port`、`ws_port`。
- `data.refresh_rate`、`max_delay` 等连接配置。

### 5. 建立 WebSocket 连接

推荐地址：

```text
wss://{host}/sub
```

备选地址：

```text
wss://broadcastlv.chat.bilibili.com/sub
```

本地资料中也列出 `wss_port=2245`，但本次实测默认 443 更稳。实现上可以优先 `wss://{host}/sub`，失败后再尝试 `wss://{host}:2245/sub` 和 `broadcastlv`。

### 6. 发送鉴权包

鉴权包是直播信息流二进制包，头部 16 字节，操作码 `7`。正文是 JSON。

建议第一版正文：

```json
{
  "uid": 0,
  "roomid": 6963590,
  "protover": 2,
  "platform": "web",
  "type": 2,
  "key": "getDanmuInfo 返回的 token",
  "buvid": "buvid3",
  "clientver": "1.14.3"
}
```

字段说明：

| 字段 | 建议 | 说明 |
| --- | --- | --- |
| `uid` | `0` | 游客态，避免账号 Cookie |
| `roomid` | 真实房间号 | 必须用真实 `room_id` |
| `protover` | `2` | 本次实测更容易拿到 `DANMU_MSG`，服务端正文使用 zlib |
| `platform` | `web` | Web 端 |
| `type` | `2` | 文档和第三方实现均使用 |
| `key` | token | 来自 `getDanmuInfo` |
| `buvid` | `buvid3` | 本地文档未列，但第三方实现和实测都表明建议带上 |
| `clientver` | 可选 | 第三方实现常见字段，保守带上即可 |

### 7. 心跳和解包

每 30 秒发送一次心跳包，操作码 `2`。服务端心跳回复操作码 `3`，正文前 4 字节可读房间人气值。

普通消息操作码 `5`，需要根据协议版本解压：

| 服务端包头协议版本 | 正文处理 | 说明 |
| --- | --- | --- |
| `0` | 直接 JSON | 未压缩普通包 |
| `1` | 直接 JSON 或心跳/鉴权相关 | 心跳及鉴权包常见 |
| `2` | zlib 解压后递归拆包 | 建议第一版支持 |
| `3` | Brotli 解压后递归拆包 | 建议第二步补齐 |

普通包可能一次包含多条命令。解析时要按每条子包头的长度递归读取，不要假设一帧只有一条 JSON。

## 可低成本获取的数据

### WebSocket 实时事件

直播信息流中可以低成本收到大量 `cmd`。对 BiliMonitor 最有价值的事件如下：

| 事件 | 可获取数据 | 价值 | 建议 |
| --- | --- | --- | --- |
| `DANMU_MSG` | 弹幕正文、发送时间、颜色、字号、模式、表情信息、部分用户和粉丝牌信息 | 核心弹幕数据 | 第一优先级 |
| `WATCHED_CHANGE` | 看过人数展示和变更 | 热度趋势 | 第一优先级 |
| `LIKE_INFO_V3_CLICK` / `LIKE_INFO_V3_UPDATE` | 点赞行为和点赞总量更新 | 互动趋势 | 第一优先级 |
| `SEND_GIFT` / `COMBO_SEND` | 礼物、数量、投喂人展示信息、价格字段 | 打赏趋势 | 第二优先级，注意用户信息最小化 |
| `SUPER_CHAT_MESSAGE` / `SUPER_CHAT_MESSAGE_DELETE` | 醒目留言内容、金额、展示时长、删除通知 | 付费互动 | 第二优先级 |
| `GUARD_BUY` / `USER_TOAST_MSG` | 上舰、庆祝消息 | 粉丝经济事件 | 第二优先级 |
| `INTERACT_WORD` / `INTERACT_WORD_V2` | 进房、关注、分享等用户交互 | 活跃度 | 可聚合，不建议全量存用户明细 |
| `ENTRY_EFFECT` | 进场特效 | 高价值用户进房信号 | 可选 |
| `ONLINE_RANK_COUNT` / `ONLINE_RANK_V2/V3` | 高能榜人数、榜单用户 | 榜单展示 | 可选，涉及用户展示信息 |
| `ROOM_REAL_TIME_MESSAGE_UPDATE` | 粉丝数、粉丝团等房间实时统计 | 房间趋势 | 第一优先级 |
| `ROOM_CHANGE` / `CHANGE_ROOM_INFO` | 标题、分区、背景等房间信息变更 | 事件审计 | 第二优先级 |
| `LIVE` / `PREPARING` | 开播、下播或准备中 | 状态事件 | 第一优先级 |
| `ROOM_SILENT_ON/OFF` / `ROOM_BLOCK_MSG` | 禁言状态和禁言事件 | 房间治理状态 | 可选 |

### HTTP 查询接口

| 接口 | 登录态 | WBI | 可获取数据 | 成本 | 建议 |
| --- | --- | --- | --- | --- | --- |
| `/room/v1/Room/room_init` | 不需要 | 不需要 | 真实房间号、主播 UID、直播状态、锁定/加密状态 | 低 | 必接 |
| `/room/v1/Room/get_info` | 不需要 | 不需要 | 标题、封面、关键帧、分区、在线/热度、热词、禁言状态 | 低 | 必接，已在直播监控中使用 |
| `/xlive/web-room/v1/index/getRoomBaseInfo` | 不需要 | 不需要 | 批量房间基础信息 | 低 | 多房间补详情 |
| `/room/v1/Room/get_status_info_by_uids` | 不需要 | 不需要 | 按主播 UID 批量查询直播状态 | 低 | 多主播轮询主入口 |
| `/xlive/web-room/v1/dM/gethistory` | 不需要 | 不需要 | 最近历史弹幕，窗口很小 | 低到中 | 只做补屏和活跃筛选 |
| `/xlive/web-room/v1/index/getDanmuInfo` | 不需要账号 | 需要 | WebSocket token 和 host | 中 | 实时弹幕必接 |
| `/xlive/web-room/v2/index/getRoomPlayInfo` | 不需要 | 不需要 | FLV/HLS/fMP4 直播流 URL | 低到中 | 仅播放/录制需要 |
| `/xlive/web-interface/v1/webMain/getMoreRecList` | 不需要 | 不需要 | 推荐直播间、在线数、封面、主播信息 | 低 | 可用于发现样本房间，不建议核心依赖 |
| `/room/v1/Area/getList` | 不需要 | 不需要 | 直播分区树 | 低 | 可缓存 |
| `/xlive/web-room/v1/giftPanel/roomGiftList` | 不需要 | 不需要 | 礼物列表、礼物图片、价格等 | 低 | 若展示礼物统计可接 |

## 不建议低成本接入的数据

| 类别 | 原因 |
| --- | --- |
| 发送弹幕 | 需要登录态和 CSRF，且会改变状态 |
| 送礼、背包、领取奖励 | 需要账号态，涉及资产或权益 |
| 房管禁言、直播管理、开播停播 | 需要账号态，且属于管理操作 |
| 关注列表、我的勋章、观看时长 | 涉及个人账号数据 |
| 直播流水、主播后台数据 | 需要主播或账号权限 |
| 自动验证码、代理规避、自动风控绕过 | 合规和账号风险明显，不应进入 BiliMonitor |

## 对 BiliMonitor 的接入建议

### 推荐架构

继续保留现有直播监控的低频 HTTP 轮询，用 WebSocket 作为直播中房间的增强链路：

1. 定时轮询 `get_status_info_by_uids` 或 `room_init/get_info`，发现直播中房间。
2. 对直播中且用户开启弹幕监控的房间建立 WebSocket。
3. 用 `DANMU_MSG`、`WATCHED_CHANGE`、`LIKE_INFO_V3_UPDATE`、`ROOM_REAL_TIME_MESSAGE_UPDATE` 等事件更新实时统计。
4. 房间下播、连接异常、连续无心跳时断开或退避重连。
5. 对未开播房间不保持 WebSocket，降低连接数和风险。

### 最小实现模块

后端可新增或扩展以下能力：

| 模块 | 职责 |
| --- | --- |
| `BilibiliWbiSigner` | 获取并缓存 WBI key，给 `getDanmuInfo` 签名 |
| `BilibiliAnonymousCookieProvider` | 低频获取和缓存 `buvid3/buvid4` |
| `BilibiliLiveDanmuInfoClient` | 调用 `getDanmuInfo`，处理 `-352/-403/-101` 等错误 |
| `BilibiliLiveMessageClient` | WebSocket 连接、鉴权、心跳、重连、二进制拆包 |
| `BilibiliLiveMessageParser` | 解析 `DANMU_MSG` 和常用 `cmd` |
| `BilibiliLiveEventAggregator` | 将弹幕、点赞、看过人数、礼物等聚合为趋势点 |

第一版可以只落地：

- `DANMU_MSG` 数量和最近弹幕。
- `WATCHED_CHANGE` 看过人数。
- `LIKE_INFO_V3_UPDATE` 点赞总量。
- `ROOM_REAL_TIME_MESSAGE_UPDATE` 房间实时统计。
- `LIVE/PREPARING` 状态事件。

礼物、SC、上舰和榜单可以第二阶段再补。

### 存储建议

默认不建议无差别永久保存完整弹幕用户信息。更稳妥的方案：

| 数据 | 建议 |
| --- | --- |
| 弹幕正文 | 若产品需要回看，可保存最近窗口或按用户配置保存；默认设置保留期 |
| 弹幕发送者 UID/昵称 | 游客态本就可能缺失；默认不作为强依赖字段 |
| 事件原始 JSON | 调试期短期保存，生产期只保存必要字段 |
| 礼物、SC、上舰 | 保存聚合值和必要展示字段，避免过度采集用户明细 |
| 连接日志 | 保存连接状态、错误码、重试次数，不保存 token 和 Cookie |

### 并发和退避

| 场景 | 建议 |
| --- | --- |
| 多房间监控 | 只给直播中房间开 WebSocket，设置全局连接上限 |
| 连接失败 | host 轮换，指数退避，避免短时间反复重连 |
| `getDanmuInfo` `-352` | 刷新 WBI key 和 buvid 后最多重试一次 |
| `-101` | 视为需要登录或接口不可匿名，直接降级 |
| 心跳超时 | 断开后按退避重连 |
| 事件过多 | 先聚合再入库，弹幕正文走限速队列 |

## 风险评估

| 风险 | 等级 | 说明 | 缓解 |
| --- | --- | --- | --- |
| 接口变更 | 中 | B站接口字段和风控条件会变化 | 封装错误码、保留降级路径 |
| WBI 失效 | 中 | key 可能每日变化，签名失败会 `-352/-403` | key 缓存和失败刷新 |
| 匿名隐私限制 | 中 | 游客态无法稳定拿完整 UID/昵称 | 产品上不承诺完整用户身份 |
| 长连接成本 | 中 | 每个直播中房间占一个连接 | 连接上限和只连直播中房间 |
| 数据合规 | 中高 | 弹幕包含用户生成内容和用户展示信息 | 最小化采集、保留期、不开账号态 |
| 账号 Cookie | 高 | `SESSDATA` 泄露风险高 | 当前方案不需要账号 Cookie |
| 官方政策 | 中高 | 本地资料库明确不是官方开放平台 | 商业或规模化场景优先评估官方直播开放平台 |

## 推荐路线图

### 第 1 阶段：验证型接入

- 新增 WBI 签名和匿名 buvid 获取。
- 实现单房间 WebSocket 连接、鉴权、心跳、zlib 解压。
- 只解析 `DANMU_MSG`、`WATCHED_CHANGE`、`LIKE_INFO_V3_UPDATE`、`ROOM_REAL_TIME_MESSAGE_UPDATE`。
- 在后端日志或调试接口输出聚合结果，不急着全量入库。

验收标准：

- 对一个直播中房间持续监听 10 分钟不掉线。
- 心跳人气值和 `WATCHED_CHANGE` 正常更新。
- 活跃房间能收到 `DANMU_MSG`。
- `getDanmuInfo` 裸请求失败时能通过 WBI + buvid 恢复。

### 第 2 阶段：产品化

- 将弹幕数量、点赞、看过人数、礼物数量聚合到趋势表。
- UI 增加直播中实时指标和最近弹幕窗口。
- 加连接数上限、重连退避、房间下播自动断开。
- 增加数据保留期配置。

### 第 3 阶段：扩展事件

- 补 `SEND_GIFT`、`COMBO_SEND`、`SUPER_CHAT_MESSAGE`、`GUARD_BUY`。
- 支持 `protover=3` 的 Brotli 解压。
- 做事件 schema 版本化，兼容 B站字段变化。

不建议阶段：

- 不接入发送弹幕、送礼、管理类接口。
- 不接入自动验证码和账号 Cookie 刷新。
- 不以游客态数据做完整用户身份分析。

## 资料索引

本地资料：

- 直播信息流协议：[`../bilibili-api-collect-new-research/repo/docs/live/message_stream.md`](../bilibili-api-collect-new-research/repo/docs/live/message_stream.md)
- 直播间弹幕 HTTP 接口：[`../bilibili-api-collect-new-research/repo/docs/live/danmaku.md`](../bilibili-api-collect-new-research/repo/docs/live/danmaku.md)
- 直播间基本信息接口：[`../bilibili-api-collect-new-research/repo/docs/live/info.md`](../bilibili-api-collect-new-research/repo/docs/live/info.md)
- WBI 签名：[`../bilibili-api-collect-new-research/repo/docs/misc/sign/wbi.md`](../bilibili-api-collect-new-research/repo/docs/misc/sign/wbi.md)
- buvid 获取：[`../bilibili-api-collect-new-research/repo/docs/misc/buvid3_4.md`](../bilibili-api-collect-new-research/repo/docs/misc/buvid3_4.md)
- 直播间接口总览：[`bilibili-live-room-api-research.md`](bilibili-live-room-api-research.md)
- SDK 风控研究：[`bilibili-api-libraries-risk-control-research.md`](bilibili-api-libraries-risk-control-research.md)

公开资料：

- Bilibili 直播开放平台文档：<https://open-live.bilibili.com/document/>
- `bilibili-live-danmaku` WebSocket API 说明：<https://github.com/Minteea/bilibili-live-danmaku>
