# Migration H2 -> PostgreSQL — passage en production DataExpert
#
# Scénario : sync terminée en local (JAR + H2), puis bascule Docker + PostgreSQL.
#
# Usage :
#   .\scripts\migrate-h2-to-postgres.ps1
#   .\scripts\migrate-h2-to-postgres.ps1 -DryRun
#   .\scripts\migrate-h2-to-postgres.ps1 -H2BasePath "C:\DataExpert\dist-client\data\dss"
#
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$H2BasePath = "",
    [string]$EnvFile = "",
    [switch]$DryRun,
    [switch]$SkipDockerDb,
    [switch]$NoComposeUp
)

$ErrorActionPreference = "Stop"

function Load-DotEnv {
    param([string]$Path)
    $vars = @{}
    if (-not (Test-Path $Path)) { return $vars }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $eq = $line.IndexOf("=")
        if ($eq -le 0) { return }
        $key = $line.Substring(0, $eq).Trim()
        $val = $line.Substring($eq + 1).Trim()
        if ($val.Length -ge 2) {
            $q0 = $val[0]; $q1 = $val[$val.Length - 1]
            if (($q0 -eq '"' -and $q1 -eq '"') -or ($q0 -eq "'" -and $q1 -eq "'")) {
                $val = $val.Substring(1, $val.Length - 2)
            }
        }
        $vars[$key] = $val
    }
    return $vars
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " DataExpert : migration H2 -> PostgreSQL" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Set-Location $ProjectRoot

if (-not $EnvFile) {
    if (Test-Path "$ProjectRoot\.env") { $EnvFile = "$ProjectRoot\.env" }
    elseif (Test-Path "$ProjectRoot\dist-client\.env") { $EnvFile = "$ProjectRoot\dist-client\.env" }
    else { $EnvFile = "$ProjectRoot\.env.example" }
}

$envVars = Load-DotEnv $EnvFile
Write-Host "Config : $EnvFile"

$pgDb = if ($envVars["POSTGRES_DB"]) { $envVars["POSTGRES_DB"] } else { "dss" }
$pgUser = if ($envVars["POSTGRES_USER"]) { $envVars["POSTGRES_USER"] } else { "dss_user" }
$pgPassword = $envVars["POSTGRES_PASSWORD"]
if (-not $pgPassword) { $pgPassword = $envVars["DB_PASSWORD"] }
$pgPort = if ($envVars["POSTGRES_PORT"]) { $envVars["POSTGRES_PORT"] } else { "5432" }
$webPort = if ($envVars["WEB_PORT"]) { $envVars["WEB_PORT"] } else { "80" }
$pgAdminPort = if ($envVars["PGADMIN_PORT"]) { $envVars["PGADMIN_PORT"] } else { "5050" }

if (-not $pgPassword) {
    $secure = Read-Host "Mot de passe PostgreSQL ($pgUser)" -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $pgPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto($ptr)
}

if (-not $H2BasePath) {
    foreach ($candidate in @(
            "$ProjectRoot\dist-client\data\dss",
            "$ProjectRoot\dss-integration\data\dss",
            "$ProjectRoot\data\dss"
        )) {
        if (Test-Path "$candidate.mv.db") {
            $H2BasePath = $candidate
            break
        }
    }
}

if (-not $H2BasePath -or -not (Test-Path "$H2BasePath.mv.db")) {
    Write-Host "Base H2 introuvable." -ForegroundColor Red
    Write-Host "Exemple : -H2BasePath '$ProjectRoot\dist-client\data\dss'"
    exit 1
}

$h2Resolved = (Resolve-Path $H2BasePath).Path
Write-Host "Source H2 : $h2Resolved"
Write-Host "Cible PG  : ${pgUser}@localhost:${pgPort}/${pgDb}"
Write-Host ""

Write-Host "[Prérequis]" -ForegroundColor Yellow
Write-Host "  1. Sync DSS terminée en local (JAR + H2)"
Write-Host "  2. Backend JAR arrêté (stop.bat) — fichier H2 libéré"
Write-Host "  3. Docker Desktop démarré"
Write-Host ""
$confirm = Read-Host "Continuer ? (O/n)"
if ($confirm -match "^[nN]") { exit 0 }

# --- 1. PostgreSQL ---
if (-not $SkipDockerDb) {
    Write-Host ""
    Write-Host "[1/3] Démarrage PostgreSQL..." -ForegroundColor Yellow
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Host "Docker introuvable." -ForegroundColor Red
        exit 1
    }
    docker compose --env-file $EnvFile up -d db
    $deadline = (Get-Date).AddMinutes(2)
    $healthy = ""
    do {
        Start-Sleep -Seconds 3
        $healthy = docker inspect --format='{{.State.Health.Status}}' dss-db 2>$null
    } while ($healthy -ne "healthy" -and (Get-Date) -lt $deadline)
    if ($healthy -ne "healthy") {
        Write-Host "PostgreSQL non prêt. Logs : docker compose logs db" -ForegroundColor Red
        exit 1
    }
    Write-Host "PostgreSQL OK" -ForegroundColor Green

    # Exposer 5432 si pas déjà mappé (migration depuis l'hôte)
    $mapped = docker port dss-db 5432 2>$null
    if (-not $mapped) {
        Write-Host "Note : Postgres n'est pas exposé sur l'hôte."
        Write-Host "       Décommentez ports:5432 dans docker-compose.yml si la migration échoue à se connecter."
    }
} else {
    Write-Host "[1/3] PostgreSQL : -SkipDockerDb" -ForegroundColor Yellow
}

# --- 2. Migration Java ---
Write-Host ""
Write-Host "[2/3] Migration des données..." -ForegroundColor Yellow
$mvnw = Join-Path $ProjectRoot "dss-integration\mvnw.cmd"
if (-not (Test-Path $mvnw)) {
    Write-Host "Introuvable : $mvnw" -ForegroundColor Red
    exit 1
}

$dryRunArg = if ($DryRun) { "--dry-run" } else { "" }
$pgUrl = "jdbc:postgresql://localhost:${pgPort}/${pgDb}"

Push-Location (Join-Path $ProjectRoot "dss-integration")
try {
    & $mvnw -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java `
        "-Dexec.mainClass=com.company.dss.tools.H2ToPostgresMigrator" `
        "-Dexec.classpathScope=runtime" `
        "-Dexec.args=--h2-path `"$h2Resolved`" --pg-url `"$pgUrl`" --pg-user `"$pgUser`" --pg-password `"$pgPassword`" $dryRunArg"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Migration échouée." -ForegroundColor Red
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}

if ($DryRun) {
    Write-Host ""
    Write-Host "Dry-run terminé. Relancez sans -DryRun pour migrer." -ForegroundColor Green
    exit 0
}

# --- 3. Vérification + stack ---
Write-Host ""
Write-Host "[3/3] Vérification et démarrage stack..." -ForegroundColor Yellow
docker compose --env-file $EnvFile exec -T db psql -U $pgUser -d $pgDb -c `
    "SELECT 'people_counting_hourly' AS tbl, COUNT(*) AS n, MIN(slot_date)::text AS min_d, MAX(slot_date)::text AS max_d FROM people_counting_hourly UNION ALL SELECT 'camera', COUNT(*), NULL, NULL FROM camera;"

if (-not $NoComposeUp) {
    docker compose --env-file $EnvFile up -d --build
    Write-Host ""
    Write-Host "Migration terminée." -ForegroundColor Green
    Write-Host "  Application : http://localhost:${webPort}/"
    Write-Host "  pgAdmin       : http://localhost:${pgAdminPort}/"
} else {
    Write-Host ""
    Write-Host "Migration terminée. Lancez : docker compose up -d --build" -ForegroundColor Green
}

Write-Host ""
Write-Host "Archive recommandée du H2 source :" -ForegroundColor DarkGray
Write-Host "  Copy-Item '$h2Resolved.mv.db' 'backup-h2-$(Get-Date -Format yyyyMMdd).mv.db'"
