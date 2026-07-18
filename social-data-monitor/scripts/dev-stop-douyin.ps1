$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
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

& (Join-Path $PSScriptRoot "load-env.ps1") -Path (Join-Path $root ".env.local")
$workerPort = if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_PORT)) { 8787 } else { [int]$env:DOUYIN_WORKER_PORT }
$workerProcess = Get-ListenerProcess -Port $workerPort
if (-not $workerProcess) {
  Write-Host "Douyin Worker is not listening on $workerPort."
} else {
  $commandLine = if ($null -eq $workerProcess.CommandLine) { "" } else { $workerProcess.CommandLine }
  $belongsToProject = $commandLine -like "*$root*" -and $commandLine -like "*douyin-worker*server.js*"
  if (-not $belongsToProject) {
    Write-Warning "Worker port $workerPort is used by PID $($workerProcess.ProcessId), outside this project. Skipped."
  } else {
    Stop-Process -Id $workerProcess.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Host "Stopped Douyin Worker on $workerPort (PID $($workerProcess.ProcessId))."
  }
}

& $windowsPowerShell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "dev-stop.ps1")
if ($LASTEXITCODE -ne 0) {
  throw "The original development stop flow failed."
}
