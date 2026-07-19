# 抖音登录态统一系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让抖音登录态成为 Social Data Monitor 的固定模块，并由原有统一启停命令管理前端、后端、Worker 和 PostgreSQL。

**Architecture:** Vue 固定注册 `/douyin` 菜单和路由；现有 Spring Boot 单体在本地统一加载 `dev,douyin`；独立 Node.js + Playwright Worker 保持技术边界，但由 `dev-start.cmd` 和 `dev-stop.cmd` 管理。可测试的 PowerShell 配置模块负责生成并持久化抖音凭据 key、设置四个进程共享的环境变量。

**Tech Stack:** Vue 3、Vue Router、Vitest、TypeScript、Windows PowerShell 5.1、Spring Boot、Node.js、Playwright、PostgreSQL。

## Global Constraints

- 只实现抖音登录态获取、保存、校验、查看、导出和撤销，不新增抖音业务数据采集。
- 不修改 Bilibili 登录实现、Bilibili API 或 Flyway V1–V8。
- 前端不直接调用 Worker；Worker 不访问数据库。
- 本地私有配置只写入项目内且被 Git 忽略的 `.env.local`。
- 已有合法抖音 key 必须保持不变；非占位但非法的 key 必须报错，不能覆盖。
- Windows 标准入口保持 `scripts/dev-start.cmd` 和 `scripts/dev-stop.cmd`。

---

### Task 1: 固定注册抖音菜单和路由

**Files:**
- Create: `frontend/src/router/douyinIntegration.test.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/layouts/MainLayout.vue`
- Modify: `frontend/src/env.d.ts`

**Interfaces:**
- Consumes: 现有 `DouyinView.vue` 和 `MainLayout.vue`。
- Produces: 无环境开关依赖的 `/douyin` 路由及左侧“抖音登录态”菜单。

- [ ] **Step 1: 写入失败的前端集成契约测试**

```ts
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const readSource = (relativePath: string) =>
  readFileSync(fileURLToPath(new URL(relativePath, import.meta.url)), 'utf8')

describe('Douyin system integration', () => {
  it('always registers the Douyin route without a feature flag', () => {
    const source = readSource('./index.ts')
    expect(source).toContain("path: 'douyin'")
    expect(source).not.toContain('VITE_DOUYIN_ENABLED')
    expect(source).not.toContain('douyinEnabled ?')
  })

  it('always renders the Douyin menu item', () => {
    const source = readSource('../layouts/MainLayout.vue')
    expect(source).toContain('index="/douyin"')
    expect(source).toContain('<span>抖音登录态</span>')
    expect(source).not.toContain('v-if="douyinEnabled"')
    expect(source).not.toContain('const douyinEnabled')
  })
})
```

- [ ] **Step 2: 运行测试并确认因现有功能开关而失败**

Run: `cd frontend; npm test -- src/router/douyinIntegration.test.ts`

Expected: FAIL，失败断言包含 `VITE_DOUYIN_ENABLED` 或 `v-if="douyinEnabled"`。

- [ ] **Step 3: 最小化修改路由、菜单和类型声明**

将条件路由替换为固定路由：

```ts
{
  path: 'douyin',
  name: 'douyin',
  component: () => import('@/views/douyin/DouyinView.vue'),
  meta: { title: '抖音登录态' }
},
```

将菜单项替换为：

```vue
<el-menu-item index="/douyin"><el-icon><Iphone /></el-icon><span>抖音登录态</span></el-menu-item>
```

删除 `router/index.ts` 和 `MainLayout.vue` 中的 `douyinEnabled` 常量，并把 `env.d.ts` 恢复为仅包含 Vue 模块声明。

- [ ] **Step 4: 运行定向测试、全部前端测试和类型检查**

Run: `cd frontend; npm test -- src/router/douyinIntegration.test.ts; npm test; npm run typecheck`

Expected: 定向测试通过，全部 Vitest 通过，`vue-tsc --noEmit` 退出码为 0。

- [ ] **Step 5: 提交前端固定入口**

```powershell
git add frontend/src/router/douyinIntegration.test.ts frontend/src/router/index.ts frontend/src/layouts/MainLayout.vue frontend/src/env.d.ts
git commit -m 'feat: expose Douyin auth in main navigation'
```

---

### Task 2: 用测试驱动统一开发环境配置

**Files:**
- Create: `scripts/tests/dev-system-config.test.ps1`
- Create: `scripts/dev-system-config.ps1`

**Interfaces:**
- Consumes: 项目根路径和当前进程中由 `.env.local` 加载的环境变量。
- Produces: `Test-Base64Key32(Value) -> bool`、`Set-EnvFileValue(Path, Name, Value)`、`Initialize-IntegratedDevEnvironment(Root) -> object`。

- [ ] **Step 1: 写入失败的 PowerShell 配置测试**

测试脚本必须先可控地报告 `Initialize-IntegratedDevEnvironment is not defined`，再覆盖以下真实文件行为：

```powershell
$modulePath = Join-Path (Split-Path -Parent $PSScriptRoot) 'dev-system-config.ps1'
if (-not (Test-Path -LiteralPath $modulePath)) {
  Write-Error 'Initialize-IntegratedDevEnvironment is not defined.'
  exit 1
}
. $modulePath

$root = Join-Path ([System.IO.Path]::GetTempPath()) ('social-monitor-config-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $root | Out-Null
try {
  Remove-Item Env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY -ErrorAction SilentlyContinue
  $first = Initialize-IntegratedDevEnvironment -Root $root
  $key = $env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY
  if (-not $first.KeyGenerated) { throw 'Missing key was not generated.' }
  if (-not (Test-Base64Key32 -Value $key)) { throw 'Generated key is not 32 bytes.' }

  $second = Initialize-IntegratedDevEnvironment -Root $root
  if ($second.KeyGenerated) { throw 'Existing key was generated again.' }
  if ($env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY -ne $key) { throw 'Existing key changed.' }
  if ($env:SPRING_PROFILES_ACTIVE -ne 'dev,douyin') { throw 'Profiles were not integrated.' }
  if ($env:SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED -ne 'true') { throw 'Douyin backend was not enabled.' }
  if ($env:DOUYIN_WORKER_PORT -ne '8787') { throw 'Worker port default is wrong.' }

  $env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY = 'invalid-user-value'
  try {
    Initialize-IntegratedDevEnvironment -Root $root | Out-Null
    throw 'Invalid key was accepted.'
  } catch {
    if ($_.Exception.Message -eq 'Invalid key was accepted.') { throw }
  }
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Output 'All dev system configuration tests passed.'
```

- [ ] **Step 2: 运行测试并确认缺少配置模块**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-system-config.test.ps1`

Expected: exit 1，输出 `Initialize-IntegratedDevEnvironment is not defined.`。

- [ ] **Step 3: 实现可测试的配置模块**

实现以下行为：

```powershell
function Set-EnvFileValue {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$Value
  )
  $directory = Split-Path -Parent $Path
  if (-not (Test-Path -LiteralPath $directory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
  }
  $lines = if (Test-Path -LiteralPath $Path) {
    [System.IO.File]::ReadAllLines($Path)
  } else {
    [string[]]@()
  }
  $pattern = '^\s*' + [regex]::Escape($Name) + '\s*='
  $found = $false
  $updated = foreach ($line in $lines) {
    if ($line -match $pattern) {
      $found = $true
      $Name + '=' + $Value
    } else {
      $line
    }
  }
  if (-not $found) { $updated = @($updated) + ($Name + '=' + $Value) }
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllLines($Path, [string[]]$updated, $utf8NoBom)
}

function Test-Base64Key32 {
  param([AllowNull()][string]$Value)
  if ([string]::IsNullOrWhiteSpace($Value)) { return $false }
  try { return ([Convert]::FromBase64String($Value).Length -eq 32) } catch { return $false }
}

function Initialize-IntegratedDevEnvironment {
  param([Parameter(Mandatory = $true)][string]$Root)
  $envPath = Join-Path $Root '.env.local'
  $key = $env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY
  $generated = $false
  if ([string]::IsNullOrWhiteSpace($key) -or $key -eq 'REPLACE_WITH_BASE64_32_BYTE_KEY') {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $key = [Convert]::ToBase64String($bytes)
    Set-EnvFileValue -Path $envPath -Name 'SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY' -Value $key
    $generated = $true
  } elseif (-not (Test-Base64Key32 -Value $key)) {
    throw 'SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY must be a base64-encoded 32-byte key.'
  }
  $env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY = $key
  $env:SPRING_PROFILES_ACTIVE = 'dev,douyin'
  $env:SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED = 'true'
  $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $Root '.dev-tools\playwright'
  if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_HOST)) { $env:DOUYIN_WORKER_HOST = '127.0.0.1' }
  if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_PORT)) { $env:DOUYIN_WORKER_PORT = '8787' }
  if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_HEADLESS)) { $env:DOUYIN_WORKER_HEADLESS = 'false' }
  if ([string]::IsNullOrWhiteSpace($env:SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL)) {
    $env:SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL = 'http://127.0.0.1:8787'
  }
  $env:SOCIAL_MONITOR_ENV_PRESERVE_EXISTING = 'true'
  [PSCustomObject]@{ KeyGenerated = $generated; EnvFilePath = $envPath; WorkerPort = [int]$env:DOUYIN_WORKER_PORT }
}
```

- [ ] **Step 4: 运行配置测试并确认通过**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-system-config.test.ps1`

Expected: exit 0，输出 `All dev system configuration tests passed.`。

- [ ] **Step 5: 提交配置模块**

```powershell
git add scripts/dev-system-config.ps1 scripts/tests/dev-system-config.test.ps1
git commit -m 'feat: initialize integrated Douyin dev environment'
```

---

### Task 3: 合并 Worker 编排、兼容入口和文档

**Files:**
- Create: `scripts/tests/dev-orchestration.test.ps1`
- Modify: `scripts/dev-start.ps1`
- Modify: `scripts/dev-stop.ps1`
- Modify: `scripts/dev-start-douyin.ps1`
- Modify: `scripts/dev-stop-douyin.ps1`
- Modify: `.env.example`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 2 的 `Initialize-IntegratedDevEnvironment` 和现有 `dev-douyin-worker.ps1`。
- Produces: 统一启动四个组件、统一停止四个组件、旧抖音命令透明转发。

- [ ] **Step 1: 写入失败的编排契约测试**

```powershell
$scriptsRoot = Split-Path -Parent $PSScriptRoot
$start = Get-Content -LiteralPath (Join-Path $scriptsRoot 'dev-start.ps1') -Raw
$stop = Get-Content -LiteralPath (Join-Path $scriptsRoot 'dev-stop.ps1') -Raw
$legacyStart = Get-Content -LiteralPath (Join-Path $scriptsRoot 'dev-start-douyin.ps1') -Raw
$legacyStop = Get-Content -LiteralPath (Join-Path $scriptsRoot 'dev-stop-douyin.ps1') -Raw

if ($start -notmatch 'Initialize-IntegratedDevEnvironment') { throw 'Unified start does not initialize Douyin.' }
if ($start -notmatch 'Start-DouyinWorker') { throw 'Unified start does not launch Worker.' }
if ($start -match 'VITE_DOUYIN_ENABLED') { throw 'Obsolete frontend flag remains.' }
if ($stop -notmatch 'Douyin Worker') { throw 'Unified stop does not stop Worker.' }
if ($legacyStart -notmatch 'dev-start\.ps1' -or $legacyStart -match 'Start-Process') { throw 'Legacy start is not a thin wrapper.' }
if ($legacyStop -notmatch 'dev-stop\.ps1' -or $legacyStop -match 'Stop-Process') { throw 'Legacy stop is not a thin wrapper.' }
Write-Output 'All dev orchestration contract tests passed.'
```

- [ ] **Step 2: 运行测试并确认统一启动器缺少 Worker**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-orchestration.test.ps1`

Expected: exit 1，输出 `Unified start does not initialize Douyin.`。

- [ ] **Step 3: 合并启动和停止逻辑**

给 `Get-Listener` 返回值增加 `CommandLine` 字段，并在 `dev-start.ps1` 中加入完整 Worker 启动函数：

```powershell
function Start-DouyinWorker {
  param([Parameter(Mandatory = $true)][int]$Port)

  $listener = Get-Listener -Port $Port
  if ($listener) {
    $commandLine = if ($null -eq $listener.CommandLine) { '' } else { $listener.CommandLine }
    if ($commandLine -notlike "*$root*" -or $commandLine -notlike '*douyin-worker*server.js*') {
      throw "Port $Port is already used by PID $($listener.ProcessId), outside this project."
    }
    Write-Host "Douyin Worker already listening on $Port (PID $($listener.ProcessId))."
    return
  }

  $stdoutPath = Join-Path $logDir 'douyin-worker-dev.log'
  $stderrPath = Join-Path $logDir 'douyin-worker-dev.err.log'
  Remove-Item -LiteralPath $stdoutPath -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
  $scriptPath = Join-Path $PSScriptRoot 'dev-douyin-worker.ps1'
  $launcher = Start-Process `
    -FilePath (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') `
    -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"{0}"' -f $scriptPath)) `
    -WorkingDirectory $root `
    -WindowStyle Hidden `
    -RedirectStandardOutput $stdoutPath `
    -RedirectStandardError $stderrPath `
    -PassThru
  Write-Host "Starting Douyin Worker with launcher PID $($launcher.Id). Logs: $stdoutPath"
}
```

`dev-start.ps1` 在启动 PostgreSQL 前执行：

```powershell
& (Join-Path $PSScriptRoot 'load-env.ps1') -Path (Join-Path $root '.env.local')
. (Join-Path $PSScriptRoot 'dev-system-config.ps1')
$devConfig = Initialize-IntegratedDevEnvironment -Root $root
Start-PortablePostgres
Start-DouyinWorker -Port $devConfig.WorkerPort
Start-DevProcess -Name 'Backend' -Port 8080 -ScriptName 'dev-backend.ps1' -StdoutName 'backend-dev.log' -StderrName 'backend-dev.err.log'
Start-DevProcess -Name 'Frontend' -Port 5173 -ScriptName 'dev-frontend.ps1' -StdoutName 'frontend-dev.log' -StderrName 'frontend-dev.err.log'
```

非 `-NoWait` 模式还必须带可选 Bearer token 等待：

```powershell
Wait-ForHttp -Url "http://127.0.0.1:$($devConfig.WorkerPort)/internal/v1/health" -TimeoutSeconds $TimeoutSeconds -BearerToken $env:SOCIAL_MONITOR_DOUYIN_WORKER_TOKEN | Out-Null
```

`dev-stop.ps1` 加载 `.env.local` 后调用：

```powershell
$workerPort = if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_PORT)) { 8787 } else { [int]$env:DOUYIN_WORKER_PORT }
Stop-ProjectListener -Name 'Douyin Worker' -Port $workerPort
```

兼容脚本只调用统一脚本：

```powershell
& (Join-Path $PSScriptRoot 'dev-start.ps1') @PSBoundParameters
& (Join-Path $PSScriptRoot 'dev-stop.ps1')
```

- [ ] **Step 4: 更新配置模板和 README**

`.env.example` 使用 `SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED=true`，删除 `VITE_DOUYIN_ENABLED`；README 说明标准命令会启动 5432、8080、5173、8787，首次缺少 key 时自动生成，旧抖音命令仅是兼容别名。

- [ ] **Step 5: 运行脚本契约、配置测试和静态检查**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-orchestration.test.ps1; powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-system-config.test.ps1; git diff --check`

Expected: 两个 PowerShell 测试均退出 0，`git diff --check` 无错误。

- [ ] **Step 6: 提交统一编排和文档**

```powershell
git add scripts/dev-start.ps1 scripts/dev-stop.ps1 scripts/dev-start-douyin.ps1 scripts/dev-stop-douyin.ps1 scripts/tests/dev-orchestration.test.ps1 .env.example README.md docs/superpowers/plans/2026-07-19-douyin-integrated-system.md
git commit -m 'feat: run Douyin auth as part of the main system'
```

---

### Task 4: 全量验证并重启统一系统

**Files:**
- Verify only: `backend/**`
- Verify only: `frontend/**`
- Verify only: `douyin-worker/**`
- Runtime private update: `.env.local`

**Interfaces:**
- Consumes: Tasks 1–3 的统一系统。
- Produces: 已重启且通过 HTTP、页面和扫码会话检查的本地服务。

- [ ] **Step 1: 运行全部自动化验证**

```powershell
cd backend
.\mvnw.cmd test
cd ..\frontend
npm test
npm run typecheck
npm run build
cd ..\douyin-worker
npm test
cd ..
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-system-config.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/dev-orchestration.test.ps1
git diff --check
```

Expected: Maven 0 failures/errors，Vitest 全部通过，类型检查和 Vite build 退出 0，Worker Node tests 全部通过，两个 PowerShell 测试通过，Git whitespace 检查通过。

- [ ] **Step 2: 使用统一脚本停止旧服务**

Run: `.\scripts\dev-stop.cmd`

Expected: 仅停止属于当前项目的 5173、8080、8787 和项目内 5432 监听器。

- [ ] **Step 3: 使用统一脚本启动全部组件**

Run: `.\scripts\dev-start.cmd -NoWait`

Expected: PostgreSQL、Worker、后端和前端启动器均被拉起；`.env.local` 缺少抖音 key 时只追加一次。

- [ ] **Step 4: 验证服务和页面**

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8787/internal/v1/health
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5173/bilibili
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5173/douyin
Invoke-RestMethod http://127.0.0.1:8080/api/douyin/auth/status
```

Expected: 后端 `status=UP`，Worker 和两个页面返回 2xx，抖音状态接口返回 JSON。

- [ ] **Step 5: 页面级和扫码会话回归**

在浏览器中确认左侧同时显示 Bilibili 和“抖音登录态”；调用 Bilibili `/api/bilibili/auth/qr/start` 与抖音 `/api/douyin/auth/web/qr/start`，确认二者都返回会话标识和二维码数据。

- [ ] **Step 6: 最终仓库与运行状态检查**

Run: `git status --short --branch; Get-NetTCPConnection -State Listen -LocalPort 5173,8080,8787,5432`

Expected: 工作树没有未提交源码，四个端口均由当前项目组件监听。
