@echo off
title IdleGrid Agent
echo.
echo  ==========================================
echo   IdleGrid Agent - Starting...
echo  ==========================================
echo.
echo  Auto-discovering Master server on the network...
echo  (This may take up to 15 seconds on first run)
echo.

REM Try to ensure Docker is in the PATH. Docker Desktop usually installs here:
set "PATH=%PATH%;C:\Program Files\Docker\Docker\resources\bin"

REM Run using the bundled JRE if present, otherwise system Java
if exist "%~dp0runtime\bin\java.exe" (
    set JAVA="%~dp0runtime\bin\java.exe"
) else (
    set JAVA=java
)

%JAVA% -jar "%~dp0agent-1.0-SNAPSHOT-exec.jar"

if %ERRORLEVEL% neq 0 (
    echo.
    echo  Agent stopped with error code %ERRORLEVEL%.
    pause
)
