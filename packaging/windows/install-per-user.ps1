#Requires -Version 5.1
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDir,
    [string]$DestDir = $(Join-Path $env:LOCALAPPDATA "Programs\DataExpert")
)

$ErrorActionPreference = "Stop"
$SourceDir = (Resolve-Path $SourceDir).Path

if (-not (Test-Path (Join-Path $SourceDir "DataExpert.exe"))) {
    throw "DataExpert.exe introuvable dans $SourceDir"
}

Write-Host "Installation vers $DestDir"
New-Item -ItemType Directory -Force -Path $DestDir | Out-Null

robocopy $SourceDir $DestDir /E /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
$robo = $LASTEXITCODE
if ($robo -ge 8) {
    throw "Copie echouee (robocopy code $robo)"
}

$ws = New-Object -ComObject WScript.Shell
$programs = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\DataExpert"
New-Item -ItemType Directory -Force -Path $programs | Out-Null

$exe = Join-Path $DestDir "DataExpert.exe"
$sc = $ws.CreateShortcut((Join-Path $programs "DataExpert.lnk"))
$sc.TargetPath = $exe
$sc.WorkingDirectory = $DestDir
$sc.Save()

$desktop = [Environment]::GetFolderPath("Desktop")
$sc3 = $ws.CreateShortcut((Join-Path $desktop "DataExpert.lnk"))
$sc3.TargetPath = $exe
$sc3.WorkingDirectory = $DestDir
$sc3.Save()

Write-Host "OK. Lancez DataExpert depuis le Bureau."
Write-Output $DestDir
