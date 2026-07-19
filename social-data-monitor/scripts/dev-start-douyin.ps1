param(
  [switch]$NoWait,
  [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "dev-start.ps1") @PSBoundParameters
