# 抖音扫码会话异步初始化设计

## 背景与根因

当前抖音 Web 扫码登录的会话创建是同步链路：前端调用 Spring Boot，Spring Boot 同步调用 Douyin Worker，Worker 再同步等待 Playwright 启动浏览器、加载抖音页面并定位二维码后才返回。

前端共享 Axios 实例的请求超时为 10 秒，Spring Boot 调用 Worker 的请求超时为 30 秒，而当前本机最近三次 Playwright 准备分别耗时 19.191 秒、12.352 秒和 13.162 秒。因此前端先显示 `timeout of 10000ms exceeded`，但后端稍后仍把这些会话记录为 `WAITING`。页面没有收到 `loginId`，无法接管已经创建的会话，重复点击还会产生额外的无主会话。

## 目标

- `POST /api/douyin/auth/web/qr/start` 不再等待外部抖音页面加载完成，而是在 Worker 接受并登记会话后立即返回。
- 页面通过已有的 `STARTING -> WAITING -> SCANNED -> VALIDATING -> SUCCESS` 状态流展示真实进度。
- Playwright 初始化失败时，通过状态轮询返回明确的 `FAILED` 消息，而不是让前端得到通用网络超时。
- 会话在初始化、关闭、过期和删除并发发生时不泄漏浏览器上下文，也不产生未处理的 Promise rejection。
- 保持现有抖音 API 路径、二维码有效期、凭据捕获和保存格式兼容。
- 修改严格限定在抖音模块；Bilibili 页面、接口、服务、数据库表、凭据和配置保持不变。

## 非目标

- 不提高共享 Axios 的全局 10 秒超时。
- 不修改 Bilibili 相关前端、Java 包、数据库迁移或运行配置。
- 不改变抖音二维码识别规则、登录态捕获内容或凭据加密格式。
- 不尝试绕过抖音验证码、风控或用户确认步骤。
- 不删除已有的历史 `douyin_auth_session` 记录。
- 不在本次修复中新增后台数据库清理任务；数据库会话继续遵循现有的状态与过期语义。

## 总体数据流

1. 抖音前端调用 `POST /api/douyin/auth/web/qr/start`。
2. Spring Boot 创建状态为 `STARTING` 的 `douyin_auth_session` 数据库记录。
3. Spring Boot 调用 Worker 的 `POST /internal/v1/login-sessions`。
4. Worker 先在内存中登记会话和初始化 Promise，随即返回 `workerSessionId`、`STARTING` 和过期时间。
5. Worker 在后台启动或复用 Chromium、创建 Context、打开页面并寻找二维码。
6. Spring Boot 把 `workerSessionId` 附加到数据库记录，状态仍保持 `STARTING`，然后立即把 `loginId` 返回前端。
7. 前端开始轮询状态。初始化未完成时得到 `STARTING`；准备完成后得到 `WAITING`，此时再请求二维码图片。
8. 后续扫码、复验、凭据消费和完整保存继续使用现有流程。

## Worker 设计

### 会话结构

`SessionManager` 在调用 `driver.createSession()` 前先写入内存会话。会话需要区分：

- `handle`：Playwright 初始化完成前为空，完成后指向实际登录会话句柄。
- `initialization`：始终被捕获处理的初始化 Promise。
- `status`：初始为 `STARTING`；初始化失败时变为 `FAILED`。
- `closed`：删除、过期或服务关闭后为真，防止延迟完成的初始化重新激活会话。

`create()` 只负责登记会话并启动后台初始化，不等待外部页面。它返回的 Promise 应在本地内存操作完成后立即解决。

### 状态与二维码接口

- 初始化仍在进行时，`status()` 返回 `STARTING`，不等待初始化 Promise。
- 初始化成功后，`status()` 委托现有 Playwright 句柄观察页面，并继续返回 `WAITING`、`SCANNED`、`USER_ACTION_REQUIRED`、`VALIDATING` 或 `SUCCESS`。
- 初始化失败时，会话进入 `FAILED`，消息保留可操作的错误原因；该失败不会变成未处理的异步异常。
- 初始化未完成时调用 `qr()`，返回可重试的 `409 SESSION_NOT_READY`；初始化成功后的二维码截图行为不变。
- `consume()`、凭据验证和成功后的资源关闭规则保持不变。

### 生命周期与并发清理

- 删除或过期发生在初始化完成前时，会话先被标记为关闭；初始化稍后成功必须立即关闭新建的 Playwright Context，而不能重新挂回已删除会话。
- 初始化本身失败时，由 `PlaywrightDouyinDriver.createSession()` 继续负责关闭已创建的 Context。
- `cleanupExpired()` 和 `closeAll()` 同时覆盖已就绪和仍在初始化的会话。
- 重复关闭必须保持幂等，实际 Context 最多关闭一次。

## Spring Boot 设计

- `DouyinWebAuthService.start()` 保留“先建数据库记录、再请求 Worker、最后附加 Worker 会话”的补偿顺序。
- Worker 返回后，数据库中的会话状态保持 `STARTING`，不再被 `attachWorkerSession` 硬编码为 `WAITING`。
- `DouyinQrStartView.status` 使用 Worker 返回并规范化后的状态，正常异步创建返回 `STARTING`。
- Worker 调用本身失败时，现有 `WORKER_UNAVAILABLE` 失败记录和异常处理保持不变。
- 数据库附加失败时，现有 `safeDelete(workerSessionId)` 补偿路径继续生效；Worker 必须能安全删除仍在初始化的会话。
- 后续 `poll()` 继续把 Worker 状态写回数据库；第一次观察到二维码准备完成时自然转换为 `WAITING`。
- API 路径和 DTO 字段不变，仅把首次响应的真实状态从错误的 `WAITING` 修正为 `STARTING`。

## 抖音前端设计

- 保留共享 Axios 的 10 秒超时，不添加抖音专用长超时。
- `startDouyinWebQr()` 快速得到 `loginId` 后立即启动现有轮询。
- 把“当前状态是否允许请求二维码”提取为抖音模块内的纯函数；只有状态为 `WAITING` 时返回真。
- `startLogin()` 使用 Worker 返回的真实首次状态：`STARTING` 时不抢先请求二维码，只有首次响应或后续轮询状态为 `WAITING` 时才加载二维码图片。
- `STARTING` 期间继续显示“浏览器启动中/浏览器就绪”步骤，不显示错误提示。
- Worker 返回 `FAILED`、`USER_ACTION_REQUIRED` 或其他现有终态时，继续使用当前状态文案和操作按钮。
- 不修改共享 HTTP 客户端，也不修改任何 Bilibili 组件或 API 文件。

## 错误处理

- Chromium 无法启动、页面导航失败或 Playwright Context 创建失败：Worker 会话转为 `FAILED`，状态接口返回原因，Spring Boot 在下一次轮询时同步失败状态。
- 初始化期间二维码尚不可用：二维码接口返回 `409 SESSION_NOT_READY`，前端不把它视为终态，并等待状态进入 `WAITING`。
- 初始化期间会话过期或被删除：后台初始化结束后立即关闭产生的 Context，不再对外暴露该会话。
- Worker 进程退出：现有后端 Worker 调用错误处理继续返回 `WORKER_UNAVAILABLE`。
- 抖音页面进入验证码或人工验证：继续返回 `USER_ACTION_REQUIRED`，不归类为初始化失败。

## 测试策略

### Worker 单元测试

使用可控的 deferred driver Promise 覆盖：

- `create()` 在 driver 尚未完成时已经返回 `STARTING`。
- 初始化期间 `status()` 非阻塞返回 `STARTING`。
- 初始化期间 `qr()` 返回 `409 SESSION_NOT_READY`。
- driver 完成后，状态调用委托实际 handle 并进入 `WAITING`。
- driver 失败后状态为 `FAILED`，且没有未处理 rejection。
- 初始化期间删除或关闭会话，延迟产生的 handle 会被关闭且不会复活。

### Spring Boot 单元与仓储测试

- `start()` 接收 Worker 的 `STARTING` 并返回 `STARTING`。
- 附加 `workerSessionId` 后数据库状态仍为 `STARTING`。
- 后续轮询可以把状态更新为 `WAITING`。
- 数据库附加失败仍调用 Worker 删除补偿。

### 抖音前端测试

- 为抖音二维码请求策略增加纯函数 Vitest：`STARTING`、验证态和终态均不允许请求图片，只有 `WAITING` 允许。
- `startLogin()` 和轮询回调共同使用该策略，避免初始分支与轮询分支产生不同规则。
- 现有请求代次与轮询并发测试继续通过。

### 回归与集成验证

- 运行 Douyin Worker 全部 Node 测试。
- 运行 Spring Boot 全部 Maven 测试。
- 运行前端全部 Vitest、TypeScript 类型检查和生产构建。
- 本地真实启动后确认扫码启动接口在 10 秒内返回 `STARTING`，随后状态变为 `WAITING` 并显示二维码。
- 验证初始化失败能够通过轮询进入 `FAILED`，而不是显示 Axios 10 秒超时。
- 运行并抽查 Bilibili 页面与后端测试，确认 `/bilibili`、Bilibili API 和健康检查没有回归。

## Bilibili 隔离边界

本次实现不得修改以下范围：

- `frontend/src/views/bilibili/**`
- `frontend/src/views/bilibili-live/**`
- `frontend/src/api/bilibili*.ts`
- `backend/src/main/java/com/socialmonitor/bilibili/**`
- Bilibili 相关 Flyway migration、配置项和凭据存储

允许执行这些范围的测试和只读检查，但不能产生代码或配置变更。共享 `frontend/src/api/http.ts` 也保持不变，避免通过公共依赖间接改变 Bilibili 行为。

## 预计修改文件

- `douyin-worker/src/session-manager.js`
- `douyin-worker/test/session-manager.test.js`
- `backend/src/main/java/com/socialmonitor/douyin/auth/repository/DouyinAuthSessionRepository.java`
- `backend/src/main/java/com/socialmonitor/douyin/auth/service/DouyinWebAuthService.java`
- `backend/src/test/java/com/socialmonitor/douyin/auth/repository/DouyinAuthSessionRepositoryTests.java`
- `backend/src/test/java/com/socialmonitor/douyin/auth/service/DouyinWebAuthServiceTests.java`
- `frontend/src/views/douyin/components/DouyinQrLoginDialog.vue`
- `frontend/src/views/douyin/qrImagePolicy.ts`
- `frontend/src/views/douyin/qrImagePolicy.test.ts`

如实现中发现必须越过上述边界，先停止并重新请求确认，不以“顺手重构”为由扩大范围。
