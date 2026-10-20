@echo off
REM Deploiement Docker DataExpert (production)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\docker-deploy.ps1" -Prod %*
pause
