@echo off
setlocal EnableExtensions
cd /d "%~dp0"

if not exist ".env" (
  echo Copiez .env.example vers .env et renseignez DSS_HOST / user / mdp.
  exit /b 1
)

REM Charge le .env dans l'environnement
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
  if not "%%A"=="" set "%%A=%%B"
)

if not defined SERVER_PORT set "SERVER_PORT=8080"

if not exist "web\index.html" (
  echo Dossier web\ manquant ^(frontend build^).
  exit /b 1
)

if not exist "dss-integration.jar" (
  echo JAR manquant : dss-integration.jar
  exit /b 1
)

echo Demarrage DataExpert sur http://localhost:%SERVER_PORT%/
java -jar dss-integration.jar
