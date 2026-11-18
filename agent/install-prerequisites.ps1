# These commands run elevated every time run-agent.bat is opened. Both installers
# report that the product is already installed when applicable; the Agent launcher
# continues afterward regardless, as requested for the lab setup flow.
$ErrorActionPreference = "Continue"

Write-Host "Installing/updating WSL..."
& wsl.exe --install
$wslExitCode = $LASTEXITCODE
if ($wslExitCode -ne 0) {
    Write-Warning "wsl --install returned exit code $wslExitCode. Continuing with Docker Desktop."
}

Write-Host "Installing/updating Docker Desktop..."
& winget.exe install -e --id Docker.DockerDesktop --accept-package-agreements --accept-source-agreements
$wingetExitCode = $LASTEXITCODE
if ($wingetExitCode -ne 0) {
    Write-Warning "winget returned exit code $wingetExitCode. Continuing to launch the Agent."
}
