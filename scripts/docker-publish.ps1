# Construit api + web et les pousse vers GHCR (Windows).
# Usage :
#   copy .env.example .env
#   # DOCKER_REGISTRY=ghcr.io/fabien112
#   # IMAGE_TAG=latest
#   echo TOKEN | docker login ghcr.io -u fabien112 --password-stdin
#   .\scripts\docker-publish.ps1

param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
Set-Location $ProjectRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Docker introuvable. Installez Docker Desktop sur cette machine de build." -ForegroundColor Red
    exit 1
}

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Fichier .env cree. Renseigne DOCKER_REGISTRY puis relance." -ForegroundColor Yellow
    exit 1
}

$envVars = @{}
Get-Content ".env" | ForEach-Object {
    $line = $_.Trim()
    if ($line -match '^([^#=]+)=(.*)$') {
        $envVars[$matches[1]] = $matches[2].Trim('"').Trim("'")
    }
}

$registry = "$($envVars['DOCKER_REGISTRY'])".TrimEnd('/')
$tag = if ($envVars['IMAGE_TAG']) { $envVars['IMAGE_TAG'] } else { "latest" }

if ([string]::IsNullOrWhiteSpace($registry)) {
    Write-Host "DOCKER_REGISTRY est vide dans .env" -ForegroundColor Red
    Write-Host "Exemple GHCR : DOCKER_REGISTRY=ghcr.io/fabien112"
    exit 1
}

$env:API_IMAGE = "$registry/dataexpert-api:$tag"
$env:WEB_IMAGE = "$registry/dataexpert-web:$tag"

Write-Host "API : $($env:API_IMAGE)"
Write-Host "Web : $($env:WEB_IMAGE)"

docker compose -f docker-compose.yml -f docker-compose.prod.yml build --pull api web
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker push $env:API_IMAGE
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
docker push $env:WEB_IMAGE
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Images publiees." -ForegroundColor Green
Write-Host "Sur le VPS, meme DOCKER_REGISTRY et IMAGE_TAG, puis :"
Write-Host "  bash deploy.sh pull"
Write-Host "  bash deploy.sh up"
