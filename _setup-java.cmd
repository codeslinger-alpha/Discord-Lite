@echo off
setlocal EnableExtensions

set "RESOLVED_JAVA_HOME="

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    set "RESOLVED_JAVA_HOME=%JAVA_HOME%"
)

if not defined RESOLVED_JAVA_HOME (
    for /f "usebackq delims=" %%I in (`powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $output = cmd /c 'java -XshowSettings:properties -version 2>&1'; $match = $output | Select-String 'java.home' | Select-Object -First 1; if ($match) { ($match.ToString().Split('=', 2)[1]).Trim() }"`) do (
        set "RESOLVED_JAVA_HOME=%%I"
    )
)

if not defined RESOLVED_JAVA_HOME (
    echo Could not find a JDK automatically.
    echo Install JDK 25 and make sure the ^`java^` command works in a new terminal.
    exit /b 1
)

if not exist "%RESOLVED_JAVA_HOME%\bin\javac.exe" (
    echo Detected Java directory is not a JDK: "%RESOLVED_JAVA_HOME%"
    echo Point JAVA_HOME to the JDK root folder and try again.
    exit /b 1
)

endlocal & (
    set "JAVA_HOME=%RESOLVED_JAVA_HOME%"
    set "PATH=%RESOLVED_JAVA_HOME%\bin;%PATH%"
)

exit /b 0
