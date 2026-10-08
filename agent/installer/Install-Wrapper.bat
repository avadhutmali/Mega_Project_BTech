@echo off
:: Self-elevating batch script that runs setup.ps1
:: This file will be bundled inside the EXE.

setlocal
:: Check for Admin rights
net session >nul 2>&1
if %errorLevel% == 0 (
    echo Administrator rights detected. Proceeding...
) else (
    echo Requesting Administrator privileges...
    powershell -Command "Start-Process '%~f0' -Verb RunAs -Wait"
    exit /b
)

echo Starting IdleGrid Setup...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1"
echo.
pause
