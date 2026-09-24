; =============================================================================
; IdleGrid Agent — NSIS Installer Script
; =============================================================================
; Produces a single IdleGridAgent-Setup.exe that guides the user through a
; standard "Next → Next → Install" Windows setup wizard.
;
; Build on Linux:
;   makensis installer.nsi
;
; Build on Windows:
;   makensis.exe installer.nsi
;
; Requires NSIS 3.x and the following files in the same directory:
;   agent-1.0-SNAPSHOT-exec.jar
;   agent.properties
;   setup.ps1
; =============================================================================

; ── Installer metadata ────────────────────────────────────────────────────────
!define APP_NAME        "IdleGrid Agent"
!define APP_VERSION     "1.0"
!define APP_PUBLISHER   "IdleGrid Project"
!define APP_URL         "https://github.com/avadhutmali/Mega_Project_BTech"
!define APP_EXE_NAME    "IdleGridAgent-Setup.exe"
!define INSTALL_DIR     "$PROGRAMFILES64\IdleGridAgent"
!define REG_KEY         "Software\Microsoft\Windows\CurrentVersion\Uninstall\IdleGridAgent"

; ── Compiler flags ────────────────────────────────────────────────────────────
Unicode True
SetCompressor /SOLID lzma        ; best compression — smaller EXE
RequestExecutionLevel admin       ; require UAC elevation (needed for WSL2 setup)

; ── Include modern UI ─────────────────────────────────────────────────────────
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "WinMessages.nsh"

; ── MUI Settings ──────────────────────────────────────────────────────────────
!define MUI_ABORTWARNING
!define MUI_ICON           "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON         "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"

; Welcome page
!define MUI_WELCOMEPAGE_TITLE    "Welcome to IdleGrid Agent Setup"
!define MUI_WELCOMEPAGE_TEXT     "This wizard will install the IdleGrid Agent on your lab PC.$\r$\n$\r$\nThe agent runs silently in the background, reports this PC's available compute capacity to the IdleGrid Master, and executes assigned jobs inside resource-capped Docker containers — only when you are away from the keyboard.$\r$\n$\r$\nClick Next to continue."
!define MUI_FINISHPAGE_TITLE     "IdleGrid Agent Setup Complete"
!define MUI_FINISHPAGE_TEXT      "The IdleGrid Agent has been installed.$\r$\n$\r$\nBefore running the agent for the first time:$\r$\n$\r$\n  1. Run the Environment Setup (WSL2 + Docker) if prompted below$\r$\n  2. Edit agent.properties to set your Master server IP$\r$\n  3. Start the agent using the Start Menu shortcut$\r$\n$\r$\nClick Finish to exit the installer."

; Finish page options
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_TEXT    "Run Environment Setup now (WSL2 + Docker — recommended for first install)"
!define MUI_FINISHPAGE_RUN_FUNCTION RunEnvSetup
!define MUI_FINISHPAGE_SHOWREADME
!define MUI_FINISHPAGE_SHOWREADME_TEXT   "Open installation folder"
!define MUI_FINISHPAGE_SHOWREADME_FUNCTION OpenInstallDir

; ── Finish page callbacks — must be defined BEFORE !insertmacro MUI_PAGE_FINISH
Function RunEnvSetup
    ; Launch setup.ps1 as Administrator (UAC already elevated for the installer)
    ExecShell "runas" "powershell.exe" \
        '-ExecutionPolicy Bypass -NoExit -File "$INSTDIR\setup.ps1"' SW_SHOW
FunctionEnd

Function OpenInstallDir
    ExecShell "open" "$INSTDIR"
FunctionEnd

; ── Pages ─────────────────────────────────────────────────────────────────────
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE      "LICENSE.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_WELCOME
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

; ── Language ──────────────────────────────────────────────────────────────────
!insertmacro MUI_LANGUAGE "English"

; ── Installer Info ────────────────────────────────────────────────────────────
Name              "${APP_NAME} ${APP_VERSION}"
OutFile           "IdleGridAgent-Setup.exe"
InstallDir        "${INSTALL_DIR}"
InstallDirRegKey  HKLM "${REG_KEY}" "InstallLocation"
ShowInstDetails   show
ShowUninstDetails show

; ── Version Info (shows in EXE properties on Windows) ─────────────────────────
VIProductVersion  "1.0.0.0"
VIAddVersionKey   "ProductName"      "${APP_NAME}"
VIAddVersionKey   "ProductVersion"   "${APP_VERSION}"
VIAddVersionKey   "CompanyName"      "${APP_PUBLISHER}"
VIAddVersionKey   "FileDescription"  "${APP_NAME} Installer"
VIAddVersionKey   "FileVersion"      "${APP_VERSION}"
VIAddVersionKey   "LegalCopyright"   "© 2026 ${APP_PUBLISHER}"

; =============================================================================
; Components
; =============================================================================

Section "Core Agent Files" SecCore
    ; Required — cannot be unchecked
    SectionIn RO

    SetOutPath "$INSTDIR"

    ; ── Main files ──────────────────────────────────────────────────────
    File "agent-1.0-SNAPSHOT-exec.jar"
    File "agent.properties"
    File "setup.ps1"

    ; ── Launcher ────────────────────────────────────────────────────────
    ; Write a clean run.bat that references the JAR in the same folder
    FileOpen  $0 "$INSTDIR\run.bat" w
    FileWrite $0 "@echo off$\r$\n"
    FileWrite $0 "REM IdleGrid Agent launcher$\r$\n"
    FileWrite $0 "REM Edit agent.properties in this folder to configure master.url$\r$\n"
    FileWrite $0 "cd /d $\"%~dp0$\"$\r$\n"
    FileWrite $0 "java -jar agent-1.0-SNAPSHOT-exec.jar %*$\r$\n"
    FileClose $0

    ; ── Uninstaller ─────────────────────────────────────────────────────
    WriteUninstaller "$INSTDIR\Uninstall.exe"

    ; ── Registry: Add/Remove Programs ────────────────────────────────────
    WriteRegStr   HKLM "${REG_KEY}" "DisplayName"     "${APP_NAME}"
    WriteRegStr   HKLM "${REG_KEY}" "DisplayVersion"  "${APP_VERSION}"
    WriteRegStr   HKLM "${REG_KEY}" "Publisher"       "${APP_PUBLISHER}"
    WriteRegStr   HKLM "${REG_KEY}" "URLInfoAbout"    "${APP_URL}"
    WriteRegStr   HKLM "${REG_KEY}" "InstallLocation" "$INSTDIR"
    WriteRegStr   HKLM "${REG_KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
    WriteRegDWORD HKLM "${REG_KEY}" "NoModify"        1
    WriteRegDWORD HKLM "${REG_KEY}" "NoRepair"        1

SectionEnd

Section "Start Menu Shortcuts" SecShortcuts

    CreateDirectory "$SMPROGRAMS\IdleGrid Agent"

    ; Launch agent shortcut — runs run.bat in the install dir
    CreateShortcut "$SMPROGRAMS\IdleGrid Agent\Start Agent.lnk" \
        "$WINDIR\System32\cmd.exe" \
        '/k "cd /d \"$INSTDIR\" && run.bat"' \
        "$INSTDIR\run.bat" 0

    ; Open config shortcut
    CreateShortcut "$SMPROGRAMS\IdleGrid Agent\Edit Config (agent.properties).lnk" \
        "notepad.exe" '"$INSTDIR\agent.properties"'

    ; Run setup shortcut
    CreateShortcut "$SMPROGRAMS\IdleGrid Agent\Run Environment Setup (Admin).lnk" \
        "powershell.exe" \
        '-ExecutionPolicy Bypass -File "$INSTDIR\setup.ps1"'

    ; Open logs shortcut
    CreateShortcut "$SMPROGRAMS\IdleGrid Agent\View Logs.lnk" \
        "$INSTDIR\logs"

    ; Uninstall shortcut
    CreateShortcut "$SMPROGRAMS\IdleGrid Agent\Uninstall.lnk" \
        "$INSTDIR\Uninstall.exe"

SectionEnd

; ── Component descriptions ────────────────────────────────────────────────────
LangString DESC_SecCore       ${LANG_ENGLISH} "The agent JAR, launcher, configuration file, and environment setup script. Required."
LangString DESC_SecShortcuts  ${LANG_ENGLISH} "Start Menu shortcuts for launching the agent, editing config, and viewing logs."

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
    !insertmacro MUI_DESCRIPTION_TEXT ${SecCore}      $(DESC_SecCore)
    !insertmacro MUI_DESCRIPTION_TEXT ${SecShortcuts} $(DESC_SecShortcuts)
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; =============================================================================
; Uninstaller
; =============================================================================

Section "Uninstall"

    ; Remove installed files
    Delete "$INSTDIR\agent-1.0-SNAPSHOT-exec.jar"
    Delete "$INSTDIR\agent.properties"
    Delete "$INSTDIR\setup.ps1"
    Delete "$INSTDIR\run.bat"
    Delete "$INSTDIR\Uninstall.exe"

    ; Remove logs and workspace (optional — ask user)
    MessageBox MB_YESNO "Remove logs and workspace data from $INSTDIR?" IDNO SkipData
        RMDir /r "$INSTDIR\logs"
        RMDir /r "$INSTDIR\workspace"
    SkipData:

    RMDir "$INSTDIR"

    ; Remove Start Menu shortcuts
    RMDir /r "$SMPROGRAMS\IdleGrid Agent"

    ; Remove registry entries
    DeleteRegKey HKLM "${REG_KEY}"

SectionEnd
