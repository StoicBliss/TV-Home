# One-time setup on Windows: Python ADB client, JDK 17, and Android SDK checks.
# Usage:
#   .\setup.ps1
#   .\setup.ps1 -NoLauncher   # Python ADB client only

param(
    [switch]$NoLauncher
)

$ErrorActionPreference = "Stop"
$RepoRoot = $PSScriptRoot

Write-Host "=== Setting up tv-home environment on Windows ===" -ForegroundColor Cyan

# 1. Python virtual environment
$venvDir = Join-Path $RepoRoot "tools\venv"
$venvPython = Join-Path $venvDir "Scripts\python.exe"

if (-not (Test-Path $venvPython)) {
    Write-Host "Creating Python virtual environment in tools/venv..."
    $sysPython = (Get-Command python -ErrorAction SilentlyContinue).Source
    if (-not $sysPython) {
        Write-Error "Python 3 is required. Please install Python 3.8+ and add it to PATH."
    }
    & python -m venv $venvDir
}

Write-Host "Installing/updating Python dependencies (adb-shell)..."
& $venvPython -m pip install -q --no-cache-dir -r (Join-Path $RepoRoot "tools\requirements.txt")
Write-Host "Python ADB client ready in tools/venv" -ForegroundColor Green

if ($NoLauncher) {
    Write-Host "Setup completed (--NoLauncher)." -ForegroundColor Green
    exit 0
}

# 2. Check Java JDK 17
Write-Host "`nChecking Java JDK 17..."
$javaFound = $false
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $javaFound = $true
    Write-Host "Found Java at: $env:JAVA_HOME" -ForegroundColor Green
} else {
    $candidates = @(
        "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot",
        "$env:LOCALAPPDATA\Programs\jdk-17",
        "$env:ProgramFiles\Java\jdk-17*"
    )
    foreach ($cand in $candidates) {
        $found = Get-Item $cand -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found -and (Test-Path "$($found.FullName)\bin\javac.exe")) {
            $javaFound = $true
            Write-Host "Found Java at: $($found.FullName)" -ForegroundColor Green
            break
        }
    }
}

if (-not $javaFound) {
    Write-Warning "JDK 17 not detected in standard locations. If build fails, set `$env:JAVA_HOME to your JDK 17 root."
}

# 3. Check Android SDK
Write-Host "`nChecking Android SDK..."
$sdk = $env:ANDROID_HOME
if (-not $sdk -or -not (Test-Path $sdk)) {
    if (Test-Path "$env:LOCALAPPDATA\Android\Sdk") {
        $sdk = "$env:LOCALAPPDATA\Android\Sdk"
    }
}

if ($sdk -and (Test-Path $sdk)) {
    Write-Host "Found Android SDK at: $sdk" -ForegroundColor Green
} else {
    Write-Warning "Android SDK not detected. Please install Android Command-Line Tools or Android Studio."
}

Write-Host "`nSetup complete! Next step: find your TV on Wi-Fi:" -ForegroundColor Cyan
Write-Host "  .\tools\tv.ps1 --find"
Write-Host "  .\tools\tv.ps1 --probe"
