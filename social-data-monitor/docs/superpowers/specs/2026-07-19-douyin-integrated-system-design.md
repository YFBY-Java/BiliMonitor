# 抖音登录态统一集成设计

## 背景

抖音扫码登录、登录态持久化、官方 OAuth 和 Playwright Worker 已经进入 `social-data-monitor`，但当前前端通过 `VITE_DOUYIN_ENABLED` 隐藏 `/douyin` 路由和左侧菜单，Windows 本地开发还要求单独执行 `dev-start-douyin.cmd`。这让同一仓库中的功能表现成了另一套系统。

本次调整把抖音登录态作为现有 Social Data Monitor 的常规模块。用户继续使用原有页面壳、原有后端应用和原有 `dev-start.cmd` / `dev-stop.cmd`；Worker 只是统一启动器管理的内部进程。

## 目标

- 左侧菜单始终显示“抖音登录态”，`/douyin` 始终注册并可直接访问。
- `scripts/dev-start.cmd` 一次启动 PostgreSQL、Douyin Worker、Spring Boot 后端和 Vite 前端。
- Spring Boot 本地开发统一加载 `dev,douyin` profile，并启用现有抖音认证组件。
- `scripts/dev-stop.cmd` 一次停止上述四个本项目组件。
- 第一次本地启动时，如果 `.env.local` 没有抖音凭据加密 key，则生成一个稳定的 base64 32 字节 key 并写回项目内的 `.env.local`。
- 保留 `dev-start-douyin.cmd` 和 `dev-stop-douyin.cmd` 作为兼容入口，但它们只转发到统一启停流程。
- 保持 Bilibili 页面、Bilibili 扫码登录、接口、数据表和既有启动 URL 不变。

## 非目标

- 不新增抖音作品、评论、直播或其他业务数据采集。
- 不把 Playwright 浏览器逻辑迁入 Java 进程。
- 不修改 Bilibili 登录实现或既有 Flyway 迁移。
- 不合并抖音与 Bilibili 的登录态存储、加密器或 API。
- 不改变 Docker Compose 的内部多容器结构；它仍然作为同一系统的部署实现。

## 用户体验

用户执行：

```powershell
.\scripts\dev-start.cmd
```

启动完成后同时得到：

- Bilibili：`http://127.0.0.1:5173/bilibili`
- 抖音登录态：`http://127.0.0.1:5173/douyin`
- 后端健康检查：`http://127.0.0.1:8080/actuator/health`
- Douyin Worker 健康检查：`http://127.0.0.1:8787/internal/v1/health`

用户执行：

```powershell
.\scripts\dev-stop.cmd
```

统一停止前端、后端、Douyin Worker 和项目内 PostgreSQL。

## 架构和组件边界

### 前端

- 移除 `VITE_DOUYIN_ENABLED` 对路由和菜单的条件控制。
- `/douyin` 与 `/bilibili` 一样，是 `MainLayout` 下的固定一级模块。
- 抖音页面继续只调用 Spring Boot 的 `/api/douyin/auth/**`，不直接调用 Worker。

### 后端

- 仍然是现有的单个 Spring Boot 应用，不创建第二个后端。
- 统一启动器为本地后端设置 `SPRING_PROFILES_ACTIVE=dev,douyin` 和 `SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED=true`。
- Bilibili Controller、Service、Repository 和凭据处理代码保持不动。

### Douyin Worker

- Worker 保留独立 Node.js + Playwright 进程，因为它负责浏览器生命周期、二维码页面和浏览器存储态提取。
- Worker 由统一 `dev-start.cmd` 启动、由统一 `dev-stop.cmd` 停止；用户不需要理解或单独管理它。
- 后端继续通过 `SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL` 调用 Worker。

## 启动流程

统一 `dev-start.ps1` 按以下顺序执行：

1. 加载项目内 `.env.local`。
2. 检查 `SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY`。
3. key 缺失、为空或仍为模板占位值时，生成 32 个随机字节，转为 base64，并写入 `.env.local`；合法的已有 key 原样保留；用户提供了非占位但格式非法的值时明确报错，不静默覆盖。
4. 在当前启动器进程中设置统一的后端、前端和 Worker 环境变量，并让子进程继承这些值。
5. 启动 PostgreSQL。
6. 并行启动 Douyin Worker、Spring Boot 后端和 Vite 前端。
7. 非 `-NoWait` 模式下等待三个 HTTP 服务就绪，然后输出 Bilibili 和抖音两个页面地址。

如果 8787 被本项目 Worker 占用则复用；如果被其他程序占用则停止启动并显示占用 PID。8080 和 5173 延续现有的本项目监听器复用规则。代码更新后已运行的旧后端需要通过统一停止、启动流程重新加载 `douyin` profile。

## 停止流程

统一 `dev-stop.ps1` 从 `.env.local` 读取 Worker 端口，并验证监听进程命令行属于当前项目后再停止。其他程序占用同一端口时只警告，不终止该程序。随后按现有规则停止前端、后端和项目内 PostgreSQL。

兼容脚本不再复制 Worker 编排逻辑：

- `dev-start-douyin.ps1` 转发全部参数到 `dev-start.ps1`。
- `dev-stop-douyin.ps1` 转发到 `dev-stop.ps1`。

## 配置

- `.env.example` 中抖音认证默认启用，说明其由统一启动器管理。
- 删除已无用途的 `VITE_DOUYIN_ENABLED` 示例项和前端类型声明。
- `.env.local` 仍然是唯一的本地私有配置文件，并保持 Git 忽略。
- 自动生成只处理抖音凭据加密 key，不改写数据库、Bilibili 或 OAuth 配置。
- 已保存登录态依赖 key 的稳定性，因此生成后必须持久化；脚本不能在每次启动时生成临时 key。

## 错误处理

- 无法创建或更新 `.env.local`：启动失败并指出文件路径。
- 已配置的抖音 key 不是 base64 编码的 32 字节：启动失败并要求修正，不覆盖原值。
- Worker 依赖或 Chromium 安装失败：启动失败，错误保留在 `.dev-data/douyin-worker-dev.err.log`。
- Worker 未在超时内健康：统一启动命令返回失败并给出 Worker 日志路径。
- 其他进程占用 Worker 端口：不终止外部进程，启动失败并报告 PID。
- 抖音接口失败不会改变 Bilibili 路由、接口或登录状态。

## 测试和验收

### 自动测试

- 前端回归测试证明固定路由集合包含 `/douyin`，且不依赖 `VITE_DOUYIN_ENABLED`。
- PowerShell 脚本测试覆盖 key 缺失时只生成一次、合法 key 保留、非法 key 拒绝，以及统一环境变量设置。
- 运行全部前端 Vitest、TypeScript 类型检查、后端 Maven 测试和 Worker Node 测试。

### 本地集成验收

1. 使用统一停止命令清理本项目旧进程。
2. 使用统一启动命令启动系统。
3. 验证 `/bilibili` 和 `/douyin` 均返回页面。
4. 验证后端和 Worker 健康检查成功。
5. 验证左侧菜单同时出现 Bilibili 和“抖音登录态”。
6. 验证 Bilibili 扫码登录按钮仍能创建二维码会话。
7. 验证抖音扫码按钮能创建 Worker 会话并显示二维码。
8. 使用统一停止命令后确认 5173、8080、8787 和项目内 5432 不再监听。

## 预计修改文件

- `frontend/src/router/index.ts`：固定注册抖音路由。
- `frontend/src/layouts/MainLayout.vue`：固定显示抖音菜单。
- `frontend/src/env.d.ts`：移除废弃的前端功能开关类型。
- `frontend/src/router/*.test.ts`：新增固定路由回归测试。
- `scripts/dev-start.ps1`：合并 Worker、环境初始化和就绪检查。
- `scripts/dev-stop.ps1`：合并 Worker 停止逻辑。
- `scripts/dev-start-douyin.ps1`、`scripts/dev-stop-douyin.ps1`：改为兼容转发器。
- `scripts/dev-system-config.ps1`：封装可测试的抖音 key 和统一环境初始化逻辑。
- `scripts/tests/dev-system-config.test.ps1`：验证配置初始化行为。
- `.env.example`：更新统一系统默认值和说明。
- `README.md`：统一本地启停说明，保留兼容命令说明。
