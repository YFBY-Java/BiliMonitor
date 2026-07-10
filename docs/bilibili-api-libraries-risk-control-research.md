# B站 API SDK 风控实现研究报告

最后更新：2026-06-11  
研究对象：

- [`Nemo2011/bilibili-api`](../external-research/Nemo2011-bilibili-api/)  
  上游：`https://github.com/Nemo2011/bilibili-api.git`  
  本地版本：`0147ab61aa5a9f821c9b441cb1ddfdc761a3d999`，提交时间 `2026-01-23T20:19:00+08:00`
- [`SeeleWaifu/bili_api`](../external-research/SeeleWaifu-bili_api/)  
  上游：`https://github.com/SeeleWaifu/bili_api.git`  
  本地版本：`2cd3fab2077a701e77b3056ef440e1b101d990f8`，提交时间 `2026-05-03T20:51:52+08:00`

研究方式：代码和文档静态阅读为主，未运行登录、验证码、自动验证或高频接口测试。本文用于理解风控形态和设计边界，不建议把验证码自动化、代理规避、账号态批量请求等能力接入当前产品。

## 结论摘要

`Nemo2011/bilibili-api` 是覆盖面很广的 Python SDK，风控处理集中在通用请求层：`Credential` 管 Cookie，`Api._prepare_request()` 统一处理登录态、CSRF、WBI、buvid、bili_ticket、APP sign 和重试。它适合参考“如何给每个接口标能力需求”，尤其适合直播间接口选型。

`SeeleWaifu/bili_api` 是 Node.js/TypeScript 风控工具箱，覆盖 CookieJar、WBI、dm_img、gaia_vtoken、v_voucher、Geetest 等现代风控流程。它对理解 `-352`、验证码链路、hook 顺序很有价值，但自动 Geetest 识别和验证码提交链路属于高风险能力，不应进入 BiliMonitor。

对 BiliMonitor 的直接建议：

- 优先继续使用无登录、无状态变更、匿名 GET 的直播间接口，见 [`bilibili-live-room-api-research.md`](bilibili-live-room-api-research.md)。
- 若后续要接入直播弹幕 WebSocket，只补一个最小 WBI 能力即可，目标是 `getDanmuInfo`，不要一并引入验证码、自动 buvid 激活、自动 dm_img 模拟。
- 对 `-352`、`-403`、`-101`、`-111` 做明确错误分流：可重试的只短退避重试，不可重试的直接降级或停止，不尝试自动解验证码。
- 第三方仓库只作为研究材料，已放在 [`../external-research/`](../external-research/)；不要把它们混进代码工程目录。

## 风控机制分级

| 等级 | 机制 | 请求难度 | 当前建议 |
| --- | --- | --- | --- |
| 低 | 普通浏览器请求头、`Referer`、匿名 GET、只读查询 | 低，参数可从房间号/UID 推导 | 优先接入，控制频率和缓存 |
| 中 | WBI 签名、WBI key 缓存、`-403` 后刷新 key | 中，需要维护签名、时间戳、失败重试 | 仅在接口明确需要时接入，例如直播 `getDanmuInfo` |
| 中 | buvid、b_lsid、bili_ticket、dm_img 参数 | 中，需要设备/Cookie/fingerprint 类参数 | 当前不主动接入，除非匿名接口稳定性必须依赖 |
| 中高 | 登录态 Cookie、`SESSDATA`、`bili_jct`/CSRF、账号个人数据 | 中高，需要用户账号授权和敏感信息管理 | 当前不建议接入 |
| 高 | v_voucher、gaia_vtoken、Geetest 验证流程 | 高，涉及风控拦截和人工验证 | 只做错误识别，不做自动处理 |
| 不建议 | 自动 Geetest 识别、代理规避、批量绕风控策略 | 很高，合规和账号风险明显 | 不接入 |

## Nemo2011/bilibili-api

### 风控相关实现

关键文件：

- 请求层：[`bilibili_api/utils/network.py`](../external-research/Nemo2011-bilibili-api/bilibili_api/utils/network.py)
- Geetest：[`bilibili_api/utils/geetest.py`](../external-research/Nemo2011-bilibili-api/bilibili_api/utils/geetest.py)
- 登录：[`bilibili_api/login_v2.py`](../external-research/Nemo2011-bilibili-api/bilibili_api/login_v2.py)
- 直播接口定义：[`bilibili_api/data/api/live.json`](../external-research/Nemo2011-bilibili-api/bilibili_api/data/api/live.json)
- 配置说明：[`docs/configuration.md`](../external-research/Nemo2011-bilibili-api/docs/configuration.md)
- 请求说明：[`docs/request_client.md`](../external-research/Nemo2011-bilibili-api/docs/request_client.md)

它的风控能力集中在 `Api` 请求封装里：

| 能力 | 实现位置 | 作用 | 风控程度 |
| --- | --- | --- | --- |
| `Credential` | `network.py` | 保存 `SESSDATA`、`bili_jct`、`buvid3/4`、`DedeUserID`、`ac_time_value` | 中高，涉及账号态 |
| `verify` | `Api._prepare_request()` | 接口声明需要登录时要求 `SESSDATA` | 中高 |
| CSRF | `Api._prepare_request()` | 非 GET 且非 `no_csrf` 时要求 `bili_jct`，POST/DELETE/PATCH 自动补 `csrf`/`csrf_token` | 中高 |
| WBI | `_enc_wbi()`、`get_wbi_mixin_key()` | 通过 nav 的 `wbi_img` 生成签名参数 | 中 |
| WBI 重试 | `Api.request()` | 遇到 `-403` 且接口启用 WBI 时清空 mixin key 后重试，默认 3 次 | 中 |
| wbi2/dm_img | `_enc_wbi2()` | 增加 `dm_img_*` 参数 | 中，代码有能力，未发现业务模块显式调用 |
| buvid | `_get_spi_buvid()`、`_active_buvid()`、`get_buvid()` | 获取并激活 `buvid3/4`，请求层默认缺失时自动补 | 中 |
| bili_ticket | `_get_bili_ticket()`、`get_bili_ticket()` | 可选生成 `bili_ticket` Cookie，默认关闭 | 中 |
| APP sign | `_enc_sign()` | 给部分 APP/直播管理接口增加 `appkey/sign` | 高，通常伴随账号或状态变更 |
| Geetest | `utils/geetest.py` | 创建验证码、本地页面人工完成、收集 validate/seccode | 高，但没有自动识别 |

几点值得注意：

- `RequestSettings` 默认 `wbi_retry_times=3`、`enable_auto_buvid=True`、`enable_bili_ticket=False`。
- 文档明确把 WBI 描述为反爬措施，并说明 WBI key 失效后会自动重新计算。
- README 中有代理和避免反爬的表述。对 BiliMonitor 来说，这类能力只能作为风险提醒，不能作为产品策略。
- 登录流程要求 Geetest 已完成，密码登录、短信登录、二次安全校验都把验证码结果传给登录接口。Nemo 的验证码流程偏人工完成，不包含自动识别模型。

### 直播间接口观察

Nemo 的直播间接口清单位于 `bilibili_api/data/api/live.json`。按该文件标记统计：

- `verify=true` 或字符串 `"true"`：24 处。
- `verify=false`：17 处。
- `wbi=true`：1 处，是 `GET /xlive/web-room/v1/index/getDanmuInfo`。
- `sign=true`：2 处，是开播和停播接口。

对当前项目有参考价值的低风控接口：

| 接口类别 | 代表接口 | 参数 | 可获取数据 | 风控程度 |
| --- | --- | --- | --- | --- |
| 房间基础信息 | `getRoomPlayInfo`、`getInfoByRoom`、`getRoomBaseInfo` 类 | `room_id`、真实房间号或展示房间号 | 房间标题、主播 UID、封面、分区、开播状态、清晰度信息 | 低，Nemo 标 `verify=false` |
| 房间分区 | `Area/getList` | 无或分区参数 | 直播分区列表 | 低 |
| 榜单/展示数据 | 大航海榜、高能榜、七日榜、粉丝牌排行榜 | `roomid`、`ruid`、分页参数 | 榜单、用户展示信息、排名 | 低到中，数据涉及用户展示信息，需谨慎存储 |
| 礼物配置 | 礼物面板、礼物配置 | `room_id`、`area_id`、`source=live` | 礼物 ID、名称、展示配置 | 低 |
| 播放流信息 | `playUrl`、`getRoomPlayInfo v2` | `cid/room_id`、协议、格式、编码、清晰度 | FLV/HLS/fMP4 URL、可用清晰度 | 低到中，只在确实需要播放/录制时接入 |

中等风控接口：

| 接口 | 参数 | 可获取数据 | 难点 |
| --- | --- | --- | --- |
| `GET /xlive/web-room/v1/index/getDanmuInfo` | `id` 真实房间号、`type=0`、`web_location=444.8` | WebSocket/WSS 服务器、token、连接配置 | `wbi=true`，需要 WBI 签名 |
| `LiveDanmaku` WebSocket 鉴权包 | `uid`、`roomid`、`protover`、`platform=web`、`type=2`、`buvid`、`key` | 实时弹幕、礼物、进场、房间事件等 | 需要 `getDanmuInfo` token，缺 buvid 时会自动生成 |

高风控或不建议接入的直播间接口：

- 开播、停播：`verify=true` 且 `sign=true`，会改变账号状态。
- 发弹幕、送礼、房管禁言、更新公告、领取奖励、报名活动：需要登录态，很多是 POST 和 CSRF，且会改变状态。
- 获取自己的直播用户信息、背包、粉丝牌面板、个人直播历史：需要账号 Cookie，涉及个人数据。

### 对 BiliMonitor 的可借鉴点

值得借鉴：

- 用接口元数据标明 `requiresLogin`、`requiresCsrf`、`requiresWbi`、`stateChanging`，在调用前阻断不该发的请求。
- WBI key 缓存和有限重试机制可以用于直播弹幕配置接口。
- 对 `SESSDATA`、`bili_jct`、`buvid` 等凭据做显式类型边界，避免在匿名监控链路里误带账号态。

不建议借鉴：

- 默认自动补 buvid 并激活设备指纹。
- 代理规避、反爬规避导向的能力。
- 登录和 Cookie 刷新能力。当前产品不需要用户账号态。

## SeeleWaifu/bili_api

### 风控相关实现

关键文件：

- CookieJar 与 nav：[`src/login.ts`](../external-research/SeeleWaifu-bili_api/src/login.ts)
- 请求头：[`src/headers.ts`](../external-research/SeeleWaifu-bili_api/src/headers.ts)
- WBI：[`src/wbi_sign.ts`](../external-research/SeeleWaifu-bili_api/src/wbi_sign.ts)
- dm_img：[`src/img_sign.ts`](../external-research/SeeleWaifu-bili_api/src/img_sign.ts)
- gaia_vtoken：[`src/gaia_vtoken.ts`](../external-research/SeeleWaifu-bili_api/src/gaia_vtoken.ts)
- v_voucher：[`src/v_voucher.ts`](../external-research/SeeleWaifu-bili_api/src/v_voucher.ts)
- Geetest 编排：[`src/geetest/geetest.ts`](../external-research/SeeleWaifu-bili_api/src/geetest/geetest.ts)
- 浏览器人工验证码：[`src/geetest/simulator/browser.ts`](../external-research/SeeleWaifu-bili_api/src/geetest/simulator/browser.ts)
- 自动验证码模拟器：[`src/geetest/simulator/auto.ts`](../external-research/SeeleWaifu-bili_api/src/geetest/simulator/auto.ts)

它的设计更像“可组合 got 客户端能力”：

| 能力 | 请求难度 | 数据/作用 | 风控程度 |
| --- | --- | --- | --- |
| `createCookieJarGot` | 低到中 | 绑定 `tough-cookie`，先访问 B站首页种 Cookie | 中 |
| `apiHeaders`/`documentHeaders` | 低 | 统一浏览器请求头、`sec-ch-ua`、`sec-fetch-*` | 低 |
| `createWbiSignGot` | 中 | 从 nav 获取 WBI 图片 key，给 `URLSearchParams` 加 `w_rid/wts` | 中 |
| `createImgSignGot` | 中 | 生成 `dm_img_list`、`dm_img_str`、`dm_cover_img_str`、`dm_img_inter` | 中 |
| `createGaiaVtokenGot` | 高 | 保存 `x-bili-gaia-vtoken` Cookie，并给请求追加 `gaia_vtoken` | 高 |
| `catchVoucherError` | 高 | 将 `-352` 且带 `v_voucher` 的响应映射为 `NeedCaptchaError` | 高，但错误识别本身可借鉴 |
| `gtRegister`/`gtValidate` | 高 | 使用 `v_voucher` 注册并校验 Geetest，返回 `grisk_id` | 高，不建议接入 |
| `Geetest` | 高 | 默认本地浏览器人工点击，支持注入自动模拟器 | 高 |
| `AutoGeetestSimulator` | 很高 | 使用内置 ONNX 模型和图片处理自动求解 | 不建议 |

依赖侧也能看出重点：`got`、`tough-cookie`、`zod`、`neverthrow` 是请求与类型校验；`onnxruntime-node`、`sharp` 则服务于验证码图片自动化，这部分是风险最高的能力。

### 覆盖的接口形态

Seele 仓库没有直播间接口模块。它主要实现了：

- 用户资料：`requestUserInfo` 调 `x/space/wbi/acc/info`，要求 CookieJar、ImgSign、WBI。
- 批量用户资料：`requestUserInfos` 调 IM 域接口，要求 CookieJar。
- 关注关系：`requestRelations` 调 `x/space/wbi/acc/relation`，要求 CookieJar、WBI。
- 评论列表：`requestAllComments` 调 `x/v2/reply/wbi/main`，要求 CookieJar、WBI。
- 以上接口遇到 `-352` 时会返回 `NeedCaptchaError` 和 `v_voucher`。

对直播间研究的直接价值不在接口清单，而在风控模型：

- `-352` 不应简单重试，往往代表需要验证码或更强风控 token。
- `gaia_vtoken` 如果要参与 WBI，必须先注入 gaia 参数，再执行 WBI 签名。这个 hook 顺序值得记住。
- 用 TypeScript 类型要求 `CookieJarGot -> ImgSignGot -> WbiSignGot` 的能力顺序，是一个很好的工程设计参考。

### 对 BiliMonitor 的可借鉴点

值得借鉴：

- 把 `-352` 显式建模为 `NeedCaptcha` 类错误，而不是普通失败。
- 用组合能力或接口标签表达请求前置条件，例如 `AnonymousClient`、`WbiClient`、`AuthenticatedClient`。
- WBI、dm_img、gaia_vtoken 的 hook 顺序在设计上要清楚，避免签完名后又改查询参数。

不建议借鉴：

- Geetest 自动识别、自动提交、ONNX 模型求解流程。
- v_voucher 到 gaia_vtoken 的完整闭环。
- 登录态集成测试或需要真实账号 Cookie 的流程。

## 两个项目对比

| 维度 | Nemo2011/bilibili-api | SeeleWaifu/bili_api |
| --- | --- | --- |
| 技术栈 | Python SDK | Node.js/TypeScript 工具库 |
| 覆盖面 | 视频、动态、直播、登录、上传等完整 SDK | 用户资料、关系、评论、登录和风控工具 |
| 风控入口 | `Api` 通用请求层 | got hook 和类型能力组合 |
| WBI | 内置 `_enc_wbi`，`-403` 自动刷新重试 | 独立 `createWbiSignGot`，要求 CookieJar |
| dm_img | 有 `_enc_wbi2`，实现较轻，业务调用很少 | 独立 `ImgSigner`，模拟 WebGL、DOM、鼠标轨迹 |
| buvid/设备 | 默认可自动生成并激活 buvid | 有 `b_lsid`，更多依赖 CookieJar |
| bili_ticket | 可选，默认关闭 | 未见同等核心能力 |
| v_voucher/Gaia | 只零散出现 `gaia_source`，无完整链路 | 有完整 `-352 -> v_voucher -> Geetest -> grisk_id -> gaia_vtoken` 模型 |
| Geetest | 本地页面人工完成 | 默认人工浏览器，可选自动模拟器 |
| 对直播间价值 | 高，含直播接口清单和 LiveDanmaku | 中，提供通用风控理解 |
| 当前可采用程度 | 可参考接口标记、WBI 最小实现、错误分级 | 可参考错误建模和 hook 顺序，不采用验证码自动化 |

## 推荐接入策略

### 第一优先级：继续走低风控匿名接口

保持现有方向：

- 主播是否开播：按 UID 批量查询。
- 房间号规范化：展示房间号转真实房间号。
- 房间基础信息：标题、封面、分区、主播 UID、直播状态。
- 需要播放时才取播放流，不把播放流作为常规监控依赖。

这些接口不需要账号 Cookie，不改变状态，请求参数少，最适合监控产品。

### 第二优先级：只为实时弹幕补 WBI

若要支持实时弹幕或直播事件：

- 仅针对 `getDanmuInfo` 实现 WBI。
- WBI key 从 nav 获取并缓存。
- `-403` 时刷新一次或少量重试，失败后降级。
- `-352` 视为风控拦截，停止该链路，不自动验证。
- WebSocket 鉴权只使用匿名 UID、房间真实 ID、平台、协议版本、token、必要 buvid。

### 暂不接入

- 用户登录、Cookie 刷新、短信/密码登录。
- 发送弹幕、送礼、禁言、开播、停播、更新公告等状态变更接口。
- 自动 Geetest、v_voucher 校验、gaia_vtoken 注入闭环。
- 代理规避、批量反爬规避、自动设备指纹激活。

## 错误码处理建议

| 错误码 | 含义倾向 | 建议处理 |
| --- | --- | --- |
| `-101` | 未登录 | 匿名链路直接降级，不提示用户提供 Cookie |
| `-111` | CSRF 错误 | 当前产品不应触发，若触发说明误用了账号态接口 |
| `-352` | 需要验证码或风控 token | 不重试，不自动解，记录并降级 |
| `-403` | 权限、WBI 失效或签名问题 | 对 WBI 接口刷新 key 后有限重试，其它场景直接失败 |
| `100003` | v_voucher 过期 | 不处理，当前不接入 v_voucher |

## 落地到代码时的边界

建议在 BiliMonitor 里把接口能力分层：

| 层级 | 允许能力 | 禁止能力 |
| --- | --- | --- |
| `AnonymousClient` | 普通 GET、固定请求头、低频重试 | Cookie、CSRF、状态变更 |
| `WbiClient` | WBI key 缓存、WBI 签名、`-403` 有限重试 | 自动验证码、gaia_vtoken |
| `AuthenticatedClient` | 预留接口标签，不实现 | 当前阶段不要落地 |

接口元数据至少包含：

- `requiresLogin`
- `requiresCsrf`
- `requiresWbi`
- `stateChanging`
- `containsPersonalData`
- `recommendedForMonitoring`

这样可以把“能不能请求”前置到代码结构里，避免后续无意接入高风险接口。

## 最终判断

对当前 BiliMonitor 来说，最有价值的不是完整复制任一 SDK，而是吸收它们的边界设计：

- 从 Nemo 学接口分级和 WBI 最小封装。
- 从 Seele 学错误建模和请求能力组合。
- 保持产品默认匿名、只读、低频、低风控。

下一步如果要继续深入，建议只围绕一个目标做验证：`getDanmuInfo + LiveDanmaku` 匿名实时事件链路。验证范围应限制在单房间、低频、无账号 Cookie、不处理验证码。
