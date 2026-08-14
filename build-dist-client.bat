@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"

echo [1/3] Build frontend...
cd dss-frontend
call npm run build
if errorlevel 1 exit /b 1

echo [2/3] Package JAR...
cd ..\dss-integration
call mvnw.cmd -DskipTests package
if errorlevel 1 exit /b 1

echo [3/3] Mise a jour dist-client...
cd ..
if exist dist-client\web rmdir /s /q dist-client\web
xcopy /e /i /y dss-frontend\dist dist-client\web >nul
copy /y dss-integration\target\dss-integration-0.1.0-SNAPSHOT.jar dist-client\dss-integration.jar >nul

echo OK ? lancez dist-client\start.bat
