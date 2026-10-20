# DataExpert — déploiement Docker sous Windows.
# Sur le VPS Linux, utiliser plutôt : bash deploy.sh
# Usage : .\scripts\docker-deploy.ps1
#         .\scripts\docker-deploy.ps1 -Prod
#         .\scripts\docker-deploy.ps1 -BuildOnly

param(
    [switch]$Prod,
    [switch]$BuildOnly,
    [switch]$Down,
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
Set-Location $ProjectRoot

function Require-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Host "Docker introuvable. Installez Docker Desktop / Engine." -ForegroundColor Red
        exit 1
    }
}

function Ensure-Env {
    if (-not (Test-Path ".env")) {
        if (Test-Path ".env.example") {
            Copy-Item ".env.example" ".env"
            Write-Host "Fichier .env créé depuis .env.example — éditez-le avant la prod." -ForegroundColor Yellow
        } else {
            Write-Host ".env manquant." -ForegroundColor Red
            exit 1
        }
    }
}

Require-Docker
Ensure-Env

$composeArgs = @("compose")
if ($Prod) {
    $composeArgs += @("-f", "docker-compose.yml", "-f", "docker-compose.prod.yml")
}

if ($Down) {
    & docker @composeArgs down
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "=== DataExpert Docker ===" -ForegroundColor Cyan
Write-Host "Mode : $(if ($Prod) { 'PRODUCTION' } else { 'STANDARD' })"
Write-Host ""

& docker @composeArgs build --pull
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($BuildOnly) {
    Write-Host "Build terminé (-BuildOnly)." -ForegroundColor Green
    exit 0
}

& docker @composeArgs up -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Attente des services..."
$deadline = (Get-Date).AddMinutes(3)
do {
    Start-Sleep -Seconds 5
    $api = docker inspect --format='{{.State.Health.Status}}' dss-api 2>$null
    $db = docker inspect --format='{{.State.Health.Status}}' dss-db 2>$null
} while ((($api -ne "healthy") -or ($db -ne "healthy")) -and (Get-Date) -lt $deadline)

& docker @composeArgs ps

$envVars = @{}
Get-Content ".env" | ForEach-Object {
    $line = $_.Trim()
    if ($line -match '^([^#=]+)=(.*)$') {
        $envVars[$matches[1]] = $matches[2]
    }
}
$webPort = if ($envVars["WEB_PORT"]) { $envVars["WEB_PORT"] } else { "80" }
$pgPort = if ($envVars["PGADMIN_PORT"]) { $envVars["PGADMIN_PORT"] } else { "5050" }

Write-Host ""
Write-Host "Stack démarrée." -ForegroundColor Green
Write-Host "  Application : http://localhost:${webPort}/"
if (-not $Prod) {
    Write-Host "  pgAdmin       : http://localhost:${pgPort}/"
} else {
    Write-Host "  pgAdmin       : http://127.0.0.1:${pgPort}/ (localhost uniquement)"
}
Write-Host ""
Write-Host "Logs API : docker compose logs -f api"
