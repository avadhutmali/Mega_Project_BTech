@echo off
setlocal
cd /d "%~dp0"

echo [1/3] Copying setup.ps1 from agent root...
copy /Y "..\setup.ps1" "setup.ps1" >nul

echo [2/3] Generating IExpress directive file...
echo [Version] > setup.sed
echo Class=IEXPRESS >> setup.sed
echo SEDVersion=3 >> setup.sed
echo [Options] >> setup.sed
echo PackagePurpose=InstallApp >> setup.sed
echo ShowInstallProgramWindow=1 >> setup.sed
echo HideExtractAnimation=1 >> setup.sed
echo UseLongFileName=1 >> setup.sed
echo InsideCompressed=0 >> setup.sed
echo CAB_FixedSize=0 >> setup.sed
echo CAB_ResvCodeSigning=0 >> setup.sed
echo RebootMode=I >> setup.sed
echo InstallPrompt=%%InstallPrompt%% >> setup.sed
echo DisplayLicense=%%DisplayLicense%% >> setup.sed
echo FinishMessage=%%FinishMessage%% >> setup.sed
echo TargetName=%%TargetName%% >> setup.sed
echo FriendlyName=%%FriendlyName%% >> setup.sed
echo AppLaunched=%%AppLaunched%% >> setup.sed
echo PostInstallCmd=%%PostInstallCmd%% >> setup.sed
echo AdminQuietInstCmd=%%AdminQuietInstCmd%% >> setup.sed
echo UserQuietInstCmd=%%UserQuietInstCmd%% >> setup.sed
echo SourceFiles=SourceFiles >> setup.sed
echo [Strings] >> setup.sed
echo InstallPrompt= >> setup.sed
echo DisplayLicense= >> setup.sed
echo FinishMessage= >> setup.sed
echo TargetName=%~dp0IdleGrid-Setup.exe >> setup.sed
echo FriendlyName=IdleGrid Agent Setup >> setup.sed
echo AppLaunched=cmd.exe /c Install-Wrapper.bat >> setup.sed
echo PostInstallCmd=^<None^> >> setup.sed
echo AdminQuietInstCmd= >> setup.sed
echo UserQuietInstCmd= >> setup.sed
echo FILE0="Install-Wrapper.bat" >> setup.sed
echo FILE1="setup.ps1" >> setup.sed
echo [SourceFiles] >> setup.sed
echo SourceFiles0=.\ >> setup.sed
echo [SourceFiles0] >> setup.sed
echo %%FILE0%%= >> setup.sed
echo %%FILE1%%= >> setup.sed

echo [3/3] Compiling IdleGrid-Setup.exe using IExpress (built into Windows)...
iexpress /n /q setup.sed

if exist IdleGrid-Setup.exe (
    echo.
    echo ========================================================
    echo SUCCESS! 
    echo Built standalone installer: IdleGrid-Setup.exe
    echo ========================================================
) else (
    echo.
    echo ERROR: Failed to compile EXE.
)

del /q setup.sed
del /q setup.ps1
