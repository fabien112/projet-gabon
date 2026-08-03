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

if not exist "web\index.html" (
  echo Dossier web\ manquant ^(frontend build^).
  exit /b 1
)

echo Demarrage People Counting sur http://localhost:%SERVER_PORT%/
java -jar dss-integration.jar
