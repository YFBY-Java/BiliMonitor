# B站接口研究记录

最后更新：2026-06-09

详细研究文档已经沉淀在 [`../social-data-monitor/docs/bilibili-follower-api-research.md`](../social-data-monitor/docs/bilibili-follower-api-research.md)。本文件是面向交接的压缩版。

## 已阅读的接口资料

来自 [`../bilibili-api-collect-new-research/`](../bilibili-api-collect-new-research/)：

- [`repo/docs/user/info.md`](../bilibili-api-collect-new-research/repo/docs/user/info.md)：用户名片、空间资料、账号信息等。
- [`repo/docs/user/status_number.md`](../bilibili-api-collect-new-research/repo/docs/user/status_number.md)：粉丝数、关注数等状态数。
- [`repo/docs/user/relation.md`](../bilibili-api-collect-new-research/repo/docs/user/relation.md)：关注、粉丝列表和关系状态。
- [`repo/docs/user/space.md`](../bilibili-api-collect-new-research/repo/docs/user/space.md)：用户空间内容、置顶视频等。
- [`repo/docs/dynamic/space.md`](../bilibili-api-collect-new-research/repo/docs/dynamic/space.md)：空间动态流。
- [`repo/docs/dynamic/basicInfo.md`](../bilibili-api-collect-new-research/repo/docs/dynamic/basicInfo.md)：动态基础信息。
- [`repo/docs/creativecenter/statistics&data.md`](../bilibili-api-collect-new-research/repo/docs/creativecenter/statistics&data.md)：创作中心统计类接口，涉及粉丝统计但偏登录态和创作者后台。

## 候选接口对比

| 用途 | URL | 参数 | Cookie / 登录 | WBI / CSRF / buvid / Referer | 关键字段 | 判断 |
| --- | --- | --- | --- | --- | --- | --- |
| 用户名片、粉丝数、头像 | `https://api.bilibili.com/x/web-interface/card` | `mid`，可带 `photo=true` | 未发现强制登录要求 | 不需要 WBI/CSRF/buvid；代码保留普通 `User-Agent` 和 `Referer` | `data.card.name`、`data.card.face`、`data.follower`、`data.card.fans` | 主选。一次请求拿到监控所需核心字段，风控复杂度最低。 |
| 关注/粉丝状态数 | `https://api.bilibili.com/x/relation/stat` | `vmid` | 文档有 Cookie 或 APP 说明，但公开粉丝数可读 | 不需要 WBI/CSRF | `data.follower`、`data.following` | 备选兜底。只返回数字，不返回昵称头像。 |
| 用户空间详细信息 | `https://api.bilibili.com/x/space/wbi/acc/info` | `mid`、`w_rid`、`wts` | 通常需要 Cookie，尤其 `SESSDATA` | 需要 WBI 签名 | `data.name`、`data.face` | 不采用。资料完整但签名和登录态复杂，不适合无登录长期监控。 |
| 批量用户名片 | `https://api.bilibili.com/x/polymer/pc-electron/v1/user/cards` | `uids` | 文档显示可返回公开资料 | 客户端接口形态，长期稳定性不如 Web card | `{mid}.name`、`{mid}.face` | 暂不采用。可作为未来批量补资料候选。 |
| 粉丝列表旧接口 | `https://api.bilibili.com/x/relation/followers` | `vmid`、`pn`、`ps` | 需要 Cookie 或 APP 场景更多 | 可能需要 Referer，且受隐私和数量限制 | `data.total` | 不采用。目标是总粉丝趋势，不需要拉粉丝列表。 |
| 空间动态流 | `https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space` | `host_mid` 等 | 未登录常依赖 buvid，登录依赖 SESSDATA | 涉及 WBI 和 `dm_img` 系列参数 | 动态作者对象可能有 `name`、`face` | 不采用。字段不直接匹配粉丝数，风控和参数成本高。 |
| 空间置顶视频 | `https://api.bilibili.com/x/space/top/arc` | `vmid` | 未发现强制登录 | 不需要 WBI/CSRF | `data.owner.name`、`data.owner.face` | 不采用。只有有置顶视频时才适合，覆盖不完整。 |
| 创作中心统计 | 多个 `member.bilibili.com` 统计接口 | 创作者后台参数 | 需要登录态 | 可能涉及 CSRF / Cookie | `total_fans`、`incr_fans` 等 | 不采用。属于登录后的创作者数据，不适合公开监控。 |

## 最终选型

粉丝数主接口：

```text
GET https://api.bilibili.com/x/web-interface/card?mid={mid}&photo=true
```

选择理由：

- 能同时返回 UID、昵称、头像、粉丝数、关注数和卡片信息。
- 当前不需要登录态、CSRF、WBI、buvid 或验证码流程。
- 面向 Web 公开用户卡片，长期维护成本低。
- 字段直接，解析逻辑简单。
- 适合低频或受控频率轮询。

头像和基础资料接口：

```text
GET https://api.bilibili.com/x/web-interface/card?mid={mid}&photo=true
```

选择理由：

- 与粉丝数主接口相同，避免为头像再引入第二套风控面。
- 昵称字段：`data.card.name`。
- 头像字段：`data.card.face`。
- 粉丝数字段：优先 `data.follower`，兼容 `data.card.fans`。

粉丝数兜底接口：

```text
GET https://api.bilibili.com/x/relation/stat?vmid={mid}
```

使用边界：

- 只在已有监控用户刷新时，且 card 接口出现解析、网络或服务端错误时尝试。
- 只补粉丝数和关注数。
- 不补昵称、头像。
- 如果 card 遇到风险控制、鉴权要求、限流等，不应继续高频重试。

## 当前代码封装

接口调用集中在 [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/client/BilibiliApiClient.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/client/BilibiliApiClient.java)：

- `fetchUserCard(Long mid)`：主接口。
- `fetchRelationStat(Long mid, BilibiliMonitoredUser existingUser)`：兜底接口。
- `execute(...)`：统一请求、耗时记录、HTTP 错误归类、B站业务码归类。

业务策略集中在 [`../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/service/BilibiliFollowerMonitorService.java`](../social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/service/BilibiliFollowerMonitorService.java)：

- 添加用户时必须使用 card 获取资料。
- 定时采集优先 card。
- 对部分可重试错误做兜底和退避。
- 对短间隔写日志提醒。

## 合规边界

当前项目不实现：

- 验证码绕过。
- WBI 签名规避。
- Cookie、登录态或 SESSDATA 抓取。
- buvid 或浏览器指纹构造。
- 高频扫描大量用户。

如果后续公开 card 接口变得不可用，应先重新研究本地接口文档和官方可公开访问方式，再调整功能策略。

