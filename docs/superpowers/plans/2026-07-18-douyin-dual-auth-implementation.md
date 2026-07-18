# Douyin Dual Login State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改 Bilibili 扫码模块的前提下，为 `social-data-monitor` 增加抖音官方 OAuth 扫码授权和 Playwright Web 扫码登录态的获取、完整保存、校验、查看、导出、刷新及撤销能力。

**Architecture:** Vue 只访问 Spring Boot；Spring Boot 负责编排、会话状态和 PostgreSQL 持久化；独立 Node.js + Playwright Worker 只负责浏览器上下文、二维码、完整浏览器状态捕获和新 Context 复验。抖音组件全部受 `app.douyin.auth.enabled=true` 控制，Worker 由新增启动入口单独启动。

**Tech Stack:** Java 17、Spring Boot 3.3.6、PostgreSQL/Flyway、Vue 3、TypeScript、Element Plus、Vitest 4.1.10、Node.js 20+、Playwright 1.61.1、Node 内置 HTTP/test runner。

## Global Constraints

- 首期只实现登录态基础设施，不实现主页、作品、评论、直播或其他抖音业务接口。
- 完整保留 OAuth 回调/token/user-info 原文和全部相关 Cookie、localStorage、sessionStorage、可序列化 IndexedDB、storageState、浏览器上下文及 Worker 原始结果。
- 不修改 `com.socialmonitor.bilibili/**`、`frontend/src/views/bilibili/**`、`frontend/src/api/bilibiliAuth.ts`、Flyway V1-V8、原 `dev-start`/`dev-stop` 默认行为。
- 抖音默认关闭；关闭时不注册 controller/service/worker client，不启动 Worker、不占端口、不要求 Chromium。
- 单管理员、每种认证类型最多一条 ACTIVE 凭据；切换使用 PostgreSQL 事务级锁并原子提交。
- Windows 与 Docker/Linux 均使用项目内相对配置和运行路径。
- 验证码或附加验证只报告 `USER_ACTION_REQUIRED`，不自动识别或绕过。

---

### Task 1: 配置绑定和数据库迁移

**Files:**
- Create: `social-data-monitor/backend/src/test/java/com/socialmonitor/douyin/auth/config/DouyinAuthPropertiesTests.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/config/DouyinAuthProperties.java`
- Create: `social-data-monitor/backend/src/main/resources/application-douyin.yml`
- Create: `social-data-monitor/backend/src/main/resources/db/migration/V9__douyin_auth_credential.sql`

**Interfaces:**
- Produces: `DouyinAuthProperties`，包含 enabled、oauthMode、OAuth 配置、Worker 配置、二维码 TTL/轮询/超时和独立加密密钥。
- Produces: `douyin_auth_session` 与两种抖音凭据的单活部分唯一索引。

- [ ] **Step 1: 写失败的配置绑定测试**

```java
class DouyinAuthPropertiesTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "app.douyin.auth.enabled=true",
                    "app.douyin.auth.oauth-mode=mock",
                    "app.douyin.auth.worker-base-url=http://127.0.0.1:8787"
            );

    @Test
    void bindsIsolatedDouyinSettings() {
        runner.run(context -> {
            DouyinAuthProperties value = context.getBean(DouyinAuthProperties.class);
            assertThat(value.enabled()).isTrue();
            assertThat(value.oauthMode()).isEqualTo("mock");
            assertThat(value.workerBaseUrl()).isEqualTo("http://127.0.0.1:8787");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DouyinAuthProperties.class)
    static class TestConfig {}
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd social-data-monitor/backend && .\mvnw.cmd -Dtest=DouyinAuthPropertiesTests test`

Expected: 测试编译失败，原因是 `DouyinAuthProperties` 尚不存在。

- [ ] **Step 3: 实现配置类、独立 profile 和 V9**

```java
@ConfigurationProperties(prefix = "app.douyin.auth")
public record DouyinAuthProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("disabled") String oauthMode,
        @DefaultValue("") String oauthClientKey,
        @DefaultValue("") String oauthClientSecret,
        @DefaultValue("") String oauthRedirectUri,
        @DefaultValue("user_info") String oauthScope,
        @DefaultValue("") String credentialEncryptionKey,
        @DefaultValue("http://127.0.0.1:8787") String workerBaseUrl,
        @DefaultValue("") String workerToken,
        @DefaultValue("180") int qrExpireSeconds,
        @DefaultValue("1500") int pollIntervalMs,
        @DefaultValue("5000") int connectTimeoutMs,
        @DefaultValue("30000") int requestTimeoutMs
) {}
```

`V9__douyin_auth_credential.sql` 必须插入 `douyin` 平台、创建 `douyin_auth_session`、创建状态/过期索引，并创建只覆盖 `DOUYIN_OAUTH2`/`DOUYIN_WEB_SESSION` ACTIVE 行的唯一索引。

- [ ] **Step 4: 运行配置测试和全部后端测试并确认 GREEN**

Run: `cd social-data-monitor/backend && .\mvnw.cmd test`

Expected: `DouyinAuthPropertiesTests` 与原 13 个测试全部通过。

- [ ] **Step 5: 提交任务**

```powershell
git add social-data-monitor/backend/src/main social-data-monitor/backend/src/test
git commit -m "feat: add Douyin auth configuration and schema"
```

### Task 2: 完整凭据存储和 CredentialProvider

**Files:**
- Create: `social-data-monitor/backend/src/test/java/com/socialmonitor/douyin/auth/service/DouyinCredentialCipherTests.java`
- Create: `social-data-monitor/backend/src/test/java/com/socialmonitor/douyin/auth/service/RepositoryDouyinCredentialProviderTests.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/domain/DouyinAuthConstants.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/domain/DouyinStoredCredential.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/domain/DouyinOAuthCredential.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/domain/DouyinWebSessionCredential.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/repository/DouyinCredentialRepository.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/service/DouyinCredentialCipher.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/service/DouyinCredentialProvider.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/service/RepositoryDouyinCredentialProvider.java`

**Interfaces:**
- Produces: `DouyinStoredCredential saveActive(String authType, Map<String,Object> payload, OffsetDateTime expiresAt)`。
- Produces: `Optional<DouyinStoredCredential> findActive(String authType)` 和 `void revokeActive(String authType)`。
- Produces: `DouyinOAuthCredential requireActiveOAuth()`、`DouyinWebSessionCredential requireActiveWebSession()`。

- [ ] **Step 1: 写失败的无损加密与 provider 测试**

```java
@Test
void roundTripsNestedRawPayloadWithoutDroppingFields() {
    Map<String, Object> raw = Map.of(
            "cookies", List.of(Map.of("name", "sessionid", "value", "raw-value")),
            "storageState", Map.of("origins", List.of()),
            "rawWorkerResult", Map.of("unknownField", List.of(1, 2, 3))
    );
    Map<String, Object> encrypted = cipher.encrypt(raw);
    assertThat(cipher.decrypt(objectMapper.writeValueAsString(encrypted))).isEqualTo(raw);
}
```

Provider 测试向 repository 返回包含 `accessToken` 或 `cookies/storageState` 的原始 Map，并断言强类型记录保留 `rawPayload`。

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd social-data-monitor/backend && .\mvnw.cmd -Dtest=DouyinCredentialCipherTests,RepositoryDouyinCredentialProviderTests test`

Expected: 编译失败，原因是凭据类尚不存在。

- [ ] **Step 3: 实现可逆 AES-GCM、原子仓储和 provider**

```java
public interface DouyinCredentialProvider {
    DouyinOAuthCredential requireActiveOAuth();
    DouyinWebSessionCredential requireActiveWebSession();
}

public record DouyinOAuthCredential(
        String accessToken, String refreshToken, String openId, String unionId,
        List<String> scope, OffsetDateTime expiresAt, Map<String, Object> rawPayload
) {}

public record DouyinWebSessionCredential(
        List<Map<String, Object>> cookies, Map<String, String> cookieHeadersByOrigin,
        Map<String, Object> storageState, Map<String, Object> browserContext,
        Map<String, Object> rawPayload
) {}
```

`saveActive` 在事务内调用 `pg_advisory_xact_lock(hashtext(platformId + ':' + authType))`，再执行旧 ACTIVE -> REVOKED 和新 ACTIVE 插入；插入失败时事务回滚。

- [ ] **Step 4: 运行凭据测试和全部后端测试并确认 GREEN**

Run: `cd social-data-monitor/backend && .\mvnw.cmd test`

Expected: 新凭据测试与原测试全部通过。

- [ ] **Step 5: 提交任务**

```powershell
git add social-data-monitor/backend/src
git commit -m "feat: persist complete Douyin credentials"
```

### Task 3: OAuth disabled/mock/live 闭环

**Files:**
- Create: `social-data-monitor/backend/src/test/java/com/socialmonitor/douyin/auth/service/DouyinOAuthServiceTests.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/domain/DouyinAuthSession.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/repository/DouyinAuthSessionRepository.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/client/DouyinOAuthClient.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/client/HttpDouyinOAuthClient.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/service/DouyinOAuthService.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/controller/DouyinAuthController.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/dto/DouyinOAuthStartView.java`

**Interfaces:**
- Produces: `DouyinOAuthStartView start()`、`void complete(String state, Map<String,List<String>> callback)`、`DouyinStoredCredential refresh()`。
- Consumes: `DouyinCredentialRepository.saveActive(DOUYIN_OAUTH2, payload, expiresAt)`。

- [ ] **Step 1: 写失败的 OAuth 服务测试**

```java
@Test
void mockCallbackPersistsEveryCallbackAndRawTokenField() {
    DouyinOAuthStartView start = service.start();
    service.complete(start.state(), Map.of(
            "code", List.of("raw-code"),
            "state", List.of(start.state()),
            "provider_extra", List.of("keep-me")
    ));
    verify(credentials).saveActive(eq("DOUYIN_OAUTH2"), argThat(payload ->
            ((Map<?, ?>) payload.get("callbackParameters")).containsKey("provider_extra")
                    && payload.containsKey("rawTokenResponse")), any());
}
```

另测 disabled 拒绝、state 不匹配拒绝、live 委托客户端、刷新失败不撤销当前凭据。

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd social-data-monitor/backend && .\mvnw.cmd -Dtest=DouyinOAuthServiceTests test`

Expected: 编译失败，原因是 OAuth 服务尚不存在。

- [ ] **Step 3: 实现 OAuth 会话、HTTP 客户端和路由**

```java
public interface DouyinOAuthClient {
    Map<String, Object> exchangeCode(String code);
    Map<String, Object> refreshAccessToken(String refreshToken);
    Map<String, Object> renewRefreshToken(String refreshToken);
    Map<String, Object> fetchUserInfo(String accessToken, String openId);
}
```

live URL 使用 `https://open.douyin.com/platform/oauth/connect/`；token、refresh、renew 和 userinfo 分别使用官方 endpoint。所有响应先作为 Map 原样保存，再读取已知字段。

- [ ] **Step 4: 运行 OAuth 测试和全部后端测试并确认 GREEN**

Run: `cd social-data-monitor/backend && .\mvnw.cmd test`

Expected: OAuth mock/live 编排测试和原测试全部通过。

- [ ] **Step 5: 提交任务**

```powershell
git add social-data-monitor/backend/src
git commit -m "feat: add Douyin OAuth login flow"
```

### Task 4: Playwright Worker 浏览器态捕获

**Files:**
- Create: `social-data-monitor/douyin-worker/package.json`
- Create: `social-data-monitor/douyin-worker/package-lock.json`
- Create: `social-data-monitor/douyin-worker/src/config.js`
- Create: `social-data-monitor/douyin-worker/src/page-state.js`
- Create: `social-data-monitor/douyin-worker/src/session-manager.js`
- Create: `social-data-monitor/douyin-worker/src/playwright-driver.js`
- Create: `social-data-monitor/douyin-worker/src/http-server.js`
- Create: `social-data-monitor/douyin-worker/src/server.js`
- Create: `social-data-monitor/douyin-worker/test/page-state.test.js`
- Create: `social-data-monitor/douyin-worker/test/session-manager.test.js`
- Create: `social-data-monitor/douyin-worker/test/http-server.test.js`

**Interfaces:**
- Produces internal API: health、create session、QR PNG、status、consume、delete、validate。
- Produces bundle keys: `cookies`、`cookieHeadersByOrigin`、`origins`、`storageState`、`browserContext`、`rawWorkerResult`、timestamps。

- [ ] **Step 1: 写失败的状态识别、会话和 HTTP 测试**

```js
test('classifies captcha as user action instead of bypassing it', () => {
  assert.equal(classifyPageState({ title: '验证码中间页', text: '请完成下列验证后继续' }), 'USER_ACTION_REQUIRED')
})

test('consume returns every field from the validated bundle', async () => {
  const manager = new SessionManager({ driver: fakeDriverWithBundle(rawBundle), ttlSeconds: 180 })
  const started = await manager.create()
  await manager.status(started.workerSessionId)
  assert.deepEqual((await manager.consume(started.workerSessionId)).bundle, rawBundle)
})
```

- [ ] **Step 2: 运行 Worker 测试并确认 RED**

Run: `cd social-data-monitor/douyin-worker && npm test`

Expected: `ERR_MODULE_NOT_FOUND`，因为 Worker 模块尚不存在。

- [ ] **Step 3: 实现 Worker 最小闭环**

```js
export const INTERNAL_ROUTES = Object.freeze([
  'GET /internal/v1/health',
  'POST /internal/v1/login-sessions',
  'GET /internal/v1/login-sessions/:id/qr',
  'GET /internal/v1/login-sessions/:id/status',
  'POST /internal/v1/login-sessions/:id/consume',
  'DELETE /internal/v1/login-sessions/:id',
  'POST /internal/v1/web-sessions/validate'
])

export const TERMINAL_STATES = new Set([
  'SUCCESS', 'EXPIRED', 'USER_ACTION_REQUIRED', 'FAILED'
])
```

Playwright 使用全新 Context 复验；检测验证码时返回 `USER_ACTION_REQUIRED`。Cookie 保存不使用白名单，`storageState({ indexedDB: true })` 与 sessionStorage 合并后返回。

- [ ] **Step 4: 运行 Worker 测试并确认 GREEN**

Run: `cd social-data-monitor/douyin-worker && npm test`

Expected: 所有 Node tests 通过且无未处理 Promise。

- [ ] **Step 5: 提交任务**

```powershell
git add social-data-monitor/douyin-worker
git commit -m "feat: add Douyin Playwright login worker"
```

### Task 5: Spring Web 扫码编排和凭据管理 API

**Files:**
- Create: `social-data-monitor/backend/src/test/java/com/socialmonitor/douyin/auth/service/DouyinWebAuthServiceTests.java`
- Create: `social-data-monitor/backend/src/test/java/com/socialmonitor/douyin/worker/client/HttpDouyinWorkerClientTests.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/worker/client/DouyinWorkerClient.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/worker/client/HttpDouyinWorkerClient.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/worker/dto/WorkerHealth.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/worker/dto/WorkerSessionStart.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/worker/dto/WorkerQrImage.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/worker/dto/WorkerStatus.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/worker/dto/WorkerConsume.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/worker/dto/WorkerValidation.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/service/DouyinWebAuthService.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/service/DouyinCredentialService.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/dto/DouyinAuthStatusView.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/dto/DouyinCredentialFullView.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/dto/DouyinQrStartView.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/dto/DouyinQrStatusView.java`
- Create: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/dto/DouyinValidationView.java`
- Modify: `social-data-monitor/backend/src/main/java/com/socialmonitor/douyin/auth/controller/DouyinAuthController.java`

**Interfaces:**
- Produces: `POST /api/douyin/auth/web/qr/start`、`GET /api/douyin/auth/web/qr/{loginId}/image`、`GET /api/douyin/auth/web/qr/{loginId}/status`、`POST /api/douyin/auth/web/validate`、`GET /api/douyin/auth/status`、两种 credential/export/delete endpoint。
- Consumes Worker internal API with `X-Worker-Token` when configured.

- [ ] **Step 1: 写失败的 Web 扫码服务测试**

```java
@Test
void successfulWorkerSessionPersistsUnmodifiedBundleOnce() {
    when(worker.status("worker-1")).thenReturn(new WorkerStatus("SUCCESS", "ok", Map.of()));
    when(worker.consume("worker-1")).thenReturn(new WorkerConsume(rawBundle));
    DouyinQrStatusView result = service.poll(loginId);
    assertThat(result.status()).isEqualTo("SUCCESS");
    verify(credentials, times(1)).saveActive("DOUYIN_WEB_SESSION", rawBundle, null);
}
```

另测二维码字节透传、EXPIRED、USER_ACTION_REQUIRED、consume 失败保留旧凭据、validate 创建新 ACTIVE 历史行。

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd social-data-monitor/backend && .\mvnw.cmd -Dtest=DouyinWebAuthServiceTests,HttpDouyinWorkerClientTests test`

Expected: 编译失败，原因是 Worker client/Web service 尚不存在。

- [ ] **Step 3: 实现 Worker client、Web 服务和控制器**

```java
public interface DouyinWorkerClient {
    WorkerHealth health();
    WorkerSessionStart start(int expiresInSeconds);
    WorkerQrImage qr(String workerSessionId);
    WorkerStatus status(String workerSessionId);
    WorkerConsume consume(String workerSessionId);
    void delete(String workerSessionId);
    WorkerValidation validate(Map<String, Object> bundle);
}
```

只有 Worker 复验成功的 bundle 才能传给 `saveActive`；失败或用户操作状态只更新 `douyin_auth_session.raw_result_json`。

- [ ] **Step 4: 运行后端全测并确认 GREEN**

Run: `cd social-data-monitor/backend && .\mvnw.cmd test`

Expected: 新 Web 流程测试与 Bilibili 13 个基线测试全部通过。

- [ ] **Step 5: 提交任务**

```powershell
git add social-data-monitor/backend/src
git commit -m "feat: orchestrate Douyin web QR login"
```

### Task 6: Vue 抖音登录态页面

**Files:**
- Modify: `social-data-monitor/frontend/package.json`
- Modify: `social-data-monitor/frontend/package-lock.json`
- Modify: `social-data-monitor/frontend/src/router/index.ts`
- Modify: `social-data-monitor/frontend/src/layouts/MainLayout.vue`
- Modify: `social-data-monitor/frontend/src/env.d.ts`
- Create: `social-data-monitor/frontend/src/api/douyinAuth.ts`
- Create: `social-data-monitor/frontend/src/views/douyin/DouyinView.vue`
- Create: `social-data-monitor/frontend/src/views/douyin/components/DouyinOAuthPanel.vue`
- Create: `social-data-monitor/frontend/src/views/douyin/components/DouyinWebAuthPanel.vue`
- Create: `social-data-monitor/frontend/src/views/douyin/components/DouyinQrLoginDialog.vue`
- Create: `social-data-monitor/frontend/src/views/douyin/components/DouyinCredentialDrawer.vue`
- Create: `social-data-monitor/frontend/src/views/douyin/useQrPolling.ts`
- Create: `social-data-monitor/frontend/src/views/douyin/useQrPolling.test.ts`

**Interfaces:**
- Produces `/douyin` 页面；仅 `VITE_DOUYIN_ENABLED=true` 时注册菜单和路由。
- Consumes backend APIs only; QR image URL points to Spring, never Worker.

- [ ] **Step 1: 写失败的无并发轮询测试**

```ts
it('never starts a second request while polling is in flight', async () => {
  let resolve!: (value: DouyinQrStatusView) => void
  const fetchStatus = vi.fn(() => new Promise<DouyinQrStatusView>(r => { resolve = r }))
  const poller = createQrPoller(fetchStatus, () => undefined)
  const first = poller.pollNow('login-id')
  const second = poller.pollNow('login-id')
  expect(fetchStatus).toHaveBeenCalledTimes(1)
  resolve({ status: 'WAITING', message: 'wait' })
  await Promise.all([first, second])
})
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd social-data-monitor/frontend && npm test -- --run`

Expected: 测试命令或模块失败，因为 Vitest/composable 尚未加入。

- [ ] **Step 3: 实现 API、轮询和页面组件**

```ts
export type DouyinQrStatus = 'STARTING' | 'WAITING' | 'SCANNED' | 'VALIDATING' |
  'SUCCESS' | 'EXPIRED' | 'USER_ACTION_REQUIRED' | 'FAILED'

export interface DouyinCredentialFull {
  credentialId: number
  authType: 'DOUYIN_OAUTH2' | 'DOUYIN_WEB_SESSION'
  status: string
  expiresAt?: string
  rawPayload: Record<string, unknown>
}
```

二维码弹窗终态、关闭或组件卸载时停止轮询；凭据抽屉提供分组 JSON、复制全部和下载原始 JSON。

- [ ] **Step 4: 运行前端测试、类型检查和构建并确认 GREEN**

Run: `cd social-data-monitor/frontend && npm test -- --run && npm run build`

Expected: Vitest 通过，`vue-tsc` 与 Vite build 成功。

- [ ] **Step 5: 提交任务**

```powershell
git add social-data-monitor/frontend
git commit -m "feat: add Douyin login state console"
```

### Task 7: Windows、Docker/Linux 和项目文档接入

**Files:**
- Modify: `social-data-monitor/.env.example`
- Modify: `social-data-monitor/.gitignore`
- Modify: `social-data-monitor/README.md`
- Create: `social-data-monitor/scripts/dev-start-douyin.cmd`
- Create: `social-data-monitor/scripts/dev-start-douyin.ps1`
- Create: `social-data-monitor/scripts/dev-stop-douyin.cmd`
- Create: `social-data-monitor/scripts/dev-stop-douyin.ps1`
- Create: `social-data-monitor/scripts/dev-douyin-worker.ps1`
- Create: `social-data-monitor/deploy/douyin/compose.yml`
- Create: `social-data-monitor/deploy/douyin/backend.Dockerfile`
- Create: `social-data-monitor/deploy/douyin/frontend.Dockerfile`
- Create: `social-data-monitor/deploy/douyin/worker.Dockerfile`
- Create: `social-data-monitor/deploy/douyin/nginx.conf`

**Interfaces:**
- Produces: `scripts/dev-start-douyin.cmd`/`dev-stop-douyin.cmd`，保留原脚本不动。
- Produces: Compose 中 PostgreSQL、backend、frontend、worker 四服务。

- [ ] **Step 1: 写可执行配置验收断言**

```powershell
$required = @(
  'SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED',
  'SOCIAL_MONITOR_DOUYIN_OAUTH_MODE',
  'SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL',
  'VITE_DOUYIN_ENABLED'
)
$envText = Get-Content .env.example -Raw
$required | ForEach-Object { if (-not $envText.Contains($_)) { throw "missing $_" } }
```

- [ ] **Step 2: 运行断言并确认 RED**

Run: 在 `social-data-monitor` 执行上述 PowerShell。

Expected: 以 `missing SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED` 失败。

- [ ] **Step 3: 实现独立启动/停止、容器和 README**

Windows 启动脚本加载项目内 `.env.local`、设置 `SPRING_PROFILES_ACTIVE=dev,douyin`、设置 Playwright 浏览器目录为 `.dev-tools/playwright`，启动 8787 Worker 后复用原三服务启动流程。停止脚本只停止属于本项目的 8787 进程，再调用原停止流程。

- [ ] **Step 4: 运行配置断言和可用的 Compose 校验并确认 GREEN**

Run: 重跑 PowerShell 断言；若 Docker CLI 存在，再运行 `docker compose -f deploy/douyin/compose.yml config`。

Expected: 配置断言成功；Docker 可用时 Compose config 退出码为 0。

- [ ] **Step 5: 提交任务**

```powershell
git add social-data-monitor/.env.example social-data-monitor/.gitignore social-data-monitor/README.md social-data-monitor/scripts social-data-monitor/deploy
git commit -m "docs: add Douyin auth deployment entrypoints"
```

### Task 8: 集成验证和零回归收口

**Files:**
- Verify: `social-data-monitor/backend/src/**`
- Verify: `social-data-monitor/frontend/src/**`
- Verify: `social-data-monitor/douyin-worker/**`
- Verify: `social-data-monitor/scripts/**`
- Verify: `social-data-monitor/deploy/douyin/**`

**Interfaces:**
- Verifies complete feature and original Bilibili behavior.

- [ ] **Step 1: 安装 Worker Chromium 并运行三套自动化验证**

Run:

```powershell
cd social-data-monitor/douyin-worker
npm ci
npx playwright install chromium
npm test
cd ../backend
.\mvnw.cmd test
cd ../frontend
npm ci
npm test -- --run
npm run build
```

Expected: Worker、后端、前端测试全部通过；前端生产构建成功。

- [ ] **Step 2: 启动 Worker 并验证内部 HTTP 契约**

Run: 启动 Worker 后请求 `/internal/v1/health`，创建一次会话，并轮询到 `WAITING`、`USER_ACTION_REQUIRED` 或二维码可用状态。

Expected: health 返回 `UP`；真实抖音若触发验证码，明确返回 `USER_ACTION_REQUIRED` 而不是尝试绕过。

- [ ] **Step 3: 验证默认关闭和 Bilibili 零回归**

Run: 不激活 `douyin` profile 再运行后端全测；运行 `git diff 41bd56b --name-only`。

Expected: Bilibili 测试仍通过；diff 不含 `backend/src/main/java/com/socialmonitor/bilibili/**`、`frontend/src/views/bilibili/**`、`frontend/src/api/bilibiliAuth.ts` 或 V1-V8。

- [ ] **Step 4: 运行格式和工作区检查**

Run: `git diff --check && git status --short`

Expected: 无空白错误；只存在本计划范围内的提交或明确的构建忽略产物。

- [ ] **Step 5: 处理经测试证明的收口问题**

如没有失败则不创建空提交；如有失败，回到引入该行为的任务，先增加或保留复现测试，再修复并重复该任务的精确测试与提交步骤。
