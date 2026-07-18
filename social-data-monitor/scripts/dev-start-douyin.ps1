param(
  [switch]$NoWait,
  [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root ".dev-data"
$workerLog = Join-Path $logDir "douyin-worker-dev.log"
$workerErrorLog = Join-Path $logDir "douyin-worker-dev.err.log"
$windowsPowerShell = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"

function Get-ListenerProcess {
  param([int]$Port)

  $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if (-not $connection) {
    return $null
  }
  Get-CimInstance Win32_Process -Filter "ProcessId = $($connection.OwningProcess)" -ErrorAction SilentlyContinue
}

function Wait-ForHttp {
  param(
    [string]$Url,
    [int]$TimeoutSeconds
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  $lastError = $null
  do {
    try {
      $headers = @{}
      if (-not [string]::IsNullOrWhiteSpace($env:SOCIAL_MONITOR_DOUYIN_WORKER_TOKEN)) {
        $headers.Authorization = "Bearer $($env:SOCIAL_MONITOR_DOUYIN_WORKER_TOKEN)"
      }
      $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -Headers $headers -TimeoutSec 3
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
        return
      }
      $lastError = "HTTP $($response.StatusCode)"
    } catch {
      $lastError = $_.Exception.Message
    }
    Start-Sleep -Seconds 1
  } while ((Get-Date) -lt $deadline)

  throw "$Url did not become ready within $TimeoutSeconds seconds. Last error: $lastError"
}

& (Join-Path $PSScriptRoot "load-env.ps1") -Path (Join-Path $root ".env.local")

if ([string]::IsNullOrWhiteSpace($env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY) -or
    $env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY -eq "REPLACE_WITH_BASE64_32_BYTE_KEY") {
  throw "Set SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY in .env.local to a base64-encoded 32-byte key before starting Douyin auth."
}

# These overrides are scoped to this launcher and inherited by its child processes.
$env:SPRING_PROFILES_ACTIVE = "dev,douyin"
$env:SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED = "true"
$env:VITE_DOUYIN_ENABLED = "true"
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $root ".dev-tools\playwright"
if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_HOST)) { $env:DOUYIN_WORKER_HOST = "127.0.0.1" }
if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_PORT)) { $env:DOUYIN_WORKER_PORT = "8787" }
if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_HEADLESS)) { $env:DOUYIN_WORKER_HEADLESS = "false" }
if ([string]::IsNullOrWhiteSpace($env:SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL)) {
  $env:SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL = "http://127.0.0.1:8787"
}
$env:SOCIAL_MONITOR_ENV_PRESERVE_EXISTING = "true"

New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$workerPort = [int]$env:DOUYIN_WORKER_PORT
$workerProcess = Get-ListenerProcess -Port $workerPort
if ($workerProcess) {
  $commandLine = if ($null -eq $workerProcess.CommandLine) { "" } else { $workerProcess.CommandLine }
  if ($commandLine -notlike "*$root*" -or $commandLine -notlike "*douyin-worker*server.js*") {
    throw "Port $workerPort is already used by PID $($workerProcess.ProcessId), outside this project."
  }
  Write-Host "Douyin Worker already listening on $workerPort (PID $($workerProcess.ProcessId))."
} else {
  Remove-Item -LiteralPath $workerLog -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $workerErrorLog -Force -ErrorAction SilentlyContinue
  $launcher = Start-Process `
    -FilePath $windowsPowerShell `
    -ArgumentList @(
      "-NoProfile",
      "-ExecutionPolicy", "Bypass",
      "-File", ('"{0}"' -f (Join-Path $PSScriptRoot "dev-douyin-worker.ps1"))
    ) `
    -WorkingDirectory $root `
    -WindowStyle Hidden `
    -RedirectStandardOutput $workerLog `
    -RedirectStandardError $workerErrorLog `
    -PassThru
  Write-Host "Starting Douyin Worker with launcher PID $($launcher.Id). Logs: $workerLog"
}

$startArguments = @(
  "-NoProfile",
  "-ExecutionPolicy", "Bypass",
  "-File", (Join-Path $PSScriptRoot "dev-start.ps1"),
  "-TimeoutSeconds", $TimeoutSeconds
)
if ($NoWait) {
  $startArguments += "-NoWait"
}
& $windowsPowerShell @startArguments
if ($LASTEXITCODE -ne 0) {
  throw "The original development startup flow failed."
}

if ($NoWait) {
  Write-Host "Douyin launchers started. Worker logs: $workerLog"
  Write-Host "Douyin page: http://127.0.0.1:5173/douyin"
  exit 0
}

Wait-ForHttp -Url "http://127.0.0.1:$workerPort/internal/v1/health" -TimeoutSeconds $TimeoutSeconds
Write-Host ""
Write-Host "Douyin auth environment is ready."
Write-Host "Douyin page: http://127.0.0.1:5173/douyin"
Write-Host "Worker health: http://127.0.0.1:$workerPort/internal/v1/health"
Write-Host "Worker logs: $workerLog"
