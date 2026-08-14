@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "ROOT=%~dp0"

echo.
echo === People Counting — front + back ===
echo.

REM Java 21 (chemins usuels, sinon JAVA_HOME / PATH)
if not defined JAVA_HOME (
  if exist "C:\Program Files\Java\jdk-21.0.11\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
  if exist "C:\Program Files\Java\jdk-21.0.12\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12"
  if exist "C:\Program Files\Java\latest\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\latest"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version >nul 2>&1
if errorlevel 1 (
  echo Java 21 introuvable. Installez JDK 21 ou definissez JAVA_HOME.
  pause
  exit /b 1
)

REM .env : dss-integration puis dist-client
if exist "%ROOT%dss-integration\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ROOT%dss-integration\.env") do if not "%%A"=="" set "%%A=%%B"
) else if exist "%ROOT%dist-client\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ROOT%dist-client\.env") do if not "%%A"=="" set "%%A=%%B"
) else (
  echo Copiez dss-integration\.env.example vers dss-integration\.env
  echo puis renseignez DSS_HOST / DSS_USERNAME / DSS_PASSWORD.
  pause
  exit /b 1
)

if not defined SERVER_PORT set "SERVER_PORT=8080"

REM Frontend : build seulement s'il n'est pas deja la
if not exist "%ROOT%dss-frontend\dist\index.html" (
  where npm >nul 2>&1
  if errorlevel 1 (
    echo Frontend non buildé et npm introuvable.
    echo Installez Node.js LTS, ou copiez aussi le dossier dss-frontend\dist.
    pause
    exit /b 1
  )
  echo [1/2] Build frontend...
  cd /d "%ROOT%dss-frontend"
  if not exist "node_modules" call npm install
  if errorlevel 1 (
    pause
    exit /b 1
  )
  call npm run build
  if errorlevel 1 (
    pause
    exit /b 1
  )
  cd /d "%ROOT%"
) else (
  echo [1/2] Frontend deja buildé.
)

set "WEB_ROOT=%ROOT%dss-frontend\dist"
set "SERVER_PORT=%SERVER_PORT%"

echo [2/2] Backend + UI sur http://localhost:%SERVER_PORT%/
echo Fermez cette fenetre pour arreter.
echo.
start "" cmd /c "timeout /t 18 /nobreak >nul && start http://localhost:%SERVER_PORT%/"

cd /d "%ROOT%dss-integration"
call mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8"
if errorlevel 1 pause
endlocal
