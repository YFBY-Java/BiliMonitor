# B站直播间接口研究报告

最后更新：2026-06-11  
资料来源：`../bilibili-api-collect-new-research/repo/docs/live/` 与少量匿名实测。  
实测方式：使用普通浏览器 `User-Agent` 和 `Referer`，不携带账号 Cookie，不做批量高频请求，不调用会改变状态的接口。

## 结论摘要

对 `BiliMonitor` 最有价值、当前匿名可用且风控较低的接口是：

1. `GET /room/v1/Room/get_status_info_by_uids`  
   批量按主播 UID 查询直播状态，适合作为监控多个主播是否开播的主入口。
2. `GET /room/v1/Room/room_init`  
   用房间短号/长号解析真实房间号、主播 UID、直播状态，适合作为房间号规范化入口。
3. `GET /room/v1/Room/get_info` 与 `GET /xlive/web-room/v1/index/getRoomBaseInfo`  
   查询直播间标题、封面、分区、开播状态、在线/热度、主播 UID 等详情。
4. `GET /xlive/web-room/v2/index/getRoomPlayInfo`  
   匿名可拿播放流信息，但只有需要播放/录制时才建议接入，普通监控不需要。
5. `GET /xlive/web-room/v1/dM/gethistory`  
   匿名可拿最近历史弹幕，数据敏感度高于房间状态，只建议低频、按需使用。
6. `GET /xlive/web-room/v1/index/getDanmuInfo` + WebSocket/WSS  
   可监听实时弹幕、礼物、开播/下播、房间变更等事件，但需要 WBI 参数，裸请求会被 `-352` 拦截，属于中等请求难度。

不建议作为第一阶段接入的接口：

- 关注列表、我的勋章、观看时长、投票、回放、直播流水、直播管理、禁言管理、发送弹幕等：需要 `SESSDATA`，很多还需要 `bili_jct`/`csrf`，其中一部分会改变账号或直播间状态。
- `getLotteryInfoWeb` 人气红包：文档标 Cookie 可选，但 2026-06-11 匿名、WBI、匿名设备 Cookie、WBI+设备 Cookie 实测均返回 `-352`。
- `blindFirstWin/getInfo` 盲盒概率：文档标无 Cookie，但 2026-06-11 匿名实测返回 `-101 账号未登录`。

## 风控分级

| 等级 | 判定 | 接入建议 |
| --- | --- | --- |
| 低 | 无 Cookie、无 WBI、GET 查询、匿名实测 `code=0` | 优先接入，控制频率即可。 |
| 中 | 无账号登录，但需要 WBI、长连接、或字段受匿名隐私限制 | 可接入，但要封装签名、退避、重连和隐私处理。 |
| 中高 | 匿名可查，但返回用户 UID、昵称、头像、粉丝牌、财富等级等用户级数据 | 只在产品确实需要时接入，避免无关存储。 |
| 高 | 需要 `SESSDATA`、`bili_jct`/`csrf`、APP 签名，或会改变状态 | 当前项目不建议接入。 |
| 不推荐 | 文档和当前实测不一致，或匿名稳定性差 | 暂不依赖，后续重新研究。 |

## 优先接口详表

### 1. 批量查询直播状态

```text
GET https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids
POST https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids
```

来源：`repo/docs/live/info.md`。  
风控：低。匿名实测 `code=0`。  
请求难度：低。

参数：

| 参数 | 必要 | 说明 |
| --- | --- | --- |
| `uids[]` | 是 | 主播 UID。GET 查询串和 POST 表单都支持重复参数，如 `uids[]=1&uids[]=2`。 |

实测注意：

- GET 重复 `uids[]` 可返回多个 UID 的 map。
- POST 表单重复 `uids[]` 可用。
- POST 逗号字符串 `uids=1,2,3` 实测 `invalid params`，不要用。

可获取数据：

- `live_status`：`0` 未开播，`1` 直播中，`2` 轮播中。
- `room_id`、`short_id`、`uid`。
- `title`、`uname`、`face`。
- `online`、`live_time`。
- 分区：`area_v2_id`、`area_v2_name`、`area_v2_parent_name`。
- 封面/关键帧：`cover_from_user`、`keyframe`。

推荐用途：已知一批 UID 时，作为直播状态轮询主接口。

### 2. 房间初始化和房间号规范化

```text
GET https://api.live.bilibili.com/room/v1/Room/room_init
```

来源：`repo/docs/live/info.md`。  
风控：低。匿名实测 `code=0`。  
请求难度：低。

参数：

| 参数 | 必要 | 说明 |
| --- | --- | --- |
| `id` | 是 | 房间号，可为短号。 |

可获取数据：

- `room_id`：真实直播间号。
- `short_id`：短号，没有短号时为 `0`。
- `uid`：主播 UID。
- `live_status`、`live_time`。
- `is_hidden`、`is_locked`、`encrypted`、`pwd_verified`。
- `special_type`、`is_sp`。

推荐用途：用户输入房间号后，先调用它解析真实 `room_id` 和 `uid`。

### 3. 单房间详情

```text
GET https://api.live.bilibili.com/room/v1/Room/get_info
```

来源：`repo/docs/live/info.md`。  
风控：低。匿名实测 `code=0`。  
请求难度：低。

参数：

| 参数 | 必要 | 说明 |
| --- | --- | --- |
| `room_id` | 是 | 直播间号，可为短号。 |

可获取数据：

- 主播和房间：`uid`、`room_id`、`short_id`、`title`、`description`。
- 状态：`live_status`、`live_time`。
- 热度/观看：`online`、`attention`。
- 分区：`area_id`、`area_name`、`parent_area_id`、`parent_area_name`。
- 视觉资源：`user_cover`、`keyframe`、`background`。
- 禁言状态：`room_silent_type`、`room_silent_level`、`room_silent_second`。
- 其它：`hot_words`、`tags`、`pk_status`。

推荐用途：单房间详情页或添加房间时补全元数据。

### 4. 批量房间基础信息

```text
GET https://api.live.bilibili.com/xlive/web-room/v1/index/getRoomBaseInfo
```

来源：`repo/docs/live/info.md`。  
风控：低。匿名实测 `code=0`。  
请求难度：低。

参数：

| 参数 | 必要 | 说明 |
| --- | --- | --- |
| `req_biz` | 是 | 文档值为 `web_room_componet`，注意原值拼写就是 `componet`。 |
| `room_ids` | 否 | 可重复传多个房间号，如 `room_ids=1&room_ids=2`。 |

可获取数据：

- `by_room_ids` map。
- `room_id`、`uid`、`short_id`。
- `title`、`uname`、`description`。
- `live_status`、`live_time`。
- `online`、`attention`。
- `area_id`、`area_name`、`parent_area_name`。
- `cover`、`background`、`live_url`。

推荐用途：已知多个房间号时，批量补详情。

### 5. 按 UID 查询旧房间状态

```text
GET https://api.live.bilibili.com/room/v1/Room/getRoomInfoOld
```

来源：`repo/docs/live/info.md`。  
风控：低。匿名实测 `code=0`。  
请求难度：低。

参数：

| 参数 | 必要 | 说明 |
| --- | --- | --- |
| `mid` | 是 | 目标主播 UID。 |

可获取数据：

- `roomStatus`：是否有直播间。
- `liveStatus`/`live_status`：是否直播中。
- `url`、`title`、`cover`。
- `online`、`roomid`。

推荐用途：单个 UID 的轻量状态兜底。批量场景优先用 `get_status_info_by_uids`。

### 6. 主播资料

```text
GET https://api.live.bilibili.com/live_user/v1/Master/info
GET https://api.live.bilibili.com/live_user/v1/UserInfo/get_anchor_in_room
```

来源：`repo/docs/live/info.md`。  
风控：低。匿名实测均 `code=0`。  
请求难度：低。

参数：

| 接口 | 参数 | 说明 |
| --- | --- | --- |
| `Master/info` | `uid` | 主播 UID。 |
| `get_anchor_in_room` | `roomid` | 房间号，可为短号。 |

可获取数据：

- 主播昵称、头像、认证信息、性别。
- `follower_num`、`medal_name`、主播等级。
- `room_id`、`room_news`。
- `san`、平台等级、直播等级。

推荐用途：主播展示信息补充。状态监控不必每次轮询。

### 7. 直播推荐与分区

```text
GET https://api.live.bilibili.com/xlive/web-interface/v1/webMain/getMoreRecList
GET https://api.live.bilibili.com/room/v1/Area/getList
```

来源：`repo/docs/live/recommend.md`、`repo/docs/live/live_area.md`。  
风控：低。匿名实测均 `code=0`。  
请求难度：低。

参数：

| 接口 | 参数 | 说明 |
| --- | --- | --- |
| `getMoreRecList` | `platform` | 必要，`web` 可用。 |
| `Area/getList` | 无 | 返回父分区和子分区。 |

推荐接口可获取：

- 推荐房间列表：`roomid`、`uid`、`uname`、`title`、`online`。
- 分区：`area_v2_id`、`area_v2_name`、`area_v2_parent_name`。
- 资源：`cover`、`face`、`keyframe`。
- 展示相关：`watched_show`、`session_id`、回调 URL。

分区接口可获取：

- 父分区 `id/name`。
- 子分区 `id/parent_id/name/pic/hot_status`。

推荐用途：

- `Area/getList` 可缓存，做分区字典。
- `getMoreRecList` 可用于发现样本或演示，不适合当作全站直播发现接口。

### 8. 播放流信息

新接口：

```text
GET https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo
```

旧接口：

```text
GET https://api.live.bilibili.com/room/v1/Room/playUrl
```

来源：`repo/docs/live/info.md`、`repo/docs/live/live_stream.md`。  
风控：低到中。匿名实测均 `code=0`，但播放 URL 有时效和使用场景限制。  
请求难度：中。

新接口参数：

| 参数 | 必要 | 说明 |
| --- | --- | --- |
| `room_id` | 是 | 直播间号。 |
| `protocol` | 是 | `0` http_stream，`1` http_hls，可逗号多选。 |
| `format` | 是 | `0` flv，`1` ts，`2` fmp4，可逗号多选。 |
| `codec` | 是 | `0` AVC，`1` HEVC，可逗号多选。 |
| `qn` | 否 | 清晰度，如 `10000` 原画、`400` 蓝光、`150` 高清。 |
| `platform` | 否 | `web`。 |
| `only_audio` | 否 | `1` 为音频流。 |

可获取数据：

- 房间状态：`live_status`、`live_time`、`encrypted`、`is_locked`。
- `playurl_info.playurl.g_qn_desc`：清晰度列表。
- `stream[].format[].codec[]`：协议、格式、编码。
- `base_url`、`url_info[].host`、`url_info[].extra`、`stream_ttl`。
- `risk_with_delay`、`degraded_playurl` 等播放侧字段。

推荐用途：只有需要播放、录制、截图或流可用性检测时才接入。普通直播状态监控不要请求播放 URL。

### 9. 弹幕配置与最近历史弹幕

```text
GET https://api.live.bilibili.com/xlive/web-room/v1/dM/GetDMConfigByGroup
GET https://api.live.bilibili.com/xlive/web-room/v1/dM/gethistory
```

来源：`repo/docs/live/danmaku.md`。  
风控：低到中。匿名实测均 `code=0`。  
请求难度：低。

参数：

| 接口 | 参数 | 说明 |
| --- | --- | --- |
| `GetDMConfigByGroup` | `room_id` | 房间号。`w_rid/wts` 文档标可选，当前不强制。 |
| `gethistory` | `roomid` | 房间号；`room_type=0` 可带。 |

可获取数据：

- 弹幕配置：颜色组、颜色值、模式、可用状态。未登录时通常只有白色和滚动可用。
- 最近历史弹幕：`admin` 和 `room` 数组，实测各 10 条。
- 历史弹幕字段包括：文本、发送者 UID/昵称、发送时间、粉丝牌、用户等级、表情、回复信息等。

推荐用途：

- `GetDMConfigByGroup` 对监控价值有限。
- `gethistory` 可用于直播间最近互动快照，但涉及用户数据，建议只按需展示，不做长期无关存储。

### 10. 实时信息流

认证秘钥接口：

```text
GET https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo
```

WebSocket/WSS 地址来自 `host_list`，路径为 `/sub`。  
来源：`repo/docs/live/message_stream.md`、`repo/docs/misc/sign/wbi.md`、`repo/docs/misc/buvid3_4.md`。  
风控：中。  
请求难度：中到高。

参数：

| 参数 | 必要 | 说明 |
| --- | --- | --- |
| `id` | 是 | 直播间真实 ID，建议先用 `room_init` 解析。 |
| `type` | 否 | 通常传 `0`。 |
| `web_location` | 否 | 作用不明确。 |
| `w_rid` | 当前必要 | WBI 签名结果。 |
| `wts` | 当前必要 | 秒级时间戳。 |

2026-06-11 实测：

| 请求形态 | 结果 |
| --- | --- |
| 裸请求，无 WBI | `code=-352` |
| 仅匿名设备 Cookie，无 WBI | `code=-352` |
| WBI，无 Cookie | `code=0`，返回 `token` 和 6 个 host |
| WBI + 匿名设备 Cookie | `code=0`，返回 `token` 和 6 个 host |

可获取数据：

- `token`：长连接认证用。
- `host_list`：`host`、`port`、`ws_port`、`wss_port`。
- 长连接心跳回复可得房间人气值。
- 普通包事件非常多，监控最有用的是：
  - `LIVE`：直播开始。
  - `PREPARING`、`STOP_LIVE_ROOM_LIST`：下播/准备中。
  - `ROOM_CHANGE`、`ROOM_REAL_TIME_MESSAGE_UPDATE`、`CHANGE_ROOM_INFO`：房间信息变更。
  - `DANMU_MSG`：弹幕。
  - `SEND_GIFT`、`COMBO_SEND`、`GUARD_BUY`、`SUPER_CHAT_MESSAGE`：礼物/上舰/醒目留言。
  - `WATCHED_CHANGE`、`LIKE_INFO_V3_UPDATE`：看过人数/点赞数。
  - `ONLINE_RANK_*`、`POPULAR_RANK_CHANGED`、`HOT_RANK_CHANGED*`：榜单变化。
  - `PLAYURL_RELOAD`：播放链接刷新。

匿名限制：

- 文档说明未登录连接会收到隐私提示，部分弹幕和用户交互消息的 `mid` 变为 `0`，昵称可能被 `*` 保护。
- 因此，如果产品目标是公开直播状态和热度，匿名连接够用；如果目标是精确用户识别，不应绕过隐私限制。

推荐用途：

- 第二阶段实时监控能力：开播/下播即时通知、弹幕/礼物事件流。
- 实现时需要：WBI 封装、token 缓存、WSS 连接、二进制包解析、brotli/zlib 解压、30 秒心跳、断线退避。

### 11. 礼物和榜单

礼物面板：

```text
GET https://api.live.bilibili.com/xlive/web-room/v1/giftPanel/roomGiftList
```

来源：`repo/docs/live/gift.md`。  
风控：低。匿名实测 `platform=pc` 返回 `code=0`。  
请求难度：低。

参数：

| 参数 | 必要 | 说明 |
| --- | --- | --- |
| `platform` | 是 | 实测 `pc` 返回基础礼物列表；`web` 虽 `code=0` 但基础列表为空。 |
| `room_id` | 是 | 房间号。 |
| `area_parent_id` | 否 | 父分区，不填可能缺少活动礼物。 |
| `area_id` | 否 | 子分区，不填可能缺少活动礼物。 |

可获取数据：

- `gift_config.base_config.list`。
- 礼物 `id/name/price/coin_type/effect/desc/img_basic/gif`。

榜单接口：

```text
GET https://api.live.bilibili.com/xlive/app-room/v2/guardTab/topListNew
GET https://api.live.bilibili.com/xlive/general-interface/v1/rank/getFansMembersRank
GET https://api.live.bilibili.com/xlive/general-interface/v1/rank/getOnlineGoldRank
GET https://api.live.bilibili.com/xlive/app-ucenter/v2/card/user
```

来源：`repo/docs/live/guard.md`、`repo/docs/live/user.md`。  
风控：中高。匿名实测 `code=0`。  
请求难度：低，但数据敏感度高。

参数和数据：

| 接口 | 参数 | 数据 |
| --- | --- | --- |
| `topListNew` | `roomid`、`ruid`、`page`、`page_size`、`typ` | 大航海成员、排名、陪伴天数、用户信息、粉丝牌。 |
| `getFansMembersRank` | `ruid`、`page`、`page_size`、`rank_type`、`ts` | 粉丝团成员、亲密度、粉丝牌、头像。 |
| `getOnlineGoldRank` | `roomId`、`ruid`、`page`、`pageSize` | 在线贡献榜、贡献值、用户信息、荣耀等级。 |
| `card/user` | `uid`、`ruid` | 用户在直播间的卡片信息、粉丝数、关注数、勋章、财富等级。 |

推荐用途：不是直播状态监控必需能力。若后续做直播间画像或榜单分析，需要单独做隐私和存储边界设计。

## 当前不推荐接口

| 接口 | 文档认证 | 2026-06-11 匿名实测 | 判断 |
| --- | --- | --- | --- |
| `xlive/lottery-interface/v1/lottery/getLotteryInfoWeb` | Cookie 可选 | 裸请求、WBI、设备 Cookie、WBI+设备 Cookie 均 `-352` | 当前风控较高，不作为 REST 入口。若已接入信息流，可关注红包相关事件。 |
| `xlive/general-interface/v1/blindFirstWin/getInfo` | 无 Cookie | `-101 账号未登录` | 当前不按文档匿名开放。 |
| `xlive/web-ucenter/user/following` | `SESSDATA` | `-101 账号未登录` | 登录态接口，不适合公开监控。 |
| `xlive/web-ucenter/v1/xfetter/GetWebList` | `SESSDATA` | `-101 账号未登录` | 登录态接口。已知 UID 场景用批量状态接口替代。 |
| `xlive/app-ucenter/v1/user/GetMyMedals` | `SESSDATA` 或 APP | `-101 账号未登录` | 个人账号数据，不接入。 |
| `xlive/web-ucenter/v2/emoticon/GetEmoticons` | `SESSDATA` | `-101 账号未登录` | 表情包不是监控核心。 |
| `xlive/general-interface/v1/guard/GuardActive` | `SESSDATA` 或 APP | `-101 账号未登录` | 查询自己的观看时长，不接入。 |
| `xlive/app-room/v1/dm/interaction/votePanel` | `SESSDATA` | `-101 账号未登录` | 主播侧/登录态投票数据，不接入。 |

## 高风险或状态变更类接口

这些接口只做文档归类，不做实测：

- 发送直播弹幕：`POST https://api.live.bilibili.com/msg/send`  
  需要 `SESSDATA`、`bili_jct`/`csrf`，有频率限制。
- 设置弹幕样式：`POST /xlive/web-room/v1/dM/AjaxSetConfig`  
  需要登录和 CSRF。
- 直播间管理：开通、更新信息、开始直播、关闭直播、更新公告等。  
  需要登录和 CSRF，部分涉及 APP 签名、直播姬版本、推流密钥。
- 禁言管理：添加禁言、查询禁言列表、解除禁言。  
  需要登录和 CSRF，并涉及房管/主播权限。
- 直播流水、直播数据、直播回放、直播投票。  
  主要是主播后台、授权、个人账号或操作型接口。
- 直播心跳上报：`live-trace.../webHeartBeat`。  
  虽然文档显示 GET 可用，但它是观看心跳上报，不是单纯读取接口。普通监控不需要模拟观看心跳。

## 推荐接入路线

第一阶段：低风控状态监控。

1. 用户添加 UID：调用 `get_status_info_by_uids` 获取是否有房间、直播状态、房间号、标题、封面。
2. 用户添加房间号：调用 `room_init` 解析真实房间号和 UID，再纳入 UID 维度监控。
3. 周期轮询：批量 `get_status_info_by_uids`。按 UID 分组，控制批大小和间隔。
4. 详情补全：必要时用 `getRoomBaseInfo` 批量按房间号补标题、封面、分区、在线数。
5. 分区字典：启动时或每天缓存一次 `Area/getList`。

第二阶段：实时事件。

1. 对少量重点房间使用 `room_init` 得到真实 `room_id`。
2. 调 `getDanmuInfo`，带 WBI 参数获取 token 和 host。
3. 建立 WSS `/sub` 长连接。
4. 发送认证包，`uid=0` 匿名即可。
5. 每 30 秒心跳，解析人气值和普通事件包。
6. 只消费必要事件：开播/下播、房间变更、弹幕计数、礼物计数、点赞/看过人数。

第三阶段：谨慎扩展。

- 播放流检测：仅在需要直播截图、录制或可播放性检测时接入 `getRoomPlayInfo`。
- 榜单/用户画像：单独设计数据最小化策略，不默认存储用户级明细。
- 登录态功能：当前不建议。除非产品明确需要用户自己的关注列表或主播后台数据，否则不要引入 `SESSDATA`。

## 实测记录

测试日期：2026-06-11。  
测试环境：匿名请求，普通浏览器 UA，`Referer: https://live.bilibili.com/` 或 `https://www.bilibili.com/`。  
样本：从 `getMoreRecList?platform=web&page=1` 取当前推荐直播间作为房间样本。实时推荐会变化，以下只记录返回行为。

| 接口 | 匿名结果 | 备注 |
| --- | --- | --- |
| `getMoreRecList` | `code=0` | 返回 12 个推荐房间。 |
| `Area/getList` | `code=0` | 返回 11 个父分区。 |
| `room_init` | `code=0` | 返回真实房间号、UID、直播状态。 |
| `Room/get_info` | `code=0` | 返回标题、分区、封面、在线、禁言状态等。 |
| `getRoomBaseInfo` | `code=0` | 重复 `room_ids` 可批量返回。 |
| `get_status_info_by_uids` | `code=0` | GET/POST 重复 `uids[]` 可批量返回。 |
| `getRoomInfoOld` | `code=0` | 单 UID 轻量状态。 |
| `Master/info` | `code=0` | 主播资料。 |
| `get_anchor_in_room` | `code=0` | 主播资料和等级。 |
| `getRoomPlayInfo` | `code=0` | 返回多协议播放流。 |
| `Room/playUrl` | `code=0` | 旧播放流接口，返回 `durl`。 |
| `GetDMConfigByGroup` | `code=0` | 未登录配置受限。 |
| `gethistory` | `code=0` | 返回 `admin=10`、`room=10`。 |
| `roomGiftList?platform=pc` | `code=0` | 返回基础礼物列表；`platform=web` 基础礼物为空。 |
| `topListNew` | `code=0` | 返回大航海列表。 |
| `getFansMembersRank` | `code=0` | 返回粉丝团数据结构。 |
| `getOnlineGoldRank` | `code=0` | 返回贡献榜数据结构。 |
| `card/user` | `code=0` | 返回用户在直播间卡片。 |
| `getDanmuInfo` 裸请求 | `code=-352` | 需要 WBI。 |
| `getDanmuInfo` WBI | `code=0` | 返回 token 和 6 个 host。 |
| `getLotteryInfoWeb` | `code=-352` | WBI 和匿名设备 Cookie 均未解决。 |
| `blindFirstWin/getInfo` | `code=-101` | 当前需要登录。 |
| 关注列表/我的勋章/表情包/投票/观看时长 | `code=-101` | 当前需要登录。 |

## 合规与实现边界

当前项目建议坚持：

- 只用公开匿名查询接口做直播状态监控。
- 不抓取、保存或要求用户提供 `SESSDATA`、`bili_jct`、`access_key`。
- 不实现验证码绕过、登录态抓取、风控规避或高频扫描。
- 对 WBI 类接口仅作为正常 Web 请求参数维护，不把它当成绕过机制。
- 对弹幕、榜单、贡献榜等用户级数据做最小化处理，默认不长期保存用户明细。
- 对所有接口做业务码判断、指数退避和频率限制；遇到 `-352`、`-101`、`-111`、`1002002` 等不要持续重试。

## 参考资料

- `../bilibili-api-collect-new-research/repo/docs/live/info.md`
- `../bilibili-api-collect-new-research/repo/docs/live/message_stream.md`
- `../bilibili-api-collect-new-research/repo/docs/live/danmaku.md`
- `../bilibili-api-collect-new-research/repo/docs/live/live_stream.md`
- `../bilibili-api-collect-new-research/repo/docs/live/recommend.md`
- `../bilibili-api-collect-new-research/repo/docs/live/live_area.md`
- `../bilibili-api-collect-new-research/repo/docs/live/gift.md`
- `../bilibili-api-collect-new-research/repo/docs/live/guard.md`
- `../bilibili-api-collect-new-research/repo/docs/live/user.md`
- `../bilibili-api-collect-new-research/repo/docs/live/follow_up_live.md`
- `../bilibili-api-collect-new-research/repo/docs/live/redpocket.md`
- `../bilibili-api-collect-new-research/repo/docs/live/manage.md`
- `../bilibili-api-collect-new-research/repo/docs/live/silent_user_manage.md`
- `../bilibili-api-collect-new-research/repo/docs/live/live_replay.md`
- `../bilibili-api-collect-new-research/repo/docs/live/live_vote.md`
- `../bilibili-api-collect-new-research/repo/docs/misc/sign/wbi.md`
- `../bilibili-api-collect-new-research/repo/docs/misc/buvid3_4.md`
