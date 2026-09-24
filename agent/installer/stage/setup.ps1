# =============================================================================
# IdleGrid Agent — One-Time Setup Script for Lab PCs
# =============================================================================
# Run this script ONCE on each lab PC (as Administrator) before deploying the
# agent. It will:
#   1. Enable WSL2 (Windows Subsystem for Linux v2)
#   2. Install Docker Desktop (silently)
#   3. Configure Docker Desktop (WSL2 backend, sensible resource limits)
#   4. Check / prompt for Java 17+
#   5. Verify everything is working
#
# Usage (in an Administrator PowerShell):
#   Set-ExecutionPolicy Bypass -Scope Process -Force
#   .\setup.ps1
#
# A REBOOT is required after WSL2 features are enabled (Step 1).
# Re-run the script after rebooting — it is fully idempotent.
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
Write-Host "  ║   IdleGrid Agent — Lab PC Setup  v1.0        ║" -ForegroundColor Cyan
Write-Host "  ╚══════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# =============================================================================
# Step 0 — Must be running as Administrator
# =============================================================================
Write-Step 0 "Checking Administrator privileges"
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Fail "This script must be run as Administrator. Right-click PowerShell → 'Run as administrator'."
}
Write-Ok "Running as Administrator"

# =============================================================================
# Step 1 — Check Windows version (WSL2 needs build 19041+)
# =============================================================================
Write-Step 1 "Checking Windows version"
$build = [System.Environment]::OSVersion.Version.Build
if ($build -lt 19041) {
    Write-Fail "Windows build $build detected. WSL2 requires Windows 10 build 19041 (version 2004) or later."
}
Write-Ok "Windows build $build — OK"

# =============================================================================
# Step 2 — Enable WSL + Virtual Machine Platform features
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

# Set WSL default version to 2
try {
    wsl --set-default-version 2 2>&1 | Out-Null
    Write-Ok "WSL default version set to 2"
} catch {
    Write-Warn "Could not set WSL default version — will be set automatically after reboot"
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
    exit 0
}

# =============================================================================
# Step 3 — Install Docker Desktop (if not already installed)
# =============================================================================
Write-Step 3 "Checking Docker Desktop"

$dockerInstalled = $false
$dockerExe = "$Env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
$dockerCli = "$Env:ProgramFiles\Docker\Docker\resources\bin\docker.exe"

# Also check PATH
try { $null = Get-Command docker -ErrorAction Stop; $dockerInstalled = $true } catch {}
if (Test-Path $dockerExe) { $dockerInstalled = $true }

if ($dockerInstalled) {
    Write-Ok "Docker Desktop is already installed — skipping download"
} else {
    Write-Info "Downloading Docker Desktop installer (~600 MB — please wait)..."
    $dockerInstallerUrl = "https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe"
    $installerPath = "$Env:TEMP\DockerDesktopInstaller.exe"

    try {
        Invoke-WebRequest -Uri $dockerInstallerUrl -OutFile $installerPath -UseBasicParsing
        Write-Ok "Download complete: $installerPath"
    } catch {
        Write-Fail "Failed to download Docker Desktop: $_`nDownload manually from https://www.docker.com/products/docker-desktop/"
    }

    Write-Info "Installing Docker Desktop silently (this takes ~2 minutes)..."
    $proc = Start-Process -FilePath $installerPath `
                          -ArgumentList "install --quiet --accept-license --backend=wsl-2" `
                          -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        Write-Fail "Docker Desktop installer exited with code $($proc.ExitCode)"
    }
    Write-Ok "Docker Desktop installed successfully"

    # Refresh PATH so docker is available in this session
    $Env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("PATH","User")
}

# =============================================================================
# Step 4 — Start Docker Desktop and wait for it to be ready
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
# Step 5 — Configure Docker Desktop for IdleGrid
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
        $settings = Get-Content $settingsFile -Raw | ConvertFrom-Json -AsHashtable
        Write-Info "Existing Docker settings found — merging IdleGrid config"
    } catch {
        Write-Warn "Could not parse existing settings — writing fresh config"
        $settings = @{}
    }
}

# Apply IdleGrid-required settings
$settings["wslEngineEnabled"]              = $true
$settings["useVirtualizationFramework"]    = $false   # WSL2 backend, not HyperV
$settings["memoryMiB"]                     = $dockerRamMb
$settings["cpus"]                          = $cpuCount
$settings["autoStart"]                     = $true    # Docker starts with Windows
$settings["openUIOnStartupDisabled"]       = $true    # Don't pop the Docker UI on each boot
$settings["analyticsEnabled"]             = $false

$settings | ConvertTo-Json -Depth 10 | Set-Content $settingsFile -Encoding UTF8
Write-Ok "Docker settings written to: $settingsFile"
Write-Ok "  WSL2 backend: enabled"
Write-Ok "  Memory: $dockerRamMb MB"
Write-Ok "  CPUs: $cpuCount"
Write-Ok "  Auto-start: enabled"

# Restart Docker Desktop to apply new settings
Write-Info "Restarting Docker Desktop to apply settings..."
try {
    Stop-Process -Name "Docker Desktop" -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    Start-Process $dockerExe -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 8
    Write-Ok "Docker Desktop restarted"
} catch {
    Write-Warn "Could not restart Docker Desktop automatically — please restart it manually"
}

# =============================================================================
# Step 6 — Check Java 17+
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
            Write-Ok "Java $major — OK (17+ required)"
            $javaOk = $true
        } else {
            Write-Warn "Java $major found but IdleGrid requires Java 17 or later"
        }
    }
} catch {
    Write-Warn "Java not found on PATH"
}

if (-not $javaOk) {
    Write-Host ""
    Write-Host "  Java 17+ is required to run the IdleGrid agent." -ForegroundColor Yellow
    Write-Host "  Install it with winget (run in Administrator PowerShell):" -ForegroundColor Yellow
    Write-Host "    winget install Microsoft.OpenJDK.17" -ForegroundColor White
    Write-Host "  Or download from: https://adoptium.net/temurin/releases/?version=17" -ForegroundColor White
    Write-Host "  After installing Java, re-run this script to verify." -ForegroundColor Yellow
}

# =============================================================================
# Step 7 — Final verification
# =============================================================================
Write-Step 7 "Final system verification"

$allOk = $true

# Docker
try {
    $out = docker info 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "docker info — Docker daemon responding"
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
    Write-Info "Pulling alpine:latest (smoke test — ~3 MB)..."
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
# Step 8 — Summary
# =============================================================================
Write-Host ""
if ($allOk) {
    Write-Host "  ╔═══════════════════════════════════════════════════╗" -ForegroundColor Green
    Write-Host "  ║  Setup complete!  This PC is ready for IdleGrid.  ║" -ForegroundColor Green
    Write-Host "  ╚═══════════════════════════════════════════════════╝" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Next steps:" -ForegroundColor Cyan
    Write-Host "    1. Edit agent.properties  →  set master.url=http://<master-ip>:8081" -ForegroundColor White
    Write-Host "    2. Run: run.bat" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host "  ╔═══════════════════════════════════════════════════╗" -ForegroundColor Yellow
    Write-Host "  ║  Setup finished with warnings (see above).        ║" -ForegroundColor Yellow
    Write-Host "  ║  Fix the warnings, then re-run setup.ps1          ║" -ForegroundColor Yellow
    Write-Host "  ╚═══════════════════════════════════════════════════╝" -ForegroundColor Yellow
    Write-Host ""
}
