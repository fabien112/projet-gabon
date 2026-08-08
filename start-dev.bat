@echo off
setlocal EnableExtensions
cd /d "%~dp0"

REM Dev mode : backend Spring (hot) + frontend Vite — PAS de jar.
REM Donnees = dist-client\data (base reelle)

set "ROOT=%~dp0"
set "DB_FILE=%ROOT%dist-client\data\dss"
set "DB_URL=jdbc:h2:file:%DB_FILE:\=/%;MODE=MySQL;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE"

if exist "%ROOT%dist-client\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ROOT%dist-client\.env") do (
    if not "%%A"=="" set "%%A=%%B"
  )
)

echo.
echo === People Counting DEV ===
echo Backend : http://localhost:8080
echo Frontend: http://localhost:5173
echo DB      : %DB_URL%
echo.

REM Liberer locks H2 eventuels
del /q "%ROOT%dist-client\data\dss.lock.db" 2>nul
del /q "%ROOT%dss-integration\data\dss.lock.db" 2>nul

start "dss-backend" cmd /k "cd /d "%ROOT%dss-integration" && set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12&& set DB_URL=%DB_URL%&& set SERVER_PORT=8080&& .\mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8""
timeout /t 8 /nobreak >nul
start "dss-frontend" cmd /k "cd /d "%ROOT%dss-frontend" && npm run dev"

echo Lance navigateur sur http://localhost:5173
timeout /t 5 /nobreak >nul
start http://localhost:5173/
endlocal
