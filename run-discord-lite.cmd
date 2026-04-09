@echo off
setlocal EnableExtensions

set "PROJECT_ROOT=%~dp0"

call "%PROJECT_ROOT%_setup-java.cmd"
if errorlevel 1 goto :fail

start "Discord Lite Server" "%PROJECT_ROOT%run-server.cmd"
timeout /t 3 /nobreak >nul
start "Discord Lite Client" "%PROJECT_ROOT%run-client.cmd"
exit /b 0

:fail
echo.
pause
exit /b 1
