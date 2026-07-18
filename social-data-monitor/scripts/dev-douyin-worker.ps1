param(
  [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$workerRoot = Join-Path $root "douyin-worker"
$browserRoot = Join-Path $root ".dev-tools\playwright"

& (Join-Path $PSScriptRoot "load-env.ps1") -Path (Join-Path $root ".env.local")

if ([string]::IsNullOrWhiteSpace($env:PLAYWRIGHT_BROWSERS_PATH)) {
  $env:PLAYWRIGHT_BROWSERS_PATH = $browserRoot
}
if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_HEADLESS)) {
  $env:DOUYIN_WORKER_HEADLESS = "false"
}

New-Item -ItemType Directory -Force -Path $env:PLAYWRIGHT_BROWSERS_PATH | Out-Null
Set-Location $workerRoot

if (-not $SkipInstall -and -not (Test-Path (Join-Path $workerRoot "node_modules\playwright"))) {
  Write-Host "Installing Douyin Worker dependencies..."
  & npm.cmd ci
  if ($LASTEXITCODE -ne 0) {
    throw "npm ci failed for Douyin Worker."
  }
}

$chromium = Get-ChildItem -LiteralPath $env:PLAYWRIGHT_BROWSERS_PATH -Directory -Filter "chromium-*" -ErrorAction SilentlyContinue |
  Select-Object -First 1
if (-not $SkipInstall -and -not $chromium) {
  Write-Host "Installing project-local Playwright Chromium..."
  & npm.cmd run install:chromium
  if ($LASTEXITCODE -ne 0) {
    throw "Playwright Chromium installation failed."
  }
}

$node = (Get-Command node.exe -ErrorAction Stop).Source
$server = Join-Path $workerRoot "src\server.js"
Write-Host "Starting Douyin Worker on $($env:DOUYIN_WORKER_HOST):$($env:DOUYIN_WORKER_PORT)..."
& $node $server
