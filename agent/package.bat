@echo off
REM ============================================================
REM  IdleGrid Agent — Native EXE packager (Windows)
REM
REM  Uses jpackage (bundled with JDK 14+) to produce a
REM  self-contained app image:  dist\IdleGridAgent\IdleGridAgent.exe
REM  The EXE bundles its own JRE — no Java installation needed
REM  on any target lab PC.
REM
REM  Usage:
REM    package.bat
REM
REM  Output:
REM    dist\IdleGridAgent\IdleGridAgent.exe   ← run this on lab PCs
REM    dist\IdleGridAgent-windows.zip         ← distribute this zip
REM ============================================================
setlocal

set MVN="C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1\plugins\maven\lib\maven3\bin\mvn.cmd"
set JPACKAGE="C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot\bin\jpackage.exe"
set JAR=agent-1.0-SNAPSHOT-exec.jar
set DIST=dist

echo.
echo [1/4] Building fat JAR...
call %MVN% package -q
if errorlevel 1 (
    echo [ERROR] Maven build failed.
    exit /b 1
)
echo       OK — target\%JAR%

echo.
echo [2/4] Cleaning previous dist...
if exist "%DIST%" rmdir /s /q "%DIST%"
mkdir "%DIST%"

echo.
echo [3/4] Packaging with jpackage (bundling JRE — this takes ~30s)...
%JPACKAGE% ^
  --type app-image ^
  --name IdleGridAgent ^
  --input target ^
  --main-jar %JAR% ^
  --main-class com.idlegrid.agent.AgentMain ^
  --app-version 1.0.0 ^
  --win-console ^
  --dest %DIST%

if errorlevel 1 (
    echo [ERROR] jpackage failed.
    exit /b 1
)
echo       OK — %DIST%\IdleGridAgent\IdleGridAgent.exe

echo.
echo [4/4] Creating distributable ZIP...
powershell -NoProfile -Command "Compress-Archive -Path 'dist\IdleGridAgent' -DestinationPath 'dist\IdleGridAgent-windows.zip' -Force"
if errorlevel 1 (
    echo [WARN] Could not create ZIP (PowerShell Compress-Archive failed).
) else (
    echo       OK — %DIST%\IdleGridAgent-windows.zip
)

echo.
echo ============================================================
echo  DONE.
echo.
echo  To run locally:
echo    dist\IdleGridAgent\IdleGridAgent.exe
echo.
echo  To deploy to a lab PC:
echo    1. Copy dist\IdleGridAgent-windows.zip to the PC
echo    2. Extract it anywhere (e.g. C:\IdleGridAgent\)
echo    3. Edit agent.properties inside the extracted folder
echo    4. Run IdleGridAgent.exe
echo.
echo  Optionally set master.url by placing agent.properties next
echo  to the EXE before running.
echo ============================================================
endlocal
