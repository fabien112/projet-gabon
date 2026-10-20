# Sauvegarde PostgreSQL (volume Docker)
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$OutDir = "backups"
)

$ErrorActionPreference = "Stop"
Set-Location $ProjectRoot

if (-not (Test-Path ".env")) {
    Write-Host ".env introuvable" -ForegroundColor Red
    exit 1
}

$envVars = @{}
Get-Content ".env" | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $envVars[$matches[1]] = $matches[2] }
}

$db = if ($envVars["POSTGRES_DB"]) { $envVars["POSTGRES_DB"] } else { "dss" }
$user = if ($envVars["POSTGRES_USER"]) { $envVars["POSTGRES_USER"] } else { "dss_user" }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outFile = Join-Path $OutDir "dss-backup-$stamp.sql"

Write-Host "Sauvegarde $db -> $outFile"
docker compose exec -T db pg_dump -U $user -d $db | Out-File -FilePath $outFile -Encoding utf8

Write-Host "Terminé : $outFile" -ForegroundColor Green
