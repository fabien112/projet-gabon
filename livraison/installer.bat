@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "DEST=%LOCALAPPDATA%\PeopleCounting"
echo Installation vers %DEST%

if not exist payload.zip (
  echo payload.zip introuvable.
  pause
  exit /b 1
)

mkdir "%DEST%" 2>nul
tar -xf payload.zip -C "%DEST%"
if errorlevel 1 (
  echo Echec extraction.
  pause
  exit /b 1
)

if not exist "%DEST%\.env" (
  copy /y "%DEST%\.env.example" "%DEST%\.env" >nul
)

powershell -NoProfile -Command ^
  "$ws = New-Object -ComObject WScript.Shell; $desk = [Environment]::GetFolderPath('Desktop'); $sc = $ws.CreateShortcut((Join-Path $desk 'People Counting.lnk')); $sc.TargetPath = Join-Path $env:LOCALAPPDATA 'PeopleCounting\PeopleCounting.exe'; $sc.WorkingDirectory = Join-Path $env:LOCALAPPDATA 'PeopleCounting'; $sc.Save(); $menu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\People Counting.lnk'; $sc2 = $ws.CreateShortcut($menu); $sc2.TargetPath = $sc.TargetPath; $sc2.WorkingDirectory = $sc.WorkingDirectory; $sc2.Save()"

echo.
echo Installe. Renseignez DSS_HOST / identifiants dans le .env qui va s'ouvrir.
echo Puis double-cliquez le raccourci "People Counting" sur le Bureau.
echo.
notepad "%DEST%\.env"
endlocal
