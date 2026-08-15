@echo off
setlocal EnableExtensions
cd /d "%~dp0"

curl -s -o nul http://localhost:8080/ 2>nul
if not errorlevel 1 goto open

start "" "DataExpert.exe"

set /a n=0
:wait
timeout /t 2 /nobreak >nul
curl -s -o nul http://localhost:8080/ 2>nul
if not errorlevel 1 goto open
set /a n+=1
if %n% lss 40 goto wait

:open
start http://localhost:8080/
