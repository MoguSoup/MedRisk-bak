$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "medrisk-conda.ps1")

function Invoke-Native {
  param(
    [string]$FilePath,
    [string[]]$ArgumentList = @()
  )

  & $FilePath @ArgumentList
  if ($LASTEXITCODE -ne 0) {
    throw "$FilePath failed with exit code $LASTEXITCODE."
  }
}

function Resolve-Tool($primary, $fallback) {
  $cmd = Get-Command $primary -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $cmd = Get-Command $fallback -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  throw "Required tool not found: $primary"
}

function Test-FrontendDependencies([string]$workingDirectory) {
  $requiredPaths = @(
    "node_modules\.bin\vite.cmd",
    "node_modules\@vitejs\plugin-vue",
    "node_modules\jsdom",
    "node_modules\vue"
  )

  foreach ($relativePath in $requiredPaths) {
    if (-not (Test-Path (Join-Path $workingDirectory $relativePath))) {
      return $false
    }
  }
  return $true
}

$mvnTool = Resolve-Tool "mvn.cmd" "mvn"
$npmTool = Resolve-Tool "npm.cmd" "npm"
$pythonExe = Resolve-MedRiskPython
$stopScript = Join-Path $root "scripts\stop-all.ps1"

if (Test-Path $stopScript) {
  & $stopScript
}

Push-Location (Join-Path $root "medrisk_backend")
try {
  Invoke-Native $mvnTool @("test")
} finally {
  Pop-Location
}

$previousNoUserSite = $env:PYTHONNOUSERSITE
$env:PYTHONNOUSERSITE = "1"
Push-Location (Join-Path $root "medrisk_model_service")
try {
  Invoke-Native $pythonExe @("-s", "-m", "pip", "install", "-i", "https://pypi.org/simple", "-r", "requirements.txt")
  Invoke-Native $pythonExe @("-s", "-m", "pytest")
} finally {
  Pop-Location
  $env:PYTHONNOUSERSITE = $previousNoUserSite
}

Push-Location (Join-Path $root "medrisk_frontend")
try {
  if (-not (Test-FrontendDependencies (Get-Location).Path)) {
    Invoke-Native $npmTool @("install")
  }
  Invoke-Native $npmTool @("run", "test")
  Invoke-Native $npmTool @("run", "build")
} finally {
  Pop-Location
}
