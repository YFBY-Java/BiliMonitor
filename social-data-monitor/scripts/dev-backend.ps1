$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot "load-env.ps1") -Path (Join-Path $root ".env.local")
Set-Location (Join-Path $root "backend")
.\mvnw.cmd spring-boot:run
