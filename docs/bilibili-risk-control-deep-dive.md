# B站第三方 SDK 风控深度研究

最后更新：2026-06-11  
研究对象：

- [`../external-research/Nemo2011-bilibili-api/`](../external-research/Nemo2011-bilibili-api/)
- [`../external-research/SeeleWaifu-bili_api/`](../external-research/SeeleWaifu-bili_api/)

本文是 [`bilibili-api-libraries-risk-control-research.md`](bilibili-api-libraries-risk-control-research.md) 的深挖补充。重点不是复刻绕过链路，而是把两个项目里的风控识别、处理方式、请求难度和工程边界研究明白。验证码自动化、代理规避、批量绕风控等能力只做风险识别，不建议进入 BiliMonitor。

## 一句话结论

这两个项目覆盖了 B站 Web API 常见的三层风控：

| 层级 | 代表机制 | 本质 | 项目里的处理方式 | BiliMonitor 态度 |
| --- | --- | --- | --- | --- |
| 请求完整性 | WBI、APP sign、CSRF | 参数签名和账号操作校验 | Nemo 集中在 `Api`，Seele 用 got hook | 只接最小 WBI |
| 设备与浏览器画像 | buvid、b_lsid、dm_img、bili_ticket、请求头、TLS/browser impersonate | 让请求更像真实浏览器/设备 | Nemo 自动补 buvid，Seele 模拟 dm_img | 默认不主动模拟 |
| 验证与恢复 | `-352`、v_voucher、Geetest、gaia_vtoken | 命中风控后要求验证码/短期通行 token | Seele 有完整恢复链，Nemo 偏人工 Geetest | 识别后停止或降级 |

## Nemo2011/bilibili-api 深挖

### 风控入口

Nemo 的核心文件是 [`bilibili_api/utils/network.py`](../external-research/Nemo2011-bilibili-api/bilibili_api/utils/network.py)。它把大多数风控处理放在 `Api._prepare_request()` 和 `Api.request()`：

| 机制 | 触发条件 | 处理方式 | 请求难度 |
| --- | --- | --- | --- |
| `verify` | API 元数据或业务代码声明需要登录 | 请求前要求 `SESSDATA` | 中高 |
| CSRF | 非 GET 且未声明 `no_csrf` | 请求前要求 `bili_jct`，部分操作自动加 `csrf/csrf_token` | 高 |
| WBI | `wbi=True` | 从 nav 获取 `wbi_img`，计算 `wts/w_rid` | 中 |
| WBI 失效 | WBI 请求返回 `-403` | 清空缓存 key，有限重试，默认 3 次 | 中 |
| wbi2/dm_img | `wbi2=True` | 加 `dm_img_*` 参数 | 中 |
| buvid | Cookie 里缺 `buvid3/4` 且自动 buvid 开启 | 请求 SPI 并激活 buvid，再写入 Cookie | 中高 |
| bili_ticket | 全局设置开启 | 获取并缓存约 3 天 | 中 |
| APP sign | `sign=True` | 加 appkey/sign | 高 |
| 代理 | 全局或 Credential 设置 proxy | 通过请求客户端代理出站 | 高风险，不建议用于规避 |

### 量化结果

对 `bilibili_api/data/api/*.json` 做静态统计：

| 标记 | 命中数量 | 含义 |
| --- | ---: | --- |
| `verify=true` 或 `"true"` | 232 | 很多接口真正的难点是登录态 |
| `wbi=true` | 26 | WBI 是常见但不是主导难点 |
| `wbi2=true` | 6 | 更强的浏览器画像参数，主要集中在用户/动态/视频相关接口 |
| `sign=true` | 5 | APP 签名，常伴随登录或状态变更 |
| `no_csrf=true` | 5 | 个别非 GET 接口绕开通用 CSRF 自动要求 |

这个比例很重要：对监控产品而言，真正应该绕开的不是“签名怎么做”，而是“不要碰账号态和状态变更接口”。

### WBI 与 wbi2

Nemo 的 WBI 思路：

- `get_wbi_mixin_key()` 从 nav 的 `wbi_img` 拆出 key，并做模块级缓存。
- `_enc_wbi()` 增加时间戳、默认 `web_location`，按参数排序后生成 `w_rid`。
- `Api.request()` 遇到 `-403` 且当前接口启用了 WBI 时，刷新 key 后重试。

Nemo 的 wbi2 比较轻：

- `_enc_wbi2()` 只补 `dm_img_list`、`dm_img_str`、`dm_cover_img_str`、`dm_img_inter`。
- 在 API 元数据里只有 6 处 `wbi2=true`。
- 它不像 Seele 那样模拟较完整的鼠标轨迹和 WebGL 画像。

对 BiliMonitor 的结论：WBI 可以做成最小独立模块；wbi2 不建议默认接入，除非某个只读接口稳定需要它，并且要先做低频验证。

### buvid、bili_ticket、设备画像

Nemo 的 buvid 链路：

- 缺 `buvid3/4` 时默认自动生成。
- 生成后还会向激活接口提交浏览器/设备画像相关 payload。
- 成功后把 `buvid3/4` 填回 Cookie。

这能提高某些接口可用性，但也让匿名监控系统变得更像“伪造浏览器设备”。对 BiliMonitor 来说，应当默认关闭这类自动设备画像。更稳妥的做法是先只使用低风控匿名接口，遇到需要 buvid 的接口再单独评估。

`bili_ticket` 在 Nemo 中默认关闭。文档也把它描述成有时有效的辅助 Cookie，不适合作为稳定产品依赖。

### Cookie 刷新与登录态

Nemo 支持：

- `Credential.check_valid()`
- `Credential.check_refresh()`
- `Credential.refresh()`
- 密码登录、短信登录、二维码登录、二次安全校验

这套能力是账号态 SDK 的完整能力，对个人脚本有用，但对 BiliMonitor 当前定位不合适。原因：

- 需要保存用户敏感 Cookie 和 refresh token。
- 容易把只读监控产品带向账号代操作。
- 一旦触发风控，后续会牵涉验证码和安全校验。

### Geetest

Nemo 的 Geetest 是人工完成路线：

- 先向 B站接口获取 gt/challenge/token。
- 启动本地页面，让用户完成验证。
- 收集 validate/seccode 给登录或安全校验流程使用。

它没有内置自动识别模型。若未来必须有账号态登录，也只能考虑“用户主动、人工验证、显式授权”的路线；当前阶段不建议接入。

### 其它风控线索

- `user_render_data.py` 会从用户空间页面的渲染数据里提取 `w_webid`，用于部分用户动态/空间接口。
- `CurlCFFIClient` 支持 browser `impersonate` 和 HTTP/2，这属于更底层的浏览器指纹相似化能力。它对普通监控产品不是必要能力，不建议用作规避手段。
- README 明确提到代理和避免反爬，说明项目有“尽量让请求过风控”的取向。BiliMonitor 只应吸收接口分级和错误处理，不吸收规避策略。

## SeeleWaifu/bili_api 深挖

### 工程模型

Seele 的设计不是大 SDK，而是一组 got 能力组合：

| 能力 | 文件 | 用途 |
| --- | --- | --- |
| CookieJar | [`src/login.ts`](../external-research/SeeleWaifu-bili_api/src/login.ts) | 绑定 tough-cookie，并先访问首页建立基础 Cookie |
| 请求头 | [`src/headers.ts`](../external-research/SeeleWaifu-bili_api/src/headers.ts) | 统一浏览器请求头 |
| WBI | [`src/wbi_sign.ts`](../external-research/SeeleWaifu-bili_api/src/wbi_sign.ts) | 获取 nav WBI key，给请求签名 |
| dm_img | [`src/img_sign.ts`](../external-research/SeeleWaifu-bili_api/src/img_sign.ts) | 生成浏览器画像和鼠标轨迹参数 |
| gaia_vtoken | [`src/gaia_vtoken.ts`](../external-research/SeeleWaifu-bili_api/src/gaia_vtoken.ts) | 保存短期风控 token 并注入请求 |
| v_voucher | [`src/v_voucher.ts`](../external-research/SeeleWaifu-bili_api/src/v_voucher.ts) | 识别 `-352` 并进入验证码注册/校验 |
| Geetest | [`src/geetest/`](../external-research/SeeleWaifu-bili_api/src/geetest/) | 处理极验流程、payload、人工/自动模拟器 |

它最值得借鉴的是“能力顺序”和“错误类型”：

- `NeedCaptchaError` 单独表达验证码需求。
- got 类型约束要求先有 CookieJar，再叠加 ImgSign/WBI/Gaia。
- 测试明确证明：dm_img 必须在 WBI 前加入，gaia_vtoken 也必须在 WBI 前加入，否则签名不包含这些参数。

### `-352 -> v_voucher -> Geetest -> gaia_vtoken`

Seele 的完整恢复链路是：

1. 业务接口返回 `-352`，响应 data 里带 `v_voucher`。
2. `catchVoucherError()` 把它转成 `NeedCaptchaError`。
3. 通过 v_voucher 注册验证码，得到 Geetest 参数和临时 token。
4. 用户或模拟器完成 Geetest。
5. 把验证结果提交给 B站风控接口，成功后得到 `grisk_id`。
6. `createGaiaVtokenGot()` 把 `grisk_id` 作为 `x-bili-gaia-vtoken` Cookie，并在约 120 秒有效期内注入请求参数。
7. 再次请求原接口。

这条链路说明 Seele 对“风控恢复”理解得很深，但它不是 BiliMonitor 应该产品化的方向。我们可以借鉴的是错误建模，不是恢复链路本身。

### dm_img 与 WBI 顺序

Seele 的 `img_sign.ts` 比 Nemo 更完整：

- 随机 WebGL 字符串和 vendor/renderer。
- 生成 DOM 位置、窗口大小、滚动位置。
- 模拟鼠标移动事件，并编码成 `dm_img_list`。
- 输出 `dm_img_str`、`dm_cover_img_str`、`dm_img_inter`。

测试证明，dm_img 参数会改变 WBI 签名结果。因此如果某个接口同时需要 dm_img 和 WBI，顺序必须是：

1. 先补 dm_img / gaia 参数。
2. 再做 WBI 签名。

对 BiliMonitor 的结论：这条顺序规则有工程价值；但浏览器画像模拟本身不应默认启用。

### Geetest 自动化能力

Seele 包含两种验证码模拟器：

- `BrowserGeetestSimulator`：启动本地 UI，由人点击。
- `AutoGeetestSimulator`：加载 ONNX 模型和图片处理依赖，自动识别点击位置并重试。

项目内还有单元测试验证自动识别 fixture。这说明它确实具备自动验证码处理能力，而不是只有接口封装。

这部分风险最高：

- 它直接面向验证码自动化。
- 它会把风控拦截转成可程序化恢复。
- 若接入监控产品，容易越过“只读、低频、匿名”的边界。

因此 BiliMonitor 不能接入自动验证码、不能提供自动恢复风控的产品能力。最多可以在日志里识别“需要人工验证/请求被风控”，然后停止任务。

### 业务接口侧的触发点

Seele 已在这些接口上处理 `-352`：

- 用户资料 `x/space/wbi/acc/info`
- 关注关系 `x/space/wbi/acc/relation`
- 评论列表 `x/v2/reply/wbi/main`
- 批量用户信息接口

这些都不是直播间监控第一阶段必需接口。它们对我们有参考价值：用户空间、关系、评论这类接口更容易进入 WBI/dm_img/voucher 风控区。

## 风控“解决方法”拆解

这里的“解决”按工程可接受度分三类。

### A. 可接受：请求合法性补齐

这类只是在接口要求内补齐必要参数，不主动越过验证码或账号限制：

| 方法 | 适用 | 采纳建议 |
| --- | --- | --- |
| 标准请求头和 Referer | 匿名只读接口 | 可以 |
| WBI 最小签名 | 明确标 `wbi=true` 的只读接口 | 可以，低频使用 |
| WBI key 过期后有限重试 | `-403` 且只限 WBI 接口 | 可以，重试次数很小 |
| 接口能力标记 | `requiresLogin/requiresWbi/stateChanging` | 强烈建议 |
| 明确错误码分流 | `-101/-111/-352/-403` | 强烈建议 |

### B. 谨慎：设备/浏览器相似化

这类能提高接口可用性，但也会明显接近反爬规避：

| 方法 | 项目实现 | 风险 |
| --- | --- | --- |
| buvid 自动生成和激活 | Nemo | 生成设备身份并提交画像 |
| dm_img 参数模拟 | Nemo/Seele | 模拟浏览器和鼠标行为 |
| bili_ticket | Nemo | 辅助 Cookie，不稳定 |
| CookieJar 先访问首页 | Seele | 较轻，但仍会产生会话痕迹 |
| browser impersonate/TLS 指纹 | Nemo CurlCFFIClient | 容易变成规避策略 |

BiliMonitor 默认不使用这些。只有在某个低风险只读接口离不开它时，才单独评审。

### C. 不采纳：验证码/风控恢复自动化

这类属于高风险能力：

| 方法 | 项目实现 | 结论 |
| --- | --- | --- |
| v_voucher 注册和校验 | Seele | 不接入 |
| gaia_vtoken 注入恢复请求 | Seele | 不接入 |
| 自动 Geetest 识别 | Seele | 不接入 |
| 代理规避风控 | Nemo README/设置 | 不接入 |
| 登录 Cookie 刷新和账号代操作 | Nemo | 当前不接入 |

## 对 BiliMonitor 的具体策略

### 请求客户端分层

建议未来代码里明确分三层：

| 客户端 | 能做什么 | 不能做什么 |
| --- | --- | --- |
| `AnonymousBiliClient` | 匿名 GET、固定 UA/Referer、超时、低频重试 | Cookie、CSRF、验证码 |
| `WbiBiliClient` | 在匿名基础上加 WBI key 缓存和签名 | dm_img、gaia、登录态 |
| `AuthenticatedBiliClient` | 仅保留设计名，暂不实现 | 当前不要落地 |

### 错误策略

| 错误/现象 | 策略 |
| --- | --- |
| `-101` 未登录 | 该接口从监控候选列表移除 |
| `-111` CSRF 错 | 说明误入账号操作接口，停止 |
| `-352` 风控验证码 | 记录为 `NEED_CAPTCHA`，停止，不自动恢复 |
| `-403` WBI 失败 | 仅 WBI 接口刷新 key 后重试 1 次 |
| 连续超时/429/频率异常 | 指数退避，暂停该目标 |

### 接口元数据

每个 B站接口都应该标：

- `riskLevel`
- `requiresWbi`
- `requiresLogin`
- `requiresCsrf`
- `stateChanging`
- `containsPersonalData`
- `allowAutoRetry`
- `onRiskControl`

这样以后即使有人看到第三方 SDK 里有“解决风控”的办法，也不会无意把它接进业务。

## 最终判断

两个项目把 B站风控研究得比较完整：

- Nemo：适合理解大 SDK 如何集中处理 WBI、Cookie、CSRF、buvid、bili_ticket、APP sign。
- Seele：适合理解现代 Web 风控恢复链路，尤其是 `-352`、v_voucher、Geetest、gaia_vtoken 和 hook 顺序。

但对 BiliMonitor 最重要的不是“更强地解决风控”，而是“更稳地避开高风控接口”：

1. 优先匿名只读接口。
2. 只在必要时加最小 WBI。
3. 命中验证码立即停止和降级。
4. 不接入自动验证码、账号代操作、代理规避、设备画像模拟。

如果后续确实要做实时直播事件，唯一建议继续研究的链路是：匿名 `getDanmuInfo` 的最小 WBI + 单房间 WebSocket 连接稳定性。除此之外，不建议向更强风控恢复能力扩展。
