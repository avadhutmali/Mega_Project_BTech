@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "JAR=%~dp0agent-1.0-SNAPSHOT-exec.jar"
set "SETUP=%~dp0setup.ps1"
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

echo Checking IdleGrid prerequisites...
call :CHECK_READY
if "%READY%"=="1" goto START_AGENT

if not exist "%SETUP%" (
    echo [ERROR] setup.ps1 is missing. Extract the complete IdleGrid ZIP.
    pause
    exit /b 1
)

echo.
echo WSL2, Docker Desktop, Java 17+, or the Docker engine needs setup.
echo Windows will ask for Administrator permission to install/configure prerequisites.
echo The Agent itself will run normally after setup completes.
set "IDLEGRID_SETUP=%SETUP%"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$setupPath=[char]34 + $env:IDLEGRID_SETUP + [char]34; $p=Start-Process -FilePath 'powershell.exe' -Verb RunAs -Wait -PassThru -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',$setupPath); exit $p.ExitCode"
set "SETUP_EXIT=%ERRORLEVEL%"
if not "%SETUP_EXIT%"=="0" (
    if "%SETUP_EXIT%"=="3010" (
        echo.
        echo [REBOOT] Restart Windows, then double-click run-agent.bat again.
    ) else (
        echo.
        echo [ERROR] Prerequisite setup did not complete. Exit code: %SETUP_EXIT%
        echo Read the setup output, fix the issue, then run this launcher again.
    )
    pause
    exit /b %SETUP_EXIT%
)

rem Refresh this process PATH after winget installed Java or Docker.
for /f "usebackq delims=" %%P in (`powershell.exe -NoProfile -Command "[Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')"`) do set "PATH=%%P"

call :CHECK_READY
if not "%READY%"=="1" (
    echo.
    echo [ERROR] Setup returned, but Java 17+ and a running Docker engine were not both detected.
    echo Restart Docker Desktop, confirm Java is installed, then run run-agent.bat again.
    pause
    exit /b 1
)

:START_AGENT
echo.
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

:CHECK_READY
set "READY=0"
set "JAVA_VERSION="
set "JAVA_MAJOR="
if "%JAVA_EXE%"=="java" (
    where java >nul 2>nul
    if errorlevel 1 exit /b 0
)
if not exist "%~dp0runtime\bin\java.exe" (
    for /f "tokens=3" %%V in ('java -version 2^>^&1') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
    if not defined JAVA_VERSION exit /b 0
    for /f "tokens=1 delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%M"
    if %JAVA_MAJOR% LSS 17 exit /b 0
)
where docker >nul 2>nul
if errorlevel 1 (
    if not exist "%ProgramFiles%\Docker\Docker\resources\bin\docker.exe" exit /b 0
    set "DOCKER_CLI=%ProgramFiles%\Docker\Docker\resources\bin\docker.exe"
) else (
    set "DOCKER_CLI=docker"
)
set "DOCKER_AUTO_START="
set "IDLEGRID_DOCKER_SETTINGS=%APPDATA%\Docker\settings-store.json"
if not exist "%IDLEGRID_DOCKER_SETTINGS%" set "IDLEGRID_DOCKER_SETTINGS=%APPDATA%\Docker\settings.json"
for /f "delims=" %%A in ('powershell.exe -NoProfile -Command "$s=$null; if (Test-Path $env:IDLEGRID_DOCKER_SETTINGS) { $s=Get-Content $env:IDLEGRID_DOCKER_SETTINGS -Raw ^| ConvertFrom-Json }; if ($s) { $s.autoStart } else { $false }"') do set "DOCKER_AUTO_START=%%A"
if /i not "%DOCKER_AUTO_START%"=="True" exit /b 0
echo Waiting briefly for Docker Desktop to finish starting...
for /l %%I in (1,1,12) do (
    "%DOCKER_CLI%" info >nul 2>nul
    if not errorlevel 1 (
        set "READY=1"
        exit /b 0
    )
    timeout /t 5 /nobreak >nul
)
exit /b 0
