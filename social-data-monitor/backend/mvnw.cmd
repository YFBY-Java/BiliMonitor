@echo off
setlocal
set "BASEDIR=%~dp0"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%BASEDIR%.mvn\wrapper\mvnw-lite.ps1" %*
exit /b %ERRORLEVEL%
