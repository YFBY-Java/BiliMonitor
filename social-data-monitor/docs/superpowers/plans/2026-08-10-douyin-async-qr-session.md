# Douyin Async QR Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Douyin QR login creation return `STARTING` in less than the frontend's 10-second timeout, then transition to `WAITING` through polling before requesting the QR image.

**Architecture:** The isolated Node/Playwright Worker registers an in-memory session before Chromium initialization and owns a caught background initialization promise. Spring Boot preserves the Worker's `STARTING` status in the existing database session and API response. The Douyin-only Vue dialog polls immediately and requests the QR image only while status is `WAITING`.

**Tech Stack:** Node.js 20 test runner, Playwright 1.61.1, Java 20 / Spring Boot 3.3.6 / JUnit 5 / Mockito, Vue 3 / TypeScript / Vitest.

## Global Constraints

- Keep public paths and DTO fields unchanged: `POST /api/douyin/auth/web/qr/start`, QR image/status paths, and Worker `/internal/v1/login-sessions` paths.
- Keep the shared Axios timeout at exactly `10000` ms; do not modify `frontend/src/api/http.ts`.
- Do not modify `frontend/src/views/bilibili/**`, `frontend/src/views/bilibili-live/**`, `frontend/src/api/bilibili*.ts`, `backend/src/main/java/com/socialmonitor/bilibili/**`, Bilibili migrations, Bilibili configuration, or Bilibili credentials.
- Preserve existing Douyin QR recognition, QR TTL, credential consume/validation, complete storage, encryption, and atomic credential-switch behavior.
- Initialization failures must become pollable `FAILED` states; initializing sessions removed, expired, or closed must close any late-created Playwright handle exactly once and must not produce unhandled promise rejections.
- Baseline on 2026-08-10: Worker 13/13, backend 64/64, frontend 8/8.

---

### Task 1: Make Worker session initialization asynchronous and lifecycle-safe

**Files:**
- Modify: `douyin-worker/test/session-manager.test.js`
- Modify: `douyin-worker/src/session-manager.js`

**Interfaces:**
- Consumes: `driver.createSession({ workerSessionId, createdAt, expiresAt }) -> Promise<handle>` and the existing handle methods `qr()`, `status()`, `close()`.
- Produces: `SessionManager.create() -> Promise<{ workerSessionId, status: 'STARTING', createdAt, expiresAt }>` that resolves before the driver promise; `status()` returns cached `STARTING` or `FAILED` while no handle exists; `qr()` throws `WorkerError('SESSION_NOT_READY', ..., 409)` while initialization is pending.

- [ ] **Step 1: Add a deferred helper and failing immediate-create/status/QR tests**

Add this helper near the top of `session-manager.test.js`:

```js
function deferred() {
  let resolve
  let reject
  const promise = new Promise((next, fail) => {
    resolve = next
    reject = fail
  })
  return { promise, resolve, reject }
}
```

Add tests that race `manager.create()` against `setImmediate`, assert `STARTING` without resolving the driver, and assert QR readiness is a retryable 409:

```js
test('registers a starting session before browser initialization completes', async () => {
  const pending = deferred()
  const manager = new SessionManager({
    driver: { createSession: () => pending.promise, validateBundle: async () => ({ valid: false }) },
    ttlSeconds: 180,
    now: () => 1_000
  })

  const raced = await Promise.race([
    manager.create().then(value => ({ kind: 'started', value })),
    new Promise(resolve => setImmediate(() => resolve({ kind: 'blocked' })))
  ])

  assert.equal(raced.kind, 'started')
  assert.equal(raced.value.status, 'STARTING')
  assert.equal((await manager.status(raced.value.workerSessionId)).status, 'STARTING')
  await assert.rejects(() => manager.qr(raced.value.workerSessionId), error =>
    error instanceof WorkerError &&
    error.code === 'SESSION_NOT_READY' &&
    error.statusCode === 409)
})
```

- [ ] **Step 2: Run the targeted test and verify RED**

Run:

```powershell
Set-Location douyin-worker
node --test test/session-manager.test.js
```

Expected: FAIL at `raced.kind`, with actual value `blocked`, because current `create()` awaits `driver.createSession()`.

- [ ] **Step 3: Register the session before initialization and guard handle access**

Change `create()` to create and store this session shape before calling the driver:

```js
const session = {
  handle: undefined,
  initialization: undefined,
  createdAtMs,
  expiresAtMs,
  status: { status: 'STARTING', message: 'Browser session is starting' },
  bundle: undefined,
  closed: false,
  closePromise: undefined
}
this.sessions.set(workerSessionId, session)
session.initialization = this.#initialize(session, {
  workerSessionId,
  createdAt: new Date(createdAtMs).toISOString(),
  expiresAt: new Date(expiresAtMs).toISOString()
})
```

Add a private `#initialize(session, options)` method that catches every rejection, stores `FAILED` plus the error message when the session is still active, assigns a successful handle only while active, and closes a late handle immediately when `session.closed` is already true. In `status()`, return the cached status while `session.handle` is absent. In `qr()`, after expiry checking, throw:

```js
throw new WorkerError('SESSION_NOT_READY', 'The login session is still initializing', 409)
```

when no handle exists.

- [ ] **Step 4: Run the targeted test and verify GREEN**

Run `node --test test/session-manager.test.js`.

Expected: all session-manager tests pass with no unhandled rejection warning.

- [ ] **Step 5: Add failing initialization-failure and late-close tests**

Add separate tests that:

1. reject the deferred driver, wait one event-loop turn, and assert `status()` is `FAILED` with the original message;
2. call `remove()` before resolving the deferred driver, resolve with a handle whose `close()` increments a counter, then assert the counter is exactly `1` and subsequent `status()` throws `SESSION_NOT_FOUND`;
3. expire a pending session through the injected clock and `cleanupExpired()`, then resolve the driver and assert its handle closes exactly once;
4. call `closeAll()` while pending, then resolve the driver and assert its handle closes exactly once.

Use `await new Promise(resolve => setImmediate(resolve))` after resolving/rejecting the deferred promise so the caught initialization continuation runs before assertions.

- [ ] **Step 6: Run the targeted tests and verify RED**

Run `node --test test/session-manager.test.js`.

Expected before cleanup changes: one or more late-handle close assertions fail or a no-handle close throws.

- [ ] **Step 7: Make close/remove/expiry/closeAll idempotent for pending sessions**

Update `#close(session)` so it sets `session.closed = true` before awaiting work, returns the existing `closePromise` for duplicate calls, and tolerates `session.handle` being absent. A handle assigned by `#initialize` after closure must be closed by `#initialize` and never stored in the map. Keep `remove()`, `cleanupExpired()`, and `closeAll()` public behavior unchanged.

- [ ] **Step 8: Verify the Worker suite**

Run:

```powershell
Set-Location douyin-worker
npm test
```

Expected: all Worker tests pass, including the new asynchronous lifecycle coverage.

- [ ] **Step 9: Commit Task 1**

```powershell
git add douyin-worker/src/session-manager.js douyin-worker/test/session-manager.test.js
git commit -m "fix: initialize Douyin QR sessions asynchronously"
```

### Task 2: Preserve Worker `STARTING` through Spring Boot

**Files:**
- Modify: `backend/src/test/java/com/socialmonitor/douyin/auth/repository/DouyinAuthSessionRepositoryTests.java`
- Modify: `backend/src/test/java/com/socialmonitor/douyin/auth/service/DouyinWebAuthServiceTests.java`
- Modify: `backend/src/main/java/com/socialmonitor/douyin/auth/repository/DouyinAuthSessionRepository.java`
- Modify: `backend/src/main/java/com/socialmonitor/douyin/auth/service/DouyinWebAuthService.java`

**Interfaces:**
- Consumes: existing `WorkerSessionStart(workerSessionId, status, expiresAt, rawResult)`.
- Produces: unchanged `DouyinQrStartView`, with `status` equal to normalized Worker status; repository attachment changes only `worker_session_id`, raw result, and `updated_at`.

- [ ] **Step 1: Write failing service and repository tests**

In `startsDatabaseSessionThenAttachesWorkerSession`, change:

```java
assertThat(result.status()).isEqualTo("STARTING");
```

Add a polling regression proving the database session naturally advances later:

```java
@Test
void pollingAdvancesStartingSessionToWaiting() {
    UUID loginId = UUID.randomUUID();
    when(sessions.findByLoginId(loginId))
            .thenReturn(Optional.of(session(loginId, "STARTING", NOW.plusSeconds(180))));
    when(worker.status("worker-1"))
            .thenReturn(new WorkerStatus("WAITING", "scan", Map.of("qrAvailable", true)));

    var result = service.poll(loginId);

    assertThat(result.status()).isEqualTo("WAITING");
    verify(sessions).updateStatus(loginId, "WAITING", Map.of("qrAvailable", true));
}
```

Add an attachment-compensation regression using `doThrow(new RuntimeException("db attach failed"))` on `sessions.attachWorkerSession(...)`, then assert `service.start()` propagates the failure and `verify(worker).delete("worker-1")` is called. Import Mockito `doThrow`.

Add a repository test:

```java
@Test
void attachingWorkerSessionPreservesStartingStatus() {
    UUID loginId = UUID.randomUUID();

    repository.attachWorkerSession(loginId, "worker-1", Map.of("status", "STARTING"));

    verify(jdbcTemplate).update(
            contains("worker_session_id = :workerSessionId"),
            any(MapSqlParameterSource.class)
    );
    verify(jdbcTemplate, never()).update(
            contains("status = 'WAITING'"),
            any(MapSqlParameterSource.class)
    );
}
```

Import `never` from Mockito.

- [ ] **Step 2: Run targeted backend tests and verify RED**

Run:

```powershell
Set-Location backend
.\mvnw.cmd "-Dtest=DouyinWebAuthServiceTests,DouyinAuthSessionRepositoryTests" test
```

Expected: service assertion reports `WAITING` instead of `STARTING`, and the repository negative verification reports the SQL still contains `status = 'WAITING'`.

- [ ] **Step 3: Implement the minimal status propagation change**

In `DouyinAuthSessionRepository.attachWorkerSession`, remove only:

```sql
status = 'WAITING',
```

In `DouyinWebAuthService.start()`, replace the hard-coded response status with:

```java
normalizeStatus(started.status())
```

Do not change controller paths, client timeouts, DTOs, polling, consume, validation, or compensation.

- [ ] **Step 4: Verify targeted and full backend suites**

Run the targeted command above, then `.\mvnw.cmd test`.

Expected: targeted tests pass; full backend result is at least 64 existing tests plus the new test, with zero failures/errors. Existing Bilibili tests must still pass.

- [ ] **Step 5: Commit Task 2**

```powershell
git add backend/src/main/java/com/socialmonitor/douyin/auth/repository/DouyinAuthSessionRepository.java backend/src/main/java/com/socialmonitor/douyin/auth/service/DouyinWebAuthService.java backend/src/test/java/com/socialmonitor/douyin/auth/repository/DouyinAuthSessionRepositoryTests.java backend/src/test/java/com/socialmonitor/douyin/auth/service/DouyinWebAuthServiceTests.java
git commit -m "fix: preserve starting Douyin QR status"
```

### Task 3: Gate QR image requests on `WAITING` in the Douyin dialog

**Files:**
- Create: `frontend/src/views/douyin/qrImagePolicy.ts`
- Create: `frontend/src/views/douyin/qrImagePolicy.test.ts`
- Modify: `frontend/src/views/douyin/components/DouyinQrLoginDialog.vue`

**Interfaces:**
- Consumes: existing `DouyinQrStatus` union and existing image/status API functions.
- Produces: `canRequestDouyinQrImage(status?: DouyinQrStatus): boolean`, true only for `WAITING`.

- [ ] **Step 1: Write the failing pure-policy test**

Create `qrImagePolicy.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { canRequestDouyinQrImage } from './qrImagePolicy'
import type { DouyinQrStatus } from '@/api/douyinAuth'

describe('Douyin QR image request policy', () => {
  it('allows image requests only while the worker is waiting for a scan', () => {
    expect(canRequestDouyinQrImage('WAITING')).toBe(true)
    const blocked: Array<DouyinQrStatus | undefined> = [
      undefined,
      'STARTING',
      'SCANNED',
      'VALIDATING',
      'SUCCESS',
      'EXPIRED',
      'USER_ACTION_REQUIRED',
      'FAILED'
    ]
    for (const status of blocked) expect(canRequestDouyinQrImage(status)).toBe(false)
  })
})
```

- [ ] **Step 2: Run the policy test and verify RED**

Run:

```powershell
Set-Location frontend
npx vitest run src/views/douyin/qrImagePolicy.test.ts
```

Expected: FAIL because `./qrImagePolicy` does not exist.

- [ ] **Step 3: Add the minimal policy implementation**

Create `qrImagePolicy.ts`:

```ts
import type { DouyinQrStatus } from '@/api/douyinAuth'

export function canRequestDouyinQrImage(status?: DouyinQrStatus): boolean {
  return status === 'WAITING'
}
```

- [ ] **Step 4: Run the policy test and verify GREEN**

Run the targeted Vitest command. Expected: one test file and one test pass.

- [ ] **Step 5: Apply the policy to both initial response and polling**

In `DouyinQrLoginDialog.vue`:

- import `canRequestDouyinQrImage`;
- replace `next.status === 'WAITING'` in `onResult` with the policy;
- set the initial local status to `started.status`, not a hard-coded `STARTING`;
- start polling immediately, but call `loadQrImage(false, requestGeneration)` only if the policy permits the initial status;
- make `loadQrImage()` return before issuing an HTTP request whenever the current status is not permitted, even when `force` is true;
- render the refresh-image button only when the policy permits the current status.

The initial state assignment must retain the existing DTO layout:

```ts
status.value = {
  loginId: started.loginId,
  status: started.status,
  message: started.status === 'WAITING'
    ? '抖音登录二维码已准备好，请使用抖音 App 扫码。'
    : '浏览器会话已创建，正在定位抖音登录二维码。',
  expiresInSeconds: started.expiresInSeconds,
  rawResult: started.rawResult
}
```

- [ ] **Step 6: Verify frontend tests, types, and production build**

Run:

```powershell
Set-Location frontend
npm test
npm run typecheck
npm run build
```

Expected: all Vitest files pass, `vue-tsc` exits 0, and Vite production build exits 0. Confirm `frontend/src/api/http.ts` has no diff.

- [ ] **Step 7: Commit Task 3**

```powershell
git add frontend/src/views/douyin/qrImagePolicy.ts frontend/src/views/douyin/qrImagePolicy.test.ts frontend/src/views/douyin/components/DouyinQrLoginDialog.vue
git commit -m "fix: wait for Douyin QR image readiness"
```

### Task 4: Full regression, restart, and live QR acceptance

**Files:**
- Verify only: `scripts/dev-stop.cmd`
- Verify only: `scripts/dev-start.cmd`
- Verify only: `.dev-data/**`

**Interfaces:**
- Consumes: ports `5432`, `8080`, `8787`, `5173`; Worker health `/internal/v1/health`; backend health `/actuator/health`; public frontend routes `/bilibili` and `/douyin`.
- Produces: a restarted current checkout and runtime evidence that QR start returns before 10 seconds as `STARTING`, later transitions to `WAITING`, and the image endpoint succeeds only after readiness.

- [ ] **Step 1: Run static orchestration regression tests**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\tests\dev-system-config.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\tests\dev-orchestration.test.ps1
```

Expected: every PowerShell assertion passes.

- [ ] **Step 2: Stop the current stack and verify application ports are released**

Run `.\scripts\dev-stop.cmd`, then inspect listening TCP endpoints for `8080`, `8787`, and `5173`. PostgreSQL `5432` may remain under the existing script's documented lifecycle; do not stop unrelated system PostgreSQL services manually.

- [ ] **Step 3: Start the current checkout**

Use process-only `JAVA_HOME=E:\java\jdk20` if the inherited value is invalid, then run:

```powershell
.\scripts\dev-start.cmd -TimeoutSeconds 180
```

Expected: startup reports backend health, Worker health, frontend `/bilibili`, and frontend `/douyin` as ready.

- [ ] **Step 4: Verify health and Bilibili isolation**

Read-only checks:

- `GET http://127.0.0.1:8080/actuator/health` returns `UP`;
- `GET http://127.0.0.1:8787/internal/v1/health` returns `UP`;
- `GET http://127.0.0.1:5173/bilibili` and `/douyin` return HTTP 200;
- `GET /api/bilibili/auth/status` remains reachable; do not invoke Bilibili revoke, refresh, or QR mutation endpoints.

- [ ] **Step 5: Verify the original timeout symptom is gone**

From the running UI, open the Douyin QR dialog and record:

1. `POST /api/douyin/auth/web/qr/start` completes in less than `10000` ms and returns a non-empty `loginId` with `status: STARTING`;
2. status polling remains nonblocking and transitions from `STARTING` to `WAITING`;
3. the QR image request is sent only after `WAITING` and returns `image/png`;
4. no `timeout of 10000ms exceeded` is shown;
5. a retry does not create an unreachable session due to a frontend timeout.

- [ ] **Step 6: Review final diff boundaries**

Run `git diff 050719b --name-only` and verify every changed production/test file is listed by Tasks 1-3 or is this plan document. Reject any diff under the prohibited Bilibili/shared HTTP/migration paths.
