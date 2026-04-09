@echo off
setlocal EnableExtensions

set "PROJECT_ROOT=%~dp0"

call "%PROJECT_ROOT%_setup-java.cmd"
if errorlevel 1 goto :fail

pushd "%PROJECT_ROOT%" >nul
call ".\mvnw.cmd" -DskipTests exec:java "-Dexec.mainClass=com.discord.discord_lite.server.ServerLauncher"
set "EXIT_CODE=%ERRORLEVEL%"
popd >nul

if "%EXIT_CODE%"=="0" exit /b 0

echo.
echo Discord Lite server exited with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%

:fail
echo.
pause
exit /b 1
