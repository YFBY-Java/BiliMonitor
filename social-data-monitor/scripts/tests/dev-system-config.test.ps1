$ErrorActionPreference = "Stop"

$modulePath = Join-Path (Split-Path -Parent $PSScriptRoot) "dev-system-config.ps1"
if (-not (Test-Path -LiteralPath $modulePath)) {
  Write-Error "Initialize-IntegratedDevEnvironment is not defined."
  exit 1
}

. $modulePath

$assertionCount = 0
function Assert-True {
  param(
    [bool]$Condition,
    [string]$Message
  )

  $script:assertionCount++
  if (-not $Condition) {
    throw $Message
  }
}

function Set-ProcessEnvironmentValue {
  param(
    [string]$Name,
    [AllowNull()][string]$Value
  )

  [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
}

$trackedEnvironmentNames = @(
  "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY",
  "SPRING_PROFILES_ACTIVE",
  "SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED",
  "PLAYWRIGHT_BROWSERS_PATH",
  "DOUYIN_WORKER_HOST",
  "DOUYIN_WORKER_PORT",
  "DOUYIN_WORKER_HEADLESS",
  "SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL",
  "SOCIAL_MONITOR_ENV_PRESERVE_EXISTING"
)
$savedEnvironment = @{}
foreach ($name in $trackedEnvironmentNames) {
  $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
  Set-ProcessEnvironmentValue -Name $name -Value $null
}

$tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$testRoot = Join-Path $tempBase ("social-monitor-config-" + [guid]::NewGuid().ToString("N"))
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
New-Item -ItemType Directory -Force -Path $testRoot | Out-Null

try {
  $missingRoot = Join-Path $testRoot "missing"
  New-Item -ItemType Directory -Force -Path $missingRoot | Out-Null
  $missingEnvPath = Join-Path $missingRoot ".env.local"
  [System.IO.File]::WriteAllText($missingEnvPath, "KEEP_ME=1`n", $utf8NoBom)

  $first = Initialize-IntegratedDevEnvironment -Root $missingRoot
  $generatedKey = $env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY
  Assert-True -Condition $first.KeyGenerated -Message "Missing key was not generated."
  Assert-True -Condition (Test-Base64Key32 -Value $generatedKey) -Message "Generated key is not a base64-encoded 32-byte value."
  $firstFile = [System.IO.File]::ReadAllText($missingEnvPath)
  Assert-True -Condition $firstFile.Contains("KEEP_ME=1") -Message "Existing env content was not preserved."
  Assert-True -Condition $firstFile.Contains("SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY=$generatedKey") -Message "Generated key was not persisted."

  $second = Initialize-IntegratedDevEnvironment -Root $missingRoot
  $secondFile = [System.IO.File]::ReadAllText($missingEnvPath)
  $keyLineCount = ([regex]::Matches($secondFile, "(?m)^SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY=")).Count
  Assert-True -Condition (-not $second.KeyGenerated) -Message "Existing key was generated again."
  Assert-True -Condition ($env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY -eq $generatedKey) -Message "Existing key changed on the second initialization."
  Assert-True -Condition ($keyLineCount -eq 1) -Message "The env file contains duplicate key entries."
  Assert-True -Condition ($env:SPRING_PROFILES_ACTIVE -eq "dev,douyin") -Message "Spring profiles were not integrated."
  Assert-True -Condition ($env:SOCIAL_MONITOR_DOUYIN_AUTH_ENABLED -eq "true") -Message "Douyin backend auth was not enabled."
  Assert-True -Condition ($env:DOUYIN_WORKER_HOST -eq "127.0.0.1") -Message "Worker host default is wrong."
  Assert-True -Condition ($env:DOUYIN_WORKER_PORT -eq "8787") -Message "Worker port default is wrong."
  Assert-True -Condition ($env:SOCIAL_MONITOR_DOUYIN_WORKER_BASE_URL -eq "http://127.0.0.1:8787") -Message "Worker base URL default is wrong."
  Assert-True -Condition ($env:SOCIAL_MONITOR_ENV_PRESERVE_EXISTING -eq "true") -Message "Child processes will not preserve integrated values."

  $validRoot = Join-Path $testRoot "valid"
  New-Item -ItemType Directory -Force -Path $validRoot | Out-Null
  $validEnvPath = Join-Path $validRoot ".env.local"
  $stableKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
  [System.IO.File]::WriteAllText($validEnvPath, "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY=$stableKey`n", $utf8NoBom)
  Set-ProcessEnvironmentValue -Name "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY" -Value $stableKey
  $valid = Initialize-IntegratedDevEnvironment -Root $validRoot
  Assert-True -Condition (-not $valid.KeyGenerated) -Message "A valid existing key was replaced."
  Assert-True -Condition ($env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY -eq $stableKey) -Message "A valid existing key changed."

  $invalidRoot = Join-Path $testRoot "invalid"
  New-Item -ItemType Directory -Force -Path $invalidRoot | Out-Null
  $invalidEnvPath = Join-Path $invalidRoot ".env.local"
  $invalidContents = "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY=invalid-user-value`nKEEP_ME=1`n"
  [System.IO.File]::WriteAllText($invalidEnvPath, $invalidContents, $utf8NoBom)
  Set-ProcessEnvironmentValue -Name "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY" -Value "invalid-user-value"
  $invalidRejected = $false
  try {
    Initialize-IntegratedDevEnvironment -Root $invalidRoot | Out-Null
  } catch {
    $invalidRejected = $_.Exception.Message -like "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY must be*"
  }
  Assert-True -Condition $invalidRejected -Message "An invalid user key was not rejected with the expected error."
  Assert-True -Condition (([System.IO.File]::ReadAllText($invalidEnvPath)) -eq $invalidContents) -Message "An invalid user key was overwritten."
} finally {
  foreach ($name in $trackedEnvironmentNames) {
    Set-ProcessEnvironmentValue -Name $name -Value $savedEnvironment[$name]
  }

  $resolvedTestRoot = [System.IO.Path]::GetFullPath($testRoot)
  if ($resolvedTestRoot.StartsWith($tempBase, [System.StringComparison]::OrdinalIgnoreCase)) {
    Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force -ErrorAction SilentlyContinue
  }
}

Write-Output "All dev system configuration tests passed ($assertionCount assertions)."
