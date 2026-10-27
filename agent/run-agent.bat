@echo off
setlocal
cd /d "%~dp0"

set "JAR=%~dp0agent-1.0-SNAPSHOT-exec.jar"
set "JAVA_EXE=java"
if exist "%~dp0runtime\bin\java.exe" set "JAVA_EXE=%~dp0runtime\bin\java.exe"

if not exist "%JAR%" (
    echo [ERROR] Agent JAR was not found beside this launcher.
    echo Extract the complete IdleGrid ZIP, then run run-agent.bat again.
    pause
    exit /b 1
)

if not exist "%~dp0agent.properties" (
    echo [ERROR] agent.properties was not found beside this launcher.
    pause
    exit /b 1
)

where "%JAVA_EXE%" >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java 17 or newer was not found.
    echo Install a Java 17+ JRE/JDK, then run this file again.
    echo The installer script setup.ps1 can also help prepare this PC.
    pause
    exit /b 1
)

echo Starting IdleGrid Agent...
echo Master setting: see agent.properties
echo Keep this window open while the agent is running.
echo.
"%JAVA_EXE%" -jar "%JAR%" %*
set "EXIT_CODE=%ERRORLEVEL%"
echo.
echo IdleGrid Agent stopped with exit code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
