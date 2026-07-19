function Test-Base64Key32 {
  param([AllowNull()][string]$Value)

  if ([string]::IsNullOrWhiteSpace($Value)) {
    return $false
  }

  try {
    return ([Convert]::FromBase64String($Value).Length -eq 32)
  } catch {
    return $false
  }
}

function Set-EnvFileValue {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$Value
  )

  $directory = Split-Path -Parent $Path
  if ([string]::IsNullOrWhiteSpace($directory)) {
    throw "Cannot resolve the parent directory for env file: $Path"
  }
  if (-not (Test-Path -LiteralPath $directory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
  }

  $lines = if (Test-Path -LiteralPath $Path) {
    [System.IO.File]::ReadAllLines($Path)
  } else {
    [string[]]@()
  }
  $pattern = "^\s*" + [regex]::Escape($Name) + "\s*="
  $found = $false
  $updatedLines = @(
    foreach ($line in $lines) {
      if ($line -match $pattern) {
        $found = $true
        "$Name=$Value"
      } else {
        $line
      }
    }
  )
  if (-not $found) {
    $updatedLines += "$Name=$Value"
  }

  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllLines($Path, [string[]]$updatedLines, $utf8NoBom)
}

function New-Base64Key32 {
  $bytes = New-Object byte[] 32
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try {
    $rng.GetBytes($bytes)
  } finally {
    $rng.Dispose()
  }
  [Convert]::ToBase64String($bytes)
}

function Initialize-IntegratedDevEnvironment {
  param([Parameter(Mandatory = $true)][string]$Root)

  $resolvedRoot = [System.IO.Path]::GetFullPath($Root)
  $envPath = Join-Path $resolvedRoot ".env.local"
  $key = $env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY
  $keyGenerated = $false
  $placeholderKey = "REPLACE_WITH_BASE64_32_BYTE_KEY"

  if ([string]::IsNullOrWhiteSpace($key) -or $key.Trim() -eq $placeholderKey) {
    $key = New-Base64Key32
    Set-EnvFileValue `
      -Path $envPath `
      -Name "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY" `
      -Value $key
    $keyGenerated = $true
  } elseif (-not (Test-Base64Key32 -Value $key)) {
    throw "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY must be a base64-encoded 32-byte key. Fix $envPath; the existing value was not overwritten."
  }

  $env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY = $key
  $env:SPRING_PROFILES_ACTIVE = "dev,douyin"
  $env:SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED = "true"
  $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $resolvedRoot ".dev-tools\playwright"
  if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_HOST)) {
    $env:DOUYIN_WORKER_HOST = "127.0.0.1"
  }
  if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_PORT)) {
    $env:DOUYIN_WORKER_PORT = "8787"
  }
  if ([string]::IsNullOrWhiteSpace($env:DOUYIN_WORKER_HEADLESS)) {
    $env:DOUYIN_WORKER_HEADLESS = "false"
  }

  $workerPort = 0
  if (-not [int]::TryParse($env:DOUYIN_WORKER_PORT, [ref]$workerPort) -or
      $workerPort -lt 1 -or
      $workerPort -gt 65535) {
    throw "DOUYIN_WORKER_PORT must be an integer between 1 and 65535."
  }
  if ([string]::IsNullOrWhiteSpace($env:SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL)) {
    $env:SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL = "http://127.0.0.1:$workerPort"
  }
  $env:SOCIAL_MONITOR_ENV_PRESERVE_EXISTING = "true"

  [PSCustomObject]@{
    EnvFilePath = $envPath
    KeyGenerated = $keyGenerated
    WorkerBaseUrl = $env:SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL
    WorkerPort = $workerPort
  }
}
