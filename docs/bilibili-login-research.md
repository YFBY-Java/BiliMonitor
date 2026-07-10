# Bilibili 登录态获取研究报告

本报告基于本地资料库、现有项目代码和线上轻量核验整理，核验时间为 2026-06-15（工作区时区 Asia/Shanghai）。本文只讨论用户主动授权登录后的登录态保存与刷新，不覆盖规避风控、批量账号、绕过验证码等用途。

## 结论摘要

推荐实现路线是 Web 端扫码登录。

理由：

- 不需要收集用户账号密码，不需要短信验证码，用户授权链路清晰。
- 当前线上端点仍可用：生成二维码返回 32 位 `qrcode_key`，未扫码轮询返回 `86101`。
- 成功后能得到 Web Cookie：`SESSDATA`、`bili_jct`、`DedeUserID`、`DedeUserID__ckMd5`、`sid` 等，以及必须持久化的 `refresh_token`。
- 对当前 `social-data-monitor` 这种 Spring Boot 后端更适合：后端持有 Cookie，前端只展示二维码和状态，不把 `SESSDATA` 暴露给浏览器。

不建议首期做密码/短信登录。它们依赖 Geetest、人机验证和可能的二次安全验证，维护成本和账号安全责任都更高。

TV 扫码登录可以拿到 `access_key`，适合 APP/TV 专用接口，但依赖 appkey 签名，长期稳定性和合规风险更高。除非明确需要 APP-only 接口，否则首期不要采用。

## 本地资料来源

重点参考了以下文件：

- `bilibili-api-collect-new-research/repo/docs/login/login_action/QR.md`
  - Web 二维码生成、轮询状态码、成功 Cookie、旧版接口、TV 扫码。
- `bilibili-api-collect-new-research/repo/docs/login/cookie_refresh.md`
  - Web Cookie 刷新、`refresh_token`、`refresh_csrf`、`confirm/refresh`。
- `bilibili-api-collect-new-research/repo/docs/login/login_info.md`
  - `x/web-interface/nav` 登录态校验。
- `bilibili-api-collect-new-research/repo/docs/login/login_action/readme.md`
  - 密码/短信登录前置 captcha/Geetest。
- `bilibili-api-collect-new-research/repo/docs/login/login_action/password.md`
  - 密码登录的 RSA 公钥、加密密码、风控二次验证。
- `bilibili-api-collect-new-research/repo/docs/login/login_action/SMS.md`
  - 短信验证码发送与短信登录。
- `external-research/SeeleWaifu-bili_api/src/login.ts`
  - TypeScript 版 Web 扫码、CookieJar、nav 校验实现。
- `external-research/SeeleWaifu-bili_api/tests/scripts/login.ts`
  - 终端二维码登录脚本，会保存 CookieJar。
- `external-research/SeeleWaifu-bili_api/tests/helpers/auth.ts`
  - CookieJar 持久化、登录态断言、获取 `bili_jct`。
- `external-research/Nemo2011-bilibili-api/bilibili_api/login_v2.py`
  - Python 版 Web/TV 二维码登录、密码、短信、安全验证。
- `external-research/Nemo2011-bilibili-api/bilibili_api/data/api/login.json`
  - 登录相关 API 配置。
- `social-data-monitor/backend/src/main/java/com/socialmonitor/bilibili/client/BilibiliApiClient.java`
  - 现有 Bilibili 客户端，目前主要调用公开接口。
- `social-data-monitor/backend/src/main/resources/db/migration/V1__init_schema.sql`
  - 已有 `platform_credential` 表，可承载 Bilibili 登录态。

线上核验使用的 Bilibili 端点：

- https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header
- https://passport.bilibili.com/x/passport-login/web/qrcode/poll
- https://api.bilibili.com/x/web-interface/nav
- https://passport.bilibili.com/x/passport-login/web/cookie/info
- https://passport.bilibili.com/x/passport-login/web/key
- https://passport.bilibili.com/x/passport-login/captcha?source=main_web
- https://passport.bilibili.com/qrcode/getLoginUrl
- https://passport.bilibili.com/qrcode/getLoginInfo

## 线上核验结果

本次只做无账号、无扫码、无密码提交的轻量核验。

| 项目 | 结果 |
| --- | --- |
| Web 二维码生成 | HTTP 200，`code=0`，`message=OK`，`qrcode_key` 长度 32 |
| 二维码 URL | 当前返回前缀为 `https://account.bilibili.com/h5/account-h5/auth/scan-web?...`，和旧文档里的 `passport.bilibili.com/h5-app/...` 路径不同，但语义相同 |
| Web 二维码轮询 | HTTP 200，顶层 `code=0`，未扫码时 `data.code=86101`、`data.message=未扫码` |
| `nav` 未登录校验 | HTTP 200，`code=-101`，`data.isLogin=false` |
| `cookie/info` 未登录校验 | HTTP 200，`code=-101`，`message=账号未登录` |
| 密码登录公钥 | HTTP 200，`code=0`，`hash` 长度 16，返回 PEM 公钥 |
| captcha 入口 | HTTP 200，`code=0`，`data.type=geetest`，返回 32 位 token，包含 `gt` 和 `challenge` |
| 旧版二维码生成 | `qrcode/getLoginUrl` 仍返回 `code=0` 和 32 位 `oauthKey` |
| 旧版二维码轮询 | `qrcode/getLoginInfo` 使用真实新 `oauthKey` 轮询返回 `code=200000`、`status=false`、`data=null`，不应采用 |

注意：旧版轮询本地资料写的是已失效，线上表现也印证了这一点，只是错误码从资料中的 `20000` 变成了本次实测的 `200000`。

## Web 扫码登录流程

### 1. 创建后端登录会话

后端创建一个短生命周期登录会话，例如 `loginId=UUID`，会话中保存：

- `qrcode_key`
- 一个临时 Cookie 容器
- 创建时间、过期时间
- 当前状态：`WAITING` / `SCANNED` / `EXPIRED` / `SUCCESS` / `FAILED`

二维码有效时间按资料记录为约 180 秒。实现上建议后端会话 TTL 设置为 180 秒，前端倒计时 170 秒左右就提示刷新。

### 2. 请求二维码

请求：

```http
GET https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header
```

建议带浏览器型请求头：

```http
User-Agent: Mozilla/5.0 ...
Referer: https://www.bilibili.com/
Origin: https://www.bilibili.com
Accept: application/json
```

响应关键字段：

```json
{
  "code": 0,
  "data": {
    "url": "二维码内容 URL",
    "qrcode_key": "32位登录密钥"
  }
}
```

前端不要自己调用 Bilibili。后端返回 `loginId` 和 `qrUrl`，前端用 `qrUrl` 生成二维码即可。

推荐前端接口：

```http
POST /api/bilibili/auth/qr/start
```

响应：

```json
{
  "loginId": "uuid",
  "qrUrl": "https://account.bilibili.com/...",
  "expiresInSeconds": 180
}
```

### 3. 轮询二维码状态

请求：

```http
GET https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key={qrcode_key}&source=main-fe-header
```

状态码：

| `data.code` | 含义 | 前端展示 |
| --- | --- | --- |
| `86101` | 未扫码 | 等待扫码 |
| `86090` | 已扫码，手机端未确认 | 等待手机确认 |
| `86038` | 二维码已失效 | 二维码过期，提示刷新 |
| `0` | 登录成功 | 保存登录态，关闭二维码 |

推荐前端接口：

```http
GET /api/bilibili/auth/qr/{loginId}/status
```

响应示例：

```json
{
  "status": "SCANNED",
  "message": "已扫码，等待确认"
}
```

轮询间隔建议 1 到 2 秒。本地 `SeeleWaifu-bili_api` 脚本使用 1 秒；产品环境用 2 秒更温和。

### 4. 成功后提取登录态

扫码确认成功时，轮询响应会：

- 在 HTTP 响应头写入 `Set-Cookie`
- JSON 里返回 `data.url`
- JSON 里返回 `data.refresh_token`
- JSON 里返回 `data.timestamp`

需要保存：

```json
{
  "auth_type": "WEB_COOKIE",
  "cookies": {
    "SESSDATA": "***",
    "bili_jct": "***",
    "DedeUserID": "***",
    "DedeUserID__ckMd5": "***",
    "sid": "***",
    "buvid3": "可选",
    "buvid4": "可选"
  },
  "refresh_token": "***",
  "created_at": "2026-06-15T...",
  "last_validated_at": "2026-06-15T...",
  "source": "web_qrcode"
}
```

字段含义：

- `SESSDATA`：核心登录 Cookie，很多读接口只需要它。
- `bili_jct`：CSRF token，写操作和刷新 Cookie 时需要。
- `DedeUserID`：登录用户 UID。
- `DedeUserID__ckMd5`：和 UID 相关的校验 Cookie。
- `sid`：会话相关 Cookie。
- `refresh_token`：Web 刷新登录态需要持久化；Web 前端通常把它放在 localStorage 的 `ac_time_value`。

不要把这些字段返回给前端。前端只需要知道是否登录成功、登录账号的基本信息。

### 5. 登录态校验

保存后立即调用：

```http
GET https://api.bilibili.com/x/web-interface/nav
Cookie: SESSDATA=...; bili_jct=...; DedeUserID=...
```

成功时 `code=0` 且 `data.isLogin=true`，可以取：

- `data.mid`
- `data.uname`
- `data.face`
- `data.level_info.current_level`
- `data.vipStatus`
- `data.wbi_img`

未登录或失效时常见返回：

```json
{
  "code": -101,
  "data": {
    "isLogin": false
  }
}
```

推荐后端接口：

```http
GET /api/bilibili/auth/status
```

响应：

```json
{
  "loggedIn": true,
  "mid": 123456,
  "uname": "账号昵称",
  "face": "https://...",
  "expiresAt": "如果能从 Cookie 解析则填"
}
```

## Java 后端实现设计

当前后端是 Spring Boot 3.3.6、Java 17，已有：

- `BilibiliApiClient`：基于 `RestClient` 调公开 Bilibili 接口。
- `PlatformCredential` record：内存层凭据抽象。
- `platform_credential` 表：已有 `encrypted_payload JSONB`、`expires_at`、`risk_level`、`status` 字段。
- `BilibiliPlatformAdapter.validateCredential` 目前还是占位逻辑。

### 推荐新增模块

包结构建议：

```text
com.socialmonitor.bilibili.auth
  BilibiliAuthController
  BilibiliAuthService
  BilibiliPassportClient
  BilibiliQrLoginSession
  BilibiliCookieState
  BilibiliCredentialRepository
  dto/
```

职责拆分：

| 类 | 职责 |
| --- | --- |
| `BilibiliAuthController` | 暴露扫码开始、状态、登录态状态、刷新、退出接口 |
| `BilibiliAuthService` | 编排登录会话、持久化凭据、校验和刷新 |
| `BilibiliPassportClient` | 只负责调用 passport/nav/cookie refresh 端点 |
| `BilibiliQrLoginSession` | 临时保存 `qrcode_key` 和扫码过程 Cookie |
| `BilibiliCookieState` | 封装 Cookie Map、`refresh_token`、过期时间、账号信息 |
| `BilibiliCredentialRepository` | 读写 `platform_credential` 表 |

### HTTP 客户端选择

扫码登录必须正确处理 `Set-Cookie`。当前 `BilibiliApiClient` 用 `RestClient + SimpleClientHttpRequestFactory`，并没有自动维护跨请求 CookieJar。

推荐两种方案：

1. 使用 Java 17 原生 `java.net.http.HttpClient` + `CookieManager`
   - 优点：不加依赖，天然维护 Cookie。
   - 适合 `BilibiliPassportClient` 这种小范围客户端。

2. 自己解析和拼接 Cookie
   - 每次响应读取所有 `Set-Cookie` 头，用 `HttpCookie.parse` 更新 `Map<String, HttpCookie>`。
   - 每次请求构造 `Cookie: name=value; name2=value2`。
   - 优点是可控、方便序列化；缺点是容易漏掉 domain/path/expires 细节。

建议首期采用 `HttpClient + CookieManager` 完成扫码会话，成功后把 CookieManager 中 `.bilibili.com` 相关 Cookie 转成持久化 Map。

### 临时会话缓存

登录会话不要入库。可以先用内存：

```text
ConcurrentHashMap<String, BilibiliQrLoginSession>
```

字段：

```text
loginId
qrcodeKey
qrUrl
CookieManager cookieManager
createdAt
expiresAt
lastStatus
```

清理策略：

- 每次读取状态时检查过期并移除。
- 加一个 `@Scheduled` 每分钟清理过期会话。
- 多实例部署时改成 Redis，并把 Cookie 序列化到 Redis。

### 持久化方案

复用 `platform_credential` 表。建议 `auth_type='BILIBILI_WEB_COOKIE'`。

`encrypted_payload` 存：

```json
{
  "version": 1,
  "authType": "WEB_COOKIE",
  "cookies": {
    "SESSDATA": "...",
    "bili_jct": "...",
    "DedeUserID": "...",
    "DedeUserID__ckMd5": "...",
    "sid": "..."
  },
  "refreshToken": "...",
  "account": {
    "mid": 123456,
    "uname": "账号昵称",
    "face": "https://..."
  },
  "lastValidatedAt": "2026-06-15T00:00:00+08:00",
  "lastRefreshCheckedAt": "2026-06-15T00:00:00+08:00"
}
```

`expires_at` 可从 `SESSDATA` 的 Cookie Expires 解析；如果取不到，就留空并依赖 `nav`/`cookie/info` 校验。

虽然表名叫 `encrypted_payload`，还要确认项目里是否真的加密。如果目前只是 JSONB 明文，建议先补应用层 AES-GCM 加密：

- 密钥写入项目内的 `social-data-monitor/.env.local`，由脚本加载为 `SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY`。
- 日志、异常、审计记录全部脱敏。
- 永远不要把原始 Cookie 返回给前端。

### 对现有采集客户端的接入

当前 `BilibiliApiClient` 调的是公开卡片和关系接口，不一定需要登录。后续需要登录接口时，不要全局默认附带 Cookie，建议按 endpoint 能力显式传：

```text
fetchXxx(..., Optional<BilibiliCookieState> credential)
```

或者拆出：

```text
BilibiliPublicApiClient
BilibiliAuthenticatedApiClient
```

已登录请求统一注入：

```http
Cookie: SESSDATA=...; bili_jct=...; DedeUserID=...
Referer: https://www.bilibili.com/
User-Agent: 浏览器 UA
```

写操作额外在表单里带：

```text
csrf={bili_jct}
csrf_token={bili_jct}
```

## Cookie 刷新流程

Bilibili Web 登录接口会返回 `refresh_token`。本地资料显示，自 2023 年以来 Web Cookie 可能随着敏感接口访问逐渐被要求刷新。刷新并不是简单延长 Cookie，而是一个带 `refresh_csrf` 的流程。

### 1. 检查是否需要刷新

```http
GET https://passport.bilibili.com/x/passport-login/web/cookie/info?csrf={bili_jct}
Cookie: SESSDATA=...; bili_jct=...
```

响应：

```json
{
  "code": 0,
  "data": {
    "refresh": true,
    "timestamp": 1684466082562
  }
}
```

策略：

- 每天第一次使用登录态时检查一次。
- 访问敏感接口前可检查一次。
- `code=-101` 直接标记登录态失效，提示重新扫码。

### 2. 生成 `CorrespondPath`

使用 `timestamp` 生成明文：

```text
refresh_{timestamp}
```

用资料中的 Bilibili RSA 公钥做 `RSA-OAEP + SHA-256` 加密，密文转小写十六进制，得到 `correspondPath`。

Java 可以用：

```text
Cipher.getInstance("RSA/ECB/OAEPPadding")
OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
```

注意 RSA-OAEP 有随机填充，同一个 timestamp 每次输出不同，这是正常的。

### 3. 获取 `refresh_csrf`

```http
GET https://www.bilibili.com/correspond/1/{correspondPath}
Cookie: SESSDATA=...; bili_jct=...
```

成功会返回 HTML，从：

```html
<div id="1-name">...</div>
```

取文本作为 `refresh_csrf`。如果 path 错误或过期通常返回 404。

### 4. 刷新 Cookie

```http
POST https://passport.bilibili.com/x/passport-login/web/cookie/refresh
Content-Type: application/x-www-form-urlencoded
Cookie: SESSDATA=...; bili_jct=...

csrf={bili_jct}
refresh_csrf={refresh_csrf}
source=main_web
refresh_token={old_refresh_token}
```

成功后响应头设置新 Cookie，JSON 返回新的 `refresh_token`。

### 5. 确认刷新

必须保存旧 `refresh_token`，因为确认接口要提交旧值：

```http
POST https://passport.bilibili.com/x/passport-login/web/confirm/refresh
Content-Type: application/x-www-form-urlencoded
Cookie: 新 Cookie

csrf={new_bili_jct}
refresh_token={old_refresh_token}
```

成功后：

- 保存新 Cookie。
- 保存新 `refresh_token`。
- 用 `nav` 再校验一次。

常见错误：

| code | 含义 |
| --- | --- |
| `-101` | 账号未登录，登录态已失效 |
| `-111` | CSRF 校验失败，检查 `bili_jct` |
| `86095` | `refresh_csrf` 错误，或 `refresh_token` 与 Cookie 不匹配 |

## 其他登录方式评估

### 密码登录

流程：

1. `GET /x/passport-login/captcha?source=main_web` 获取 `token`、`gt`、`challenge`。
2. 用户完成 Geetest，得到 `validate` 和 `seccode`。
3. `GET /x/passport-login/web/key` 获取 RSA 公钥和 `hash`。
4. 密码明文前拼接 `hash`，用 RSA/PKCS#1 v1.5 加密并 base64。
5. `POST /x/passport-login/web/login`，提交账号、加密密码、`token/challenge/validate/seccode`。
6. 成功后获取 Cookie 和 `refresh_token`。
7. 如果返回安全验证 URL，需要走 `safecenter` 二次短信验证。

不推荐首期实现：

- 后端会接触用户密码，安全责任明显增加。
- Geetest 和二次验证交互复杂。
- 自动化验证码会带来风控和合规风险；如果做，也只能做用户手动完成。

### 短信登录

流程：

1. 先走 captcha/Geetest。
2. `POST /x/passport-login/web/sms/send` 发送短信，得到 `captcha_key`。
3. 用户输入短信码。
4. `POST /x/passport-login/web/login/sms` 获取 Cookie 和 `refresh_token`。

比密码登录少了密码保存问题，但仍有：

- 短信发送频率限制。
- Geetest 依赖。
- 风控二次验证。

可以作为后续“无法扫码时”的人工备用方案，不建议首期主推。

### TV 扫码登录

流程：

1. `POST /x/passport-tv-login/qrcode/auth_code`，需要 appkey 签名。
2. 用返回 URL 生成二维码。
3. `POST /x/passport-tv-login/qrcode/poll`，需要 appkey 签名。
4. 成功后返回 `access_token`、`refresh_token`、`cookie_info`。

适用场景：

- 必须调用 APP/TV 端接口，接口只接受 `access_key`。

不推荐首期：

- 依赖 appkey/appsec，容易随客户端版本变化。
- `access_key` 和 appkey 签名要配套使用。
- 对 Web 监控需求来说，Web Cookie 更直接。

## 安全与合规要求

必须做：

- 明确用户授权：扫码页面写清楚“用你的 Bilibili 账号授权本系统访问需要登录的接口”。
- 后端保存登录态，前端绝不保存 `SESSDATA`、`bili_jct`、`refresh_token`。
- 所有日志脱敏：只允许输出 Cookie 名称、过期时间、UID，不能输出值。
- `platform_credential.encrypted_payload` 真正加密，至少应用层 AES-GCM。
- 提供退出/删除登录态按钮。
- 定期校验登录态，失效后自动停用相关任务，不要无限重试。
- 对 Bilibili 请求做速率限制和退避，遇到 `-352`、`-412`、`v_voucher` 标记高风险并暂停。

不应做：

- 不要绕过验证码。
- 不要把用户密码落库。
- 不要把 Cookie 放到前端 localStorage。
- 不要把一个账号的 Cookie 批量用于高频抓取。

## 建议 API 设计

后端：

```http
POST /api/bilibili/auth/qr/start
GET  /api/bilibili/auth/qr/{loginId}/status
GET  /api/bilibili/auth/status
POST /api/bilibili/auth/refresh
DELETE /api/bilibili/auth
```

状态响应：

```json
{
  "status": "WAITING",
  "message": "等待扫码",
  "expiresInSeconds": 132
}
```

成功响应：

```json
{
  "status": "SUCCESS",
  "account": {
    "mid": 123456,
    "uname": "昵称",
    "face": "https://..."
  }
}
```

前端：

- 打开登录弹窗时调用 `start`。
- 用 `qrUrl` 生成二维码。
- 每 2 秒调用 `status`。
- `SCANNED` 时显示“请在手机端确认”。
- `EXPIRED` 时显示“二维码已过期，点击刷新”。
- `SUCCESS` 时关闭弹窗并刷新账号状态。

## 测试计划

单元测试：

- 解析二维码生成响应。
- 解析轮询状态：`86101`、`86090`、`86038`、`0`、未知状态。
- 解析 `Set-Cookie`，确认 `SESSDATA`、`bili_jct`、`DedeUserID` 能保存。
- `nav` 登录态判断：`code=0/isLogin=true`、`code=-101/isLogin=false`。
- Cookie 刷新参数构造，确认旧 `refresh_token` 用在 `confirm/refresh`。
- 日志脱敏测试，确保不会输出 Cookie 值。

集成测试：

- 用 Mock HTTP Server 模拟 passport/nav/cookie refresh。
- 真实 Bilibili 扫码测试设为手动 Profile，例如 `-DmanualBilibiliLogin=true`，默认不跑。
- 真实测试产生的 Cookie 文件或数据库记录必须加入 `.gitignore`，并只在本机保存。

回归验证：

- 新增登录态后，现有公开粉丝数采集仍不受影响。
- 登录态失效时任务应标记 `AUTH_EXPIRED`，不要误判为 Bilibili 接口字段变化。
- 风控响应时任务应暂停或降频，进入人工处理。

## 分阶段实施建议

第一阶段：Web 扫码最小闭环

- 后端实现扫码开始、状态轮询、成功保存 Cookie。
- 保存到 `platform_credential`。
- `nav` 校验登录态。
- 前端加登录弹窗。

第二阶段：登录态消费

- 给需要登录的 Bilibili API 客户端注入 Cookie。
- `BilibiliPlatformAdapter.validateCredential` 改为真实 `nav` 校验。
- 采集任务遇到 `-101` 时标记凭据失效。

第三阶段：Cookie 刷新

- 实现 `cookie/info` 检查。
- 实现 `CorrespondPath`、`refresh_csrf`、`cookie/refresh`、`confirm/refresh`。
- 每日或任务前自动刷新。

第四阶段：备用登录方式

- 仅在产品需要时做短信登录。
- 密码登录尽量不做；如果必须做，密码只在一次请求内使用，不落库。
- TV 登录只在确实需要 `access_key` 的 APP 接口时做。

## 最终推荐

当前项目最稳的方案是：

1. 使用 Web 扫码登录获取 Web Cookie。
2. 后端保存 Cookie 和 `refresh_token`。
3. 用 `x/web-interface/nav` 作为登录态校验。
4. 对需要登录的接口按需注入 Cookie。
5. 后续补完整 Cookie 刷新流程。

这样能满足“拿到登录态”的目标，同时避免账号密码、短信验证码和 appkey 签名带来的复杂度。
