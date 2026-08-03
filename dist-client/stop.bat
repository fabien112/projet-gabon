@echo off
echo Arret via Ctrl+C dans la fenetre start.bat, ou :
echo taskkill /FI "WINDOWTITLE eq dss-integration*" /F
for /f "tokens=5" %%P in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do taskkill /PID %%P /F
