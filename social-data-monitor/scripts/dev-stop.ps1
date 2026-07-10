$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Get-ListenerProcess {
  param([int]$Port)

  $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if (-not $connection) {
    return $null
  }

  Get-CimInstance Win32_Process -Filter "ProcessId = $($connection.OwningProcess)" -ErrorAction SilentlyContinue
}

function Stop-ProjectListener {
  param(
    [string]$Name,
    [int]$Port
  )

  $process = Get-ListenerProcess -Port $Port
  if (-not $process) {
    Write-Host "$Name is not listening on $Port."
    return
  }

  $commandLine = ""
  if ($null -ne $process.CommandLine) {
    $commandLine = $process.CommandLine
  }

  $belongsToProject = $commandLine -like "*$root*"
  if ($Name -eq "Backend" -and $commandLine -like "*com.socialmonitor.SocialDataMonitorApplication*") {
    $belongsToProject = $true
  }

  if (-not $belongsToProject) {
    Write-Warning "$Name port $Port is used by PID $($process.ProcessId), but it does not look like this project. Skipped."
    return
  }

  Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
  Write-Host "Stopped $Name on $Port (PID $($process.ProcessId))."
}

Stop-ProjectListener -Name "Frontend" -Port 5173
Stop-ProjectListener -Name "Backend" -Port 8080

$pgCtl = Join-Path $root ".dev-tools\postgresql\pgsql\bin\pg_ctl.exe"
$pgData = Join-Path $root ".dev-data\postgres"
$pgProcess = Get-ListenerProcess -Port 5432
if (-not $pgProcess) {
  Write-Host "PostgreSQL is not listening on 5432."
} elseif ((Test-Path $pgCtl) -and (Test-Path $pgData)) {
  & $pgCtl -D $pgData stop | Out-Null
  Write-Host "Stopped PostgreSQL."
} else {
  Write-Warning "Portable PostgreSQL tools or data directory were not found. Skipped PostgreSQL stop."
}
