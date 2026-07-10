# Bilibili 粉丝数接口研究

本功能只使用公开、无登录态或风控最简单的查询能力；不实现验证码、复杂风控、登录态限制或平台安全机制绕过。采集间隔允许配置到 1 秒，但仍通过全局请求间隔、失败退避和 UI 提示避免无控制高频请求。

| 接口 | URL | 参数 | 登录 / Cookie | WBI / CSRF / buvid / Referer | 粉丝数字段 | 稳定性与风控 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 用户名片信息 | `https://api.bilibili.com/x/web-interface/card` | `mid` 必填，`photo=true` 可取头图 | 文档未要求；实测未登录可返回公开资料 | 不需要 WBI/CSRF；使用普通 `User-Agent` 和 `Referer: https://www.bilibili.com/` | `data.follower`，同时 `data.card.fans` | Web 公开卡片接口，字段直接含昵称、头像、粉丝数；低频轮询复杂度最低 | 主选 |
| 关系状态数 | `https://api.bilibili.com/x/relation/stat` | `vmid` 必填 | 文档写 Cookie 或 APP；实测未登录可返回公开粉丝数 | 不需要 WBI/CSRF | `data.follower` | 只返回关注/粉丝数，不含昵称头像；适合作为卡片接口异常时的轻量兜底 | 备选 |
| 用户空间详细信息 | `https://api.bilibili.com/x/space/wbi/acc/info` | `mid`、`w_rid`、`wts` | 需要 Cookie（SESSDATA） | 需要 WBI 签名 | 无直接粉丝数字段 | 用户资料完整，但签名和登录复杂度高 | 不采用 |
| UP 主状态数 | `https://api.bilibili.com/x/space/upstat` | `mid` | 需要任意用户登录，否则无数据 | 不需要 CSRF；登录态必要 | 无粉丝总数字段，主要是播放/阅读/获赞 | 字段不满足需求，且依赖登录 | 不采用 |
| 用户导航栏状态数 | `https://api.bilibili.com/x/space/navnum` | `mid` | 未注明必须登录 | 不需要 WBI/CSRF | 无粉丝数字段 | 可取投稿/订阅数量，不适合粉丝趋势 | 不采用 |
| 粉丝明细新接口 | `https://api.bilibili.com/x/relation/fans` | `vmid`、`ps`、`pn`/`offset` | 需要 Cookie（SESSDATA） | 需要 `Referer`，UA 不能含 `python`；可能返回 `-352` 拦截 | `data.total` | 明细受隐私和数量限制，风控复杂，不适合趋势轮询 | 不采用 |
| 粉丝明细旧接口 | `https://api.bilibili.com/x/relation/followers` | `vmid`、`ps`、`pn` | 需要 Cookie 或 APP | 需要 `Referer`，可能拦截 | `data.total` | 旧接口、最多前 1000 名、隐私限制明显 | 不采用 |
| 创作中心统计 | `https://member.bilibili.com/x/web/index/stat` | 无 | 仅可 Cookie（SESSDATA） | 登录态必要 | `data.total_fans`、`data.incr_fans` | 只能查登录账号自己的创作中心数据 | 不采用 |
| 用户空间动态 | `https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space` | `host_mid` 等 | 未登录需 Cookie 含 `buvid3`；登录需 SESSDATA | WBI 与 `dm_img` 系列风控 | 无粉丝总数字段 | 风控复杂且字段不匹配 | 不采用 |
| 专栏卡片信息 | `https://api.bilibili.com/x/article/cards` | `ids` | 未要求登录 | 需要 `.bilibili.com` Referer；WBI 可选 | 示例中作者 `fans` 不稳定且与卡片对象相关 | 面向稿件/专栏卡片，不是按 UID 查询用户粉丝趋势 | 不采用 |

最终选择 `x/web-interface/card` 作为主接口，原因是：一次请求即可获得 UID、昵称、头像、当前粉丝数和关注数；无需登录、无需 CSRF、无需 WBI；适合低频公开轮询。`x/relation/stat` 作为兜底，只在已有监控用户刷新时用于补偿粉丝数读取。

## 头像与基础资料接口研究

| 接口 | URL | 参数 | 登录 / Cookie | WBI / CSRF / buvid / Referer | 昵称字段 | 头像字段 | 稳定性与风控 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 用户名片信息 | `https://api.bilibili.com/x/web-interface/card` | `mid` 必填，`photo=true` 可同时请求主页头图 | 文档未要求；未登录可获取公开卡片 | 不需要 WBI/CSRF/buvid；保留普通 `User-Agent` 与 `Referer: https://www.bilibili.com/` | `data.card.name` | `data.card.face` | 面向 Web 用户卡片，字段稳定且与粉丝数主接口一致 | 主选，添加用户和定期采集时同步刷新昵称、头像 |
| 用户空间详细信息 | `https://api.bilibili.com/x/space/wbi/acc/info` | `mid`、`w_rid`、`wts` | 文档要求 Cookie（SESSDATA） | 需要 WBI 签名；Cookie 总项数也有限制 | `data.name` | `data.face` | 资料最完整，但签名和登录态复杂；不适合无登录长期监控 | 不采用 |
| 用户名片批量信息 | `https://api.bilibili.com/x/polymer/pc-electron/v1/user/cards` | 多个 `uids` | 文档显示可返回公开资料 | 接口形态偏客户端/批量卡片，字段较多 | `{mid}.name` | `{mid}.face` | 适合批量资料补全，但不是粉丝数主链路，长期稳定性弱于 Web card | 暂不采用 |
| 用户空间动态 | `https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space` | `host_mid` 等 | 未登录通常依赖 buvid；登录依赖 SESSDATA | WBI 与 `dm_img` 系列参数，风控复杂 | 动态作者对象中的 `name` | 动态作者对象中的 `face` | 面向动态流，不是基础资料接口；请求参数和风控成本高 | 不采用 |
| 空间置顶视频 | `https://api.bilibili.com/x/space/top/arc` | `vmid` | 文档未要求登录 | 无 WBI/CSRF | `data.owner.name` | `data.owner.face` | 只有有置顶视频时可用，用户无置顶会返回业务错误 | 不采用 |
| 关系状态数 | `https://api.bilibili.com/x/relation/stat` | `vmid` | 文档写 Cookie 或 APP；公开粉丝数可读取 | 不需要 WBI/CSRF | 无 | 无 | 只返回关注/粉丝数，不能补头像资料 | 仅粉丝数兜底 |

头像/基础资料最终也选择 `x/web-interface/card`：它与粉丝数主接口相同，一次请求即可返回 `data.card.name`、`data.card.face` 和 `data.follower`，不需要登录态、CSRF、WBI 或验证码绕过，长期维护成本最低。已有监控用户如果缺头像，会在下一次 card 采集或手动“立即采集”时补齐；若 card 临时失败，系统只用 `x/relation/stat` 兜底粉丝数并保留已有头像资料。
