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

echo Running WSL and Docker installation commands...
set "IDLEGRID_INSTALL_SCRIPT=%~dp0install-prerequisites.ps1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$p = Start-Process -FilePath 'powershell.exe' -Verb RunAs -Wait -PassThru -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',([char]34 + $env:IDLEGRID_INSTALL_SCRIPT + [char]34)); exit $p.ExitCode"
if errorlevel 1 echo [WARN] Installation commands returned an error or Administrator permission was declined. Continuing to start the Agent.

rem Winget may update the machine PATH while this launcher is already open.
for /f "usebackq delims=" %%P in (`powershell.exe -NoProfile -Command "[Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')"`) do set "PATH=%%P"
if exist "%ProgramFiles%\Docker\Docker\resources\bin\docker.exe" set "PATH=%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"

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
