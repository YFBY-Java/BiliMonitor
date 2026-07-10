param(
  [string]$Path = (Join-Path (Split-Path -Parent $PSScriptRoot) ".env.local")
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
if ([System.IO.Path]::IsPathRooted($Path)) {
  $candidatePath = $Path
} else {
  $candidatePath = Join-Path $projectRoot $Path
}

$fullPath = [System.IO.Path]::GetFullPath($candidatePath)
$projectRootWithSeparator = $projectRoot.TrimEnd([char[]]@("\", "/")) + [System.IO.Path]::DirectorySeparatorChar
if (-not $fullPath.StartsWith($projectRootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw "Local env file must stay inside the project directory: $projectRoot"
}

if (-not (Test-Path -LiteralPath $fullPath)) {
  Write-Warning "Local env file not found: $fullPath"
  return
}

foreach ($rawLine in Get-Content -LiteralPath $fullPath) {
  $line = $rawLine.Trim()
  if ($line.Length -eq 0 -or $line.StartsWith("#")) {
    continue
  }

  $separatorIndex = $line.IndexOf("=")
  if ($separatorIndex -le 0) {
    continue
  }

  $key = $line.Substring(0, $separatorIndex).Trim()
  if ($key -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") {
    Write-Warning "Skipping invalid env key: $key"
    continue
  }

  $value = $line.Substring($separatorIndex + 1)
  if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
    $value = $value.Substring(1, $value.Length - 2)
  }

  [Environment]::SetEnvironmentVariable($key, $value, "Process")
}
