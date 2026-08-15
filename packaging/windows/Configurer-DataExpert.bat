@echo off
setlocal EnableExtensions
cd /d "%~dp0"

if not exist ".env" (
  if exist ".env.example" (
    copy /y ".env.example" ".env" >nul
    echo Fichier .env cree a partir de .env.example.
  ) else (
    echo Fichier .env.example introuvable.
    pause
    exit /b 1
  )
)

echo.
echo Renseignez DSS_HOST, DSS_USERNAME et DSS_PASSWORD, enregistrez, puis lancez DataExpert.
echo.
notepad ".env"
