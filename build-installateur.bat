@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "ROOT=%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12"
if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\latest"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [1/4] Frontend...
cd /d "%ROOT%dss-frontend"
call npm run build
if errorlevel 1 exit /b 1

echo [2/4] JAR (front inclus)...
cd /d "%ROOT%dss-integration"
call mvnw.cmd -DskipTests package
if errorlevel 1 exit /b 1

echo [3/4] Image Windows (JRE inclus)...
cd /d "%ROOT%"
if exist installer\image rmdir /s /q installer\image
if exist installer\out rmdir /s /q installer\out
mkdir installer\image
mkdir installer\out
mkdir installer\jpackage-in
copy /y dss-integration\target\dss-integration-0.1.0-SNAPSHOT.jar installer\jpackage-in\dss-integration.jar >nul

jpackage --type app-image --name PeopleCounting --app-version 0.1.0 --vendor DAHUA --dest installer\image --input installer\jpackage-in --main-jar dss-integration.jar --java-options -Dfile.encoding=UTF-8 --win-console
if errorlevel 1 exit /b 1

copy /y dss-integration\.env.example installer\image\PeopleCounting\.env.example >nul

echo [4/4] Setup.exe...
if exist installer\payload.zip del /q installer\payload.zip
pushd installer\image\PeopleCounting
tar -a -cf ..\..\payload.zip *
popd
copy /y installer\install.bat installer\out\install.bat >nul
copy /y installer\payload.zip installer\out\payload.zip >nul

set "SED=%ROOT%installer\out\setup.sed"
set "SETUP=%ROOT%installer\out\PeopleCounting-Setup.exe"
> "%SED%" (
  echo [Version]
  echo Class=IEXPRESS
  echo SEDVersion=3
  echo [Options]
  echo PackagePurpose=InstallApp
  echo ShowInstallProgramWindow=1
  echo HideExtractAnimation=0
  echo UseLongFileName=1
  echo InsideCompressed=1
  echo CAB_FixedSize=0
  echo CAB_ResvCodeSigning=0
  echo RebootMode=N
  echo TargetName=%SETUP%
  echo FriendlyName=People Counting
  echo AppLaunched=cmd /c install.bat
  echo PostInstallCmd=^<None^>
  echo SourceFiles=SourceFiles
  echo [Strings]
  echo FILE0=install.bat
  echo FILE1=payload.zip
  echo [SourceFiles]
  echo SourceFiles0=%ROOT%installer\out\
  echo [SourceFiles0]
  echo %%FILE0%%=
  echo %%FILE1%%=
)

iexpress /N /Q "%SED%"
if errorlevel 1 (
  echo IExpress a echoue. Fichiers prets : installer\out\payload.zip + install.bat
  exit /b 1
)

echo.
echo OK — installeur : installer\out\PeopleCounting-Setup.exe
echo Copiez UNIQUEMENT ce fichier sur la machine DSS, puis executez-le.
endlocal
