#Requires -Version 5.1
$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-21.0.12" }
$Jpackage = Join-Path $JavaHome "bin\jpackage.exe"
$JarTool = Join-Path $JavaHome "bin\jar.exe"

if (-not (Test-Path $Jpackage)) {
    throw "jpackage introuvable : $Jpackage"
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$(Join-Path $JavaHome 'bin');$env:Path"

Write-Host "[1/5] Build frontend..."
Push-Location (Join-Path $Root "dss-frontend")
try {
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "npm run build a echoue" }
} finally {
    Pop-Location
}

Write-Host "[2/5] Package JAR..."
Push-Location (Join-Path $Root "dss-integration")
try {
    & ".\mvnw.cmd" -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "mvn package a echoue" }
} finally {
    Pop-Location
}

$JarSrc = Join-Path $Root "dss-integration\target\dss-integration-0.1.0-SNAPSHOT.jar"
if (-not (Test-Path $JarSrc)) {
    throw "JAR introuvable : $JarSrc"
}

$MainClass = "org.springframework.boot.loader.launch.JarLauncher"
$tmp = Join-Path $env:TEMP "dataexpert-mf"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
Push-Location $tmp
try {
    & $JarTool xf $JarSrc META-INF/MANIFEST.MF
    $mf = Get-Content "META-INF\MANIFEST.MF" -Raw
    if ($mf -match "Main-Class:\s*(\S+)") {
        $MainClass = $Matches[1].Trim()
    }
    Write-Host "Main-Class = $MainClass"
} finally {
    Pop-Location
}

Write-Host "[3/5] Image jpackage (JRE embarquee)..."
$Out = Join-Path $Root "dist-installer"
$InputDir = Join-Path $Out "jpackage-input"
$ImageDir = Join-Path $Out "app-image"
Remove-Item $InputDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $ImageDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $InputDir | Out-Null
Copy-Item $JarSrc (Join-Path $InputDir "dss-integration.jar")

& $Jpackage `
    --type app-image `
    --name DataExpert `
    --app-version 0.1.0 `
    --vendor DataExpert `
    --description "DataExpert - comptage personnes DSS" `
    --input $InputDir `
    --main-jar dss-integration.jar `
    --main-class $MainClass `
    --dest $ImageDir `
    --java-options "-Dfile.encoding=UTF-8" `
    --java-options "-Duser.timezone=Africa/Libreville" `
    --win-console

if ($LASTEXITCODE -ne 0) { throw "jpackage a echoue" }

$AppDir = Join-Path $ImageDir "DataExpert"
$WebSrc = Join-Path $Root "dss-frontend\dist"
if (-not (Test-Path (Join-Path $WebSrc "index.html"))) {
    throw "Frontend build introuvable : $WebSrc"
}
if (Test-Path (Join-Path $AppDir "web")) {
    Remove-Item (Join-Path $AppDir "web") -Recurse -Force
}
Copy-Item $WebSrc (Join-Path $AppDir "web") -Recurse
Copy-Item (Join-Path $PSScriptRoot "site.env") (Join-Path $AppDir ".env") -Force
Copy-Item (Join-Path $PSScriptRoot "Lancer-DataExpert.bat") (Join-Path $AppDir "Lancer-DataExpert.bat") -Force

$DistClient = Join-Path $Root "dist-client"
if (Test-Path (Join-Path $DistClient "web")) {
    Remove-Item (Join-Path $DistClient "web") -Recurse -Force
}
Copy-Item $WebSrc (Join-Path $DistClient "web") -Recurse
Copy-Item $JarSrc (Join-Path $DistClient "dss-integration.jar") -Force

Write-Host "[4/5] Installeur..."
$Iss = Join-Path $PSScriptRoot "DataExpert.iss"
$IsccCandidates = @(
    (Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe"),
    "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
    "C:\Program Files\Inno Setup 6\ISCC.exe",
    (Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 7\ISCC.exe"),
    "C:\Program Files (x86)\Inno Setup 7\ISCC.exe"
)
$Iscc = $IsccCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if ($Iscc) {
    Write-Host "Inno Setup : $Iscc"
    & $Iscc "/DAppImageDir=$AppDir" $Iss
    if ($LASTEXITCODE -ne 0) { throw "ISCC a echoue" }
    Write-Host "Installeur : $(Join-Path $Out 'DataExpert-Setup.exe')"
} else {
    Write-Host "Inno Setup absent - creation de DataExpert-Setup.bat"
    $SetupBat = Join-Path $Out "DataExpert-Setup.bat"
    $InstallPs1 = Join-Path $PSScriptRoot "install-per-user.ps1"
    @(
        '@echo off',
        'setlocal',
        'cd /d "%~dp0"',
        ('powershell -NoProfile -ExecutionPolicy Bypass -File "{0}" -SourceDir "%~dp0app-image\DataExpert"' -f $InstallPs1),
        'echo.',
        'echo Installe dans %LOCALAPPDATA%\Programs\DataExpert',
        'echo Lancez DataExpert depuis le Bureau ou le menu Demarrer',
        'pause'
    ) | Set-Content -Path $SetupBat -Encoding ASCII
}

Write-Host "[5/5] Archive portable..."
$Zip = Join-Path $Out "DataExpert-portable.zip"
if (Test-Path $Zip) { Remove-Item $Zip -Force }
Compress-Archive -Path $AppDir -DestinationPath $Zip -Force

Write-Host ""
Write-Host "OK"
Write-Host "  Image    : $AppDir"
Write-Host "  Portable : $Zip"
Write-Host "Pret : lancer DataExpert-Setup.exe puis DataExpert"
