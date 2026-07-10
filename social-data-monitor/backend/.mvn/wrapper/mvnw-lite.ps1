$ErrorActionPreference = "Stop"

$wrapperDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Split-Path -Parent (Split-Path -Parent $wrapperDir)
$propertiesPath = Join-Path $wrapperDir "maven-wrapper.properties"
$props = @{}
Get-Content $propertiesPath | ForEach-Object {
    if ($_ -match "^\s*([^#][^=]+)=(.+)$") {
        $props[$matches[1].Trim()] = $matches[2].Trim()
    }
}

$mavenVersion = $props["mavenVersion"]
$downloadUrl = $props["mavenDownloadUrl"]
$installDir = Join-Path $wrapperDir "apache-maven-$mavenVersion"
$mavenCmd = Join-Path $installDir "bin\mvn.cmd"

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $javaHomeLine = (cmd /c "java -XshowSettings:properties -version 2>&1" | Select-String "java.home =").Line
    if ($javaHomeLine) {
        $env:JAVA_HOME = ($javaHomeLine -replace ".*java.home =\s*", "").Trim()
        Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
    }
}

if (-not (Test-Path $mavenCmd)) {
    $zipPath = Join-Path $wrapperDir "apache-maven-$mavenVersion-bin.zip"
    if (-not (Test-Path $zipPath)) {
        Write-Host "Downloading Maven $mavenVersion..."
        Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath
    }
    $extractRoot = Join-Path $wrapperDir "_extract"
    if (Test-Path $extractRoot) {
        Remove-Item -LiteralPath $extractRoot -Recurse -Force
    }
    Expand-Archive -Path $zipPath -DestinationPath $extractRoot -Force
    $expanded = Join-Path $extractRoot "apache-maven-$mavenVersion"
    Move-Item -LiteralPath $expanded -Destination $installDir -Force
    Remove-Item -LiteralPath $extractRoot -Recurse -Force
}

& $mavenCmd @args
exit $LASTEXITCODE
