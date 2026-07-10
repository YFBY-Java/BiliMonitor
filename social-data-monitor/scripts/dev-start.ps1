param(
  [switch]$NoWait,
  [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root ".dev-data"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Get-Listener {
  param([int]$Port)

  $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if (-not $connection) {
    return $null
  }

  $process = Get-Process -Id $connection.OwningProcess -ErrorAction SilentlyContinue
  [PSCustomObject]@{
    Port = $Port
    ProcessId = $connection.OwningProcess
    ProcessName = $process.ProcessName
    Path = $process.Path
  }
}

function Wait-ForPort {
  param(
    [int]$Port,
    [int]$TimeoutSeconds
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    $listener = Get-Listener -Port $Port
    if ($listener) {
      return $listener
    }
    Start-Sleep -Milliseconds 500
  } while ((Get-Date) -lt $deadline)

  throw "Port $Port did not start listening within $TimeoutSeconds seconds."
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
      $request = [System.Net.HttpWebRequest]::Create($Url)
      $request.Method = "GET"
      $request.Timeout = 3000
      $request.ReadWriteTimeout = 3000
      $response = $request.GetResponse()
      try {
        $statusCode = [int]$response.StatusCode
      } finally {
        $response.Close()
      }
      if ($statusCode -ge 200 -and $statusCode -lt 500) {
        return $statusCode
      }
      $lastError = "HTTP $statusCode"
    } catch {
      $lastError = $_.Exception.Message
    }
    Start-Sleep -Seconds 1
  } while ((Get-Date) -lt $deadline)

  throw "$Url did not become ready within $TimeoutSeconds seconds. Last error: $lastError"
}

function Start-PortablePostgres {
  $listener = Get-Listener -Port 5432
  if ($listener) {
    Write-Host "PostgreSQL already listening on 5432 (PID $($listener.ProcessId))."
    return
  }

  $pgCtl = Join-Path $root ".dev-tools\postgresql\pgsql\bin\pg_ctl.exe"
  $pgData = Join-Path $root ".dev-data\postgres"
  $pgLog = Join-Path $root ".dev-data\postgres.log"

  if (-not (Test-Path $pgCtl)) {
    throw "pg_ctl.exe not found: $pgCtl"
  }
  if (-not (Test-Path $pgData)) {
    throw "PostgreSQL data directory not found: $pgData"
  }

  Write-Host "Starting PostgreSQL..."
  Start-Process `
    -FilePath $pgCtl `
    -ArgumentList @("-D", $pgData, "-l", $pgLog, "start") `
    -WorkingDirectory $root `
    -WindowStyle Hidden | Out-Null
  Wait-ForPort -Port 5432 -TimeoutSeconds 30 | Out-Null
}

function Start-DevProcess {
  param(
    [string]$Name,
    [int]$Port,
    [string]$ScriptName,
    [string]$StdoutName,
    [string]$StderrName
  )

  $listener = Get-Listener -Port $Port
  if ($listener) {
    Write-Host "$Name already listening on $Port (PID $($listener.ProcessId))."
    return
  }

  $scriptPath = Join-Path $PSScriptRoot $ScriptName
  $stdoutPath = Join-Path $logDir $StdoutName
  $stderrPath = Join-Path $logDir $StderrName

  Remove-Item -LiteralPath $stdoutPath -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue

  $process = Start-Process `
    -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $scriptPath) `
    -WorkingDirectory $root `
    -WindowStyle Hidden `
    -RedirectStandardOutput $stdoutPath `
    -RedirectStandardError $stderrPath `
    -PassThru

  Write-Host "Starting $Name with launcher PID $($process.Id). Logs: $stdoutPath"
}

Start-PortablePostgres

# Backend and frontend do not need to wait on each other; starting them together is noticeably faster.
Start-DevProcess -Name "Backend" -Port 8080 -ScriptName "dev-backend.ps1" -StdoutName "backend-dev.log" -StderrName "backend-dev.err.log"
Start-DevProcess -Name "Frontend" -Port 5173 -ScriptName "dev-frontend.ps1" -StdoutName "frontend-dev.log" -StderrName "frontend-dev.err.log"

if ($NoWait) {
  Write-Host "Started launchers. Skipping readiness checks because -NoWait was supplied."
  Write-Host "Bilibili page: http://127.0.0.1:5173/bilibili"
  exit 0
}

Write-Host "Waiting for backend and frontend..."
Wait-ForHttp -Url "http://127.0.0.1:8080/actuator/health" -TimeoutSeconds $TimeoutSeconds | Out-Null
Wait-ForHttp -Url "http://127.0.0.1:5173/bilibili" -TimeoutSeconds $TimeoutSeconds | Out-Null

Write-Host ""
Write-Host "Dev environment is ready."
Write-Host "Bilibili page: http://127.0.0.1:5173/bilibili"
Write-Host "Backend health: http://127.0.0.1:8080/actuator/health"
Write-Host "Logs: $logDir"
