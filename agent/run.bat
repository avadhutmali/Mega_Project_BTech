@echo off
REM ============================================================
REM  IdleGrid Agent — Windows launcher
REM  Run from the agent/ directory after building with:
REM    .\mvnw.cmd package -q
REM ============================================================

set JAR=target\agent-1.0-SNAPSHOT-exec.jar

if not exist "%JAR%" (
    echo [ERROR] JAR not found: %JAR%
    echo Run:  .\mvnw.cmd package -q
    exit /b 1
)

echo Starting IdleGrid Agent...
echo JAR:     %JAR%
echo Config:  %CD%\agent.properties  (if present, overrides defaults)
echo Logs:    %CD%\logs\
echo.

java -jar "%JAR%" %*
