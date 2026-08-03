@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script (Windows)
@REM ----------------------------------------------------------------------------

@echo off
setlocal

if "%JAVA_HOME%"=="" (
  echo Error: JAVA_HOME is not set. >&2
  exit /b 1
)

set "MAVEN_PROJECTBASEDIR=%~dp0"
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"

if not exist "%WRAPPER_JAR%" (
  echo Error: %WRAPPER_JAR% not found. >&2
  exit /b 1
)

"%JAVA_HOME%\bin\java.exe" ^
  -classpath "%WRAPPER_JAR%" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  %WRAPPER_LAUNCHER% %*

exit /b %ERRORLEVEL%
