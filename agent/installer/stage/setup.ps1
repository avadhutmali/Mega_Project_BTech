# =============================================================================
# IdleGrid Agent - One-Time Setup Script for Lab PCs
# =============================================================================
# Run this script ONCE on each lab PC (as Administrator) before deploying the
# agent. It will:
#   1. Install/enable WSL2 without an unnecessary Linux distribution
#   2. Install Docker Desktop with winget (official installer fallback)
#   3. Configure Docker Desktop (WSL2 backend, sensible resource limits)
#   4. Install Java 17+ with winget when it is missing
#   5. Verify everything is working
#
# Usage (in an Administrator PowerShell):
#   Set-ExecutionPolicy Bypass -Scope Process -Force
#   .\setup.ps1
#
# A REBOOT may be required after WSL2 is installed (Step 1).
# Re-run the script after rebooting - it is fully idempotent.
# =============================================================================

$ErrorActionPreference = "Stop"
$ProgressPreference    = "SilentlyContinue"   # faster Invoke-WebRequest

# ─── Colour helpers ───────────────────────────────────────────────────────────
function Write-Step  { param($n,$msg) Write-Host "`n[$n] $msg" -ForegroundColor Cyan }
function Write-Ok    { param($msg)    Write-Host "    OK  $msg" -ForegroundColor Green }
function Write-Warn  { param($msg)    Write-Host "    WARN $msg" -ForegroundColor Yellow }
function Write-Fail  { param($msg)    Write-Host "    FAIL $msg" -ForegroundColor Red; exit 1 }
function Write-Info  { param($msg)    Write-Host "    ... $msg" -ForegroundColor Gray }

# ─── Banner ───────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  ╔══════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "  ║   IdleGrid Agent - Lab PC Setup  v1.0        ║" -ForegroundColor Cyan
Write-Host "  ╚══════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# =============================================================================
# Step 0 - Must be running as Administrator
# =============================================================================
Write-Step 0 "Checking Administrator privileges"
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Fail "This script must be run as Administrator. Right-click PowerShell → 'Run as administrator'."
}
Write-Ok "Running as Administrator"

# =============================================================================
# Step 1 - Check Windows version (WSL2 needs build 19041+)
# =============================================================================
Write-Step 1 "Checking Windows version"
$build = [System.Environment]::OSVersion.Version.Build
if ($build -lt 19041) {
    Write-Fail "Windows build $build detected. WSL2 requires Windows 10 build 19041 (version 2004) or later."
}
Write-Ok "Windows build $build - OK"

# =============================================================================
# Step 2 - Enable WSL + Virtual Machine Platform features
# =============================================================================
Write-Step 2 "Enabling WSL2 Windows features"

$wslState = (Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux).State
$vmState  = (Get-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform).State

$rebootNeeded = $false

if ($wslState -ne "Enabled") {
    Write-Info "Enabling Microsoft-Windows-Subsystem-Linux..."
    $r = Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux -NoRestart
    if ($r.RestartNeeded) { $rebootNeeded = $true }
    Write-Ok "WSL feature enabled"
} else {
    Write-Ok "WSL feature already enabled"
}

if ($vmState -ne "Enabled") {
    Write-Info "Enabling VirtualMachinePlatform (required for WSL2)..."
    $r = Enable-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform -NoRestart
    if ($r.RestartNeeded) { $rebootNeeded = $true }
    Write-Ok "VirtualMachinePlatform enabled"
} else {
    Write-Ok "VirtualMachinePlatform already enabled"
}

# Install/update WSL without installing Ubuntu. Docker Desktop uses the WSL2
# engine directly, so a separate Linux distribution is not needed for IdleGrid.
try {
    & wsl.exe --status *> $null
    $wslStatusExitCode = $LASTEXITCODE
    if ($wslStatusExitCode -eq 0 -and $wslState -eq "Enabled" -and $vmState -eq "Enabled") {
        Write-Ok "WSL is already installed; skipping installation"
    } else {
        $wslInstallOutput = (& wsl.exe --install --no-distribution 2>&1 | Out-String)
        $wslInstallExitCode = $LASTEXITCODE
        if ($wslInstallExitCode -eq 0) {
            Write-Ok "WSL installed/updated without a Linux distribution"
            if ($wslInstallOutput -match "(?i)restart|reboot") { $rebootNeeded = $true }
        } else {
            Write-Warn "wsl --install --no-distribution returned $wslInstallExitCode; Windows features were checked/enabled above."
            if ($wslInstallOutput.Trim()) { Write-Info $wslInstallOutput.Trim() }
        }
    }
} catch {
    Write-Warn "Could not run wsl --install --no-distribution; Windows feature setup above remains in place. $_"
}

# Set WSL default version to 2
try {
    wsl --set-default-version 2 2>&1 | Out-Null
    Write-Ok "WSL default version set to 2"
} catch {
    Write-Warn "Could not set WSL default version - will be set automatically after reboot"
}

if ($rebootNeeded) {
    Write-Host ""
    Write-Host "  ┌─────────────────────────────────────────────────────┐" -ForegroundColor Yellow
    Write-Host "  │  REBOOT REQUIRED                                     │" -ForegroundColor Yellow
    Write-Host "  │  WSL2 features were just enabled.                    │" -ForegroundColor Yellow
    Write-Host "  │  Please reboot and re-run this script to continue.   │" -ForegroundColor Yellow
    Write-Host "  └─────────────────────────────────────────────────────┘" -ForegroundColor Yellow
    Write-Host ""
    $choice = Read-Host "Reboot now? (Y/N)"
    if ($choice -match "^[Yy]") {
        Restart-Computer -Force
    }
    # MSI-standard reboot-required exit code; run-agent.bat uses it to avoid
    # launching the Agent before WSL has finished initializing after reboot.
    exit 3010
}

# =============================================================================
# Step 3 - Install Docker Desktop with winget (official installer fallback)
# =============================================================================
Write-Step 3 "Checking Docker Desktop"

$dockerExe = "$Env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
$dockerInstalled = Test-Path $dockerExe

if ($dockerInstalled) {
    Write-Ok "Docker Desktop is already installed - skipping download"
} else {
    $wingetCommand = Get-Command winget -ErrorAction SilentlyContinue
    if ($wingetCommand) {
        Write-Info "Installing Docker Desktop with winget (silent install)..."
        try {
            & $wingetCommand.Source install -e --id Docker.DockerDesktop `
                --accept-package-agreements --accept-source-agreements --silent
            $wingetExitCode = $LASTEXITCODE
            if ($wingetExitCode -eq 0 -or (Test-Path $dockerExe)) {
                $dockerInstalled = Test-Path $dockerExe
                if ($dockerInstalled) { Write-Ok "Docker Desktop installed with winget" }
            } else {
                Write-Warn "winget returned exit code $wingetExitCode; trying the official installer fallback."
            }
        } catch {
            Write-Warn "winget installation failed; trying the official installer fallback. $_"
        }
    } else {
        Write-Warn "winget was not found; using the official Docker Desktop installer fallback."
    }

    if (-not (Test-Path $dockerExe)) {
        Write-Info "Downloading Docker Desktop installer from Docker..."
        $dockerInstallerUrl = "https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe"
        $installerPath = "$Env:TEMP\DockerDesktopInstaller.exe"

        try {
            Invoke-WebRequest -Uri $dockerInstallerUrl -OutFile $installerPath -UseBasicParsing
            Write-Ok "Download complete: $installerPath"
        } catch {
            Write-Fail "Failed to download Docker Desktop: $_`nInstall winget or download manually from https://www.docker.com/products/docker-desktop/"
        }

        Write-Info "Installing Docker Desktop silently (this takes a few minutes)..."
        $proc = Start-Process -FilePath $installerPath `
                              -ArgumentList "install --quiet --accept-license --backend=wsl-2" `
                              -Wait -PassThru
        if ($proc.ExitCode -ne 0) {
            Write-Fail "Docker Desktop installer exited with code $($proc.ExitCode)"
        }
        if (-not (Test-Path $dockerExe)) {
            Write-Fail "Docker Desktop installer completed, but Docker Desktop.exe was not found at $dockerExe"
        }
        Write-Ok "Docker Desktop installed successfully"
    }

    # Refresh PATH so docker is available in this session
    $Env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("PATH","User")
}

# =============================================================================
# Step 4 - Start Docker Desktop and wait for it to be ready
# =============================================================================
Write-Step 4 "Starting Docker Desktop and waiting for it to be ready"

# Launch Docker Desktop if not already running
$dockerProcess = Get-Process "Docker Desktop" -ErrorAction SilentlyContinue
if (-not $dockerProcess) {
    Write-Info "Starting Docker Desktop..."
    Start-Process $dockerExe -ErrorAction SilentlyContinue
}

Write-Info "Waiting for Docker daemon (up to 120 seconds)..."
$ready = $false
for ($i = 0; $i -lt 24; $i++) {
    Start-Sleep -Seconds 5
    try {
        $out = docker ps 2>&1
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    } catch {}
    Write-Info "  Still waiting... ($([int](($i+1)*5))s)"
}

if (-not $ready) {
    Write-Warn "Docker daemon did not respond within 120s."
    Write-Warn "Docker Desktop may need a moment to finish starting."
    Write-Warn "Try running: docker ps"
    Write-Warn "If it works, continue with deploying the agent."
} else {
    Write-Ok "Docker daemon is running"
}

# =============================================================================
# Step 5 - Configure Docker Desktop for IdleGrid
# =============================================================================
Write-Step 5 "Configuring Docker Desktop for IdleGrid"

$settingsDir  = "$Env:APPDATA\Docker"
$settingsFile = "$settingsDir\settings-store.json"

# Detect total RAM in MB and use at most 75% for Docker (leave headroom for the user)
$totalRamMb = [math]::Round((Get-CimInstance Win32_PhysicalMemory | Measure-Object Capacity -Sum).Sum / 1MB)
$dockerRamMb = [math]::Max(2048, [math]::Round($totalRamMb * 0.75 / 1024) * 1024)  # round to GB, min 2 GB
$cpuCount    = (Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors

Write-Info "System: $totalRamMb MB RAM, $cpuCount logical CPUs"
Write-Info "Docker resource allocation: $dockerRamMb MB RAM, $cpuCount CPUs"

if (-not (Test-Path $settingsDir)) {
    New-Item -ItemType Directory -Path $settingsDir -Force | Out-Null
}

# Read existing settings if present (preserve user settings we don't touch)
$settings = @{}
if (Test-Path $settingsFile) {
    try {
        # ConvertFrom-Json -AsHashtable is unavailable in Windows PowerShell 5.1.
        # Copy properties explicitly so rerunning setup preserves existing settings.
        $existingSettings = Get-Content $settingsFile -Raw | ConvertFrom-Json
        foreach ($property in $existingSettings.PSObject.Properties) {
            $settings[$property.Name] = $property.Value
        }
        Write-Info "Existing Docker settings found - merging IdleGrid config"
    } catch {
        Write-Warn "Could not parse existing settings - writing fresh config"
        $settings = @{}
    }
}

# Apply IdleGrid-required settings
$settings["wslEngineEnabled"]              = $true
$settings["useVirtualizationFramework"]    = $false   # WSL2 backend, not HyperV
$settings["memoryMiB"]                     = $dockerRamMb
$settings["cpus"]                          = $cpuCount
$settings["openUIOnStartupDisabled"]       = $true    # Don't pop the Docker UI on each boot
$settings["analyticsEnabled"]             = $false

$settings | ConvertTo-Json -Depth 10 | Set-Content $settingsFile -Encoding UTF8
Write-Ok "Docker settings written to: $settingsFile"
Write-Ok "  WSL2 backend: enabled"
Write-Ok "  Memory: $dockerRamMb MB"
Write-Ok "  CPUs: $cpuCount"
# Restart Docker Desktop to apply new settings
Write-Info "Restarting Docker Desktop to apply settings..."
try {
    Stop-Process -Name "Docker Desktop" -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    Start-Process $dockerExe -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 8
    Write-Ok "Docker Desktop restarted"
} catch {
    Write-Warn "Could not restart Docker Desktop automatically - please restart it manually"
}

# =============================================================================
# Step 6 - Check Java 17+
# =============================================================================
Write-Step 6 "Checking Java 17+"

$javaOk = $false
try {
    $javaVer = java -version 2>&1 | Select-Object -First 1
    Write-Info "Detected: $javaVer"
    # Parse major version (e.g. "17", "21", etc.)
    if ($javaVer -match '"(\d+)') {
        $major = [int]$Matches[1]
        if ($major -ge 17) {
            Write-Ok "Java $major - OK (17+ required)"
            $javaOk = $true
        } else {
            Write-Warn "Java $major found but IdleGrid requires Java 17 or later"
        }
    }
} catch {
    Write-Warn "Java not found on PATH"
}

if (-not $javaOk) {
    $wingetCommand = Get-Command winget -ErrorAction SilentlyContinue
    if ($wingetCommand) {
        Write-Info "Installing Java 17 with winget..."
        try {
            & $wingetCommand.Source install -e --id Microsoft.OpenJDK.17 `
                --accept-package-agreements --accept-source-agreements --silent
            if ($LASTEXITCODE -eq 0) {
                $Env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" +
                            [System.Environment]::GetEnvironmentVariable("PATH","User")
                $javaVer = java -version 2>&1 | Select-Object -First 1
                if ($javaVer -match '"(\d+)') {
                    $major = [int]$Matches[1]
                    if ($major -ge 17) {
                        Write-Ok "Installed and verified Java $major"
                        $javaOk = $true
                    }
                }
            }
        } catch {
            Write-Warn "Automatic Java installation failed: $_"
        }
    }
    if (-not $javaOk) {
        Write-Host ""
        Write-Host "  Java 17+ is required to run the IdleGrid agent." -ForegroundColor Yellow
        Write-Host "  Install with: winget install -e --id Microsoft.OpenJDK.17" -ForegroundColor White
        Write-Host "  Or download from: https://adoptium.net/temurin/releases/?version=17" -ForegroundColor White
        Write-Host "  Then run run-agent.bat again." -ForegroundColor Yellow
    }
}

# =============================================================================
# Step 7 - Final verification
# =============================================================================
Write-Step 7 "Final system verification"

$allOk = $true

# Docker
try {
    $out = docker info 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "docker info - Docker daemon responding"
    } else {
        Write-Warn "docker info returned non-zero. Docker may still be starting."
        $allOk = $false
    }
} catch {
    Write-Warn "docker not found on PATH. Try opening a new terminal after Docker Desktop finishes starting."
    $allOk = $false
}

# Pull a tiny test image
try {
    Write-Info "Pulling alpine:latest (smoke test - ~3 MB)..."
    docker pull alpine:latest 2>&1 | Out-Null
    $result = docker run --rm alpine:latest echo "IdleGrid OK" 2>&1
    if ($result -match "IdleGrid OK") {
        Write-Ok "Container run smoke test passed: '$result'"
    } else {
        Write-Warn "Container smoke test output unexpected: $result"
        $allOk = $false
    }
} catch {
    Write-Warn "Container smoke test failed: $_"
    $allOk = $false
}

# Java
if (-not $javaOk) { $allOk = $false }

# =============================================================================
# Step 8 - Summary
# =============================================================================
Write-Host ""
if ($allOk) {
    Write-Host "  ╔═══════════════════════════════════════════════════╗" -ForegroundColor Green
    Write-Host "  ║  Setup complete!  This PC is ready for IdleGrid.  ║" -ForegroundColor Green
    Write-Host "  ╚═══════════════════════════════════════════════════╝" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Next steps:" -ForegroundColor Cyan
    Write-Host "    1. Edit agent.properties  →  set master.url=http://<master-ip>:9090 (or auto for LAN discovery)" -ForegroundColor White
    Write-Host "    2. Run: run.bat" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host "  ╔═══════════════════════════════════════════════════╗" -ForegroundColor Yellow
    Write-Host "  ║  Setup finished with warnings (see above).        ║" -ForegroundColor Yellow
    Write-Host "  ║  Fix the warnings, then re-run setup.ps1          ║" -ForegroundColor Yellow
    Write-Host "  ╚═══════════════════════════════════════════════════╝" -ForegroundColor Yellow
    Write-Host ""
}
