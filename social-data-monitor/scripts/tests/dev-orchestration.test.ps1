$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Split-Path -Parent $scriptsRoot
$start = Get-Content -LiteralPath (Join-Path $scriptsRoot "dev-start.ps1") -Encoding UTF8 -Raw
$stop = Get-Content -LiteralPath (Join-Path $scriptsRoot "dev-stop.ps1") -Encoding UTF8 -Raw
$legacyStart = Get-Content -LiteralPath (Join-Path $scriptsRoot "dev-start-douyin.ps1") -Encoding UTF8 -Raw
$legacyStop = Get-Content -LiteralPath (Join-Path $scriptsRoot "dev-stop-douyin.ps1") -Encoding UTF8 -Raw
$envExample = Get-Content -LiteralPath (Join-Path $projectRoot ".env.example") -Encoding UTF8 -Raw
$readme = Get-Content -LiteralPath (Join-Path $projectRoot "README.md") -Encoding UTF8 -Raw

$assertionCount = 0
function Assert-Matches {
  param(
    [string]$Value,
    [string]$Pattern,
    [string]$Message
  )

  $script:assertionCount++
  if ($Value -notmatch $Pattern) {
    throw $Message
  }
}

function Assert-DoesNotMatch {
  param(
    [string]$Value,
    [string]$Pattern,
    [string]$Message
  )

  $script:assertionCount++
  if ($Value -match $Pattern) {
    throw $Message
  }
}

Assert-Matches -Value $start -Pattern 'dev-system-config\.ps1' -Message "Unified start does not load the integrated configuration module."
Assert-Matches -Value $start -Pattern 'Initialize-IntegratedDevEnvironment' -Message "Unified start does not initialize Douyin."
Assert-Matches -Value $start -Pattern 'function\s+Start-DouyinWorker' -Message "Unified start does not define the Worker launcher."
Assert-Matches -Value $start -Pattern 'Start-DouyinWorker\s+-Port' -Message "Unified start does not launch the Worker."
Assert-Matches -Value $start -Pattern '/internal/v1/health' -Message "Unified start does not wait for Worker health."
Assert-DoesNotMatch -Value $start -Pattern 'VITE_DOUYIN_ENABLED' -Message "Unified start still uses the obsolete frontend flag."

Assert-Matches -Value $stop -Pattern 'load-env\.ps1' -Message "Unified stop does not load the configured Worker port."
Assert-Matches -Value $stop -Pattern 'Stop-ProjectListener\s+-Name\s+"Douyin Worker"' -Message "Unified stop does not stop the Worker."

Assert-Matches -Value $legacyStart -Pattern 'dev-start\.ps1' -Message "Legacy Douyin start does not forward to unified start."
Assert-DoesNotMatch -Value $legacyStart -Pattern 'Start-Process|DOUYIN_WORKER_PORT|VITE_DOUYIN_ENABLED' -Message "Legacy Douyin start still owns orchestration."
Assert-Matches -Value $legacyStop -Pattern 'dev-stop\.ps1' -Message "Legacy Douyin stop does not forward to unified stop."
Assert-DoesNotMatch -Value $legacyStop -Pattern 'Stop-Process|DOUYIN_WORKER_PORT' -Message "Legacy Douyin stop still owns orchestration."

Assert-Matches -Value $envExample -Pattern '(?m)^SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED=true$' -Message "The env template does not enable the integrated Douyin module."
Assert-DoesNotMatch -Value $envExample -Pattern 'VITE_DOUYIN_ENABLED' -Message "The env template still exposes the obsolete frontend flag."
Assert-Matches -Value $readme -Pattern 'dev-start\.cmd.*PostgreSQL.*Douyin Worker.*Spring Boot.*Vite' -Message "README does not describe the unified startup flow."
Assert-DoesNotMatch -Value $readme -Pattern 'VITE_DOUYIN_ENABLED' -Message "README still documents the obsolete isolated frontend flag."

Write-Output "All dev orchestration contract tests passed ($assertionCount assertions)."
