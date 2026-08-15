@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0packaging\windows\package.ps1"
exit /b %ERRORLEVEL%
