<# .SYNOPSIS
    GraphVisual Installer for Windows — downloads the latest fat JAR and creates a launcher.
.DESCRIPTION
    Usage:
      irm https://raw.githubusercontent.com/sauravbhattacharya001/GraphVisual/master/install.ps1 | iex
    Or with a specific version:
      $env:GRAPHVISUAL_VERSION = "v2.62.0"; irm ... | iex
#>
[CmdletBinding()]
param(
    [string]$Version = $env:GRAPHVISUAL_VERSION,
    [string]$InstallDir = $(if ($env:GRAPHVISUAL_HOME) { $env:GRAPHVISUAL_HOME } else { "$env:USERPROFILE\.graphvisual" })
)

$ErrorActionPreference = 'Stop'
$Repo = "sauravbhattacharya001/GraphVisual"

function Write-Info  { param($m) Write-Host "==> $m" -ForegroundColor Blue }
function Write-Ok    { param($m) Write-Host "  ✔ $m" -ForegroundColor Green }
function Write-Fail  { param($m) Write-Host "  ✘ $m" -ForegroundColor Red; exit 1 }

# ---------- prerequisites ----------
try {
    $javaVer = & java -version 2>&1 | Select-Object -First 1
    if ($javaVer -match '"(\d+)') {
        $major = [int]$Matches[1]
        if ($major -lt 11) { Write-Fail "Java 11+ required (found Java $major)" }
    }
} catch {
    Write-Fail "Java is required but not found. Install JDK 11+ first."
}

# ---------- resolve version ----------
if (-not $Version) {
    Write-Info "Fetching latest release..."
    try {
        $release = Invoke-RestMethod "https://api.github.com/repos/$Repo/releases/latest" -Headers @{ 'User-Agent' = 'GraphVisual-Installer' }
        $Version = $release.tag_name
    } catch {
        # try gh CLI
        if (Get-Command gh -ErrorAction SilentlyContinue) {
            $Version = gh release view --repo $Repo --json tagName -q .tagName 2>$null
        }
    }
}
if (-not $Version) { Write-Fail "Could not determine latest version." }
Write-Info "Installing GraphVisual $Version"

# ---------- download ----------
New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null

$ver = $Version -replace '^v', ''
$jarName = "graphvisual-$ver-all.jar"
$jarUrl = "https://github.com/$Repo/releases/download/$Version/$jarName"
$jarPath = Join-Path $InstallDir $jarName

Write-Info "Downloading fat JAR..."
$downloaded = $false

# try gh CLI first (handles auth for private repos)
if (Get-Command gh -ErrorAction SilentlyContinue) {
    try {
        gh release download $Version --repo $Repo --pattern "*-all.jar" --dir $InstallDir --clobber 2>$null
        $actual = Get-ChildItem "$InstallDir\*-all.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($actual) { $jarPath = $actual.FullName; $downloaded = $true }
    } catch { }
}

if (-not $downloaded) {
    try {
        Invoke-WebRequest -Uri $jarUrl -OutFile $jarPath -UseBasicParsing
        $downloaded = $true
    } catch {
        # try alternate naming
        $jarName = "graphvisual-$ver.jar"
        $jarUrl = "https://github.com/$Repo/releases/download/$Version/$jarName"
        $jarPath = Join-Path $InstallDir $jarName
        Invoke-WebRequest -Uri $jarUrl -OutFile $jarPath -UseBasicParsing
    }
}

if (-not (Test-Path $jarPath)) { Write-Fail "JAR not found after download." }
Write-Ok "Downloaded $(Split-Path $jarPath -Leaf)"

# store version
Set-Content -Path (Join-Path $InstallDir ".version") -Value $Version

# ---------- create launcher batch ----------
$batPath = Join-Path $InstallDir "graphvisual.cmd"
$batContent = @"
@echo off
setlocal
set "INSTALL_DIR=%GRAPHVISUAL_HOME%"
if "%INSTALL_DIR%"=="" set "INSTALL_DIR=%USERPROFILE%\.graphvisual"

for /f "delims=" %%J in ('dir /b /o-d "%INSTALL_DIR%\*-all.jar" 2^>nul') do (
    set "JAR=%INSTALL_DIR%\%%J"
    goto :found
)
echo GraphVisual JAR not found in %INSTALL_DIR% >&2
exit /b 1

:found
java %* -jar "%JAR%"
"@
Set-Content -Path $batPath -Value $batContent -Encoding ASCII
Write-Ok "Launcher created at $batPath"

# ---------- add to PATH ----------
$userPath = [Environment]::GetEnvironmentVariable('PATH', 'User')
if ($userPath -notlike "*$InstallDir*") {
    [Environment]::SetEnvironmentVariable('PATH', "$InstallDir;$userPath", 'User')
    $env:PATH = "$InstallDir;$env:PATH"
    Write-Ok "Added $InstallDir to user PATH"
}

# ---------- verify ----------
Write-Info "Verifying installation..."
try {
    $output = & java -Djava.awt.headless=true -jar $jarPath --help 2>&1 | Select-Object -First 5
    $output | ForEach-Object { Write-Host "  $_" }
} catch { }

Write-Ok "GraphVisual $Version installed successfully!"
Write-Host ""
Write-Host "  Run:     graphvisual"
Write-Host "  Docker:  docker run --rm ghcr.io/$Repo`:latest"
Write-Host "  Update:  re-run this installer"
Write-Host ""
