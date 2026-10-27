@echo off
setlocal
cd /d "%~dp0"

set "JAR_NAME=agent-1.0-SNAPSHOT-exec.jar"
set "STAGE=dist\IdleGridAgent"
set "ZIP=IdleGridAgent-windows.zip"

echo [1/4] Building the standalone agent JAR...
call .\mvnw.cmd -q -DskipTests package
if errorlevel 1 (
    echo [ERROR] Agent build failed.
    exit /b 1
)
if not exist "target\%JAR_NAME%" (
    echo [ERROR] Expected JAR was not produced: target\%JAR_NAME%
    exit /b 1
)

echo [2/4] Preparing portable ZIP contents...
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%"
copy /y "target\%JAR_NAME%" "%STAGE%\%JAR_NAME%" >nul
copy /y "agent.properties" "%STAGE%\agent.properties" >nul
copy /y "run-agent.bat" "%STAGE%\run-agent.bat" >nul
copy /y "setup.ps1" "%STAGE%\setup.ps1" >nul
copy /y "QUICKSTART.txt" "%STAGE%\QUICKSTART.txt" >nul
xcopy "training-worker" "%STAGE%\training-worker\" /E /I /Y >nul
if not exist "%STAGE%\%JAR_NAME%" (
    echo [ERROR] Could not copy the agent JAR.
    exit /b 1
)
if not exist "%STAGE%\agent.properties" (
    echo [ERROR] Could not copy agent.properties.
    exit /b 1
)
if not exist "%STAGE%\run-agent.bat" (
    echo [ERROR] Could not copy run-agent.bat.
    exit /b 1
)
if not exist "%STAGE%\setup.ps1" (
    echo [ERROR] Could not copy setup.ps1.
    exit /b 1
)
if not exist "%STAGE%\QUICKSTART.txt" (
    echo [ERROR] Could not prepare ZIP contents.
    exit /b 1
)

echo [3/4] Creating %ZIP%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%STAGE%\*' -DestinationPath '%ZIP%' -Force"
if errorlevel 1 (
    echo [ERROR] Could not create %ZIP%.
    exit /b 1
)

echo Copying the ZIP into the public landing page...
if not exist "..\public-landing-page\public\downloads" mkdir "..\public-landing-page\public\downloads"
copy /y "%ZIP%" "..\public-landing-page\public\downloads\%ZIP%" >nul
if not exist "..\public-landing-page\public\downloads\%ZIP%" (
    echo [ERROR] ZIP was built but could not be copied to the public landing page.
    exit /b 1
)

echo [4/4] Done.
echo.
echo Created: %~dp0%ZIP%
echo Landing page download updated: ..\public-landing-page\public\downloads\%ZIP%
echo Extract it, edit agent.properties if needed, then double-click run-agent.bat.
endlocal
