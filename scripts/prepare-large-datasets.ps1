$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "medrisk-conda.ps1")

$PythonExe = Resolve-MedRiskPython

Write-Host "Using Python: $PythonExe"
Push-Location $RepoRoot
try {
  New-Item -ItemType Directory -Force -Path "data\raw", "data\processed" | Out-Null
  & $PythonExe "scripts\prepare_large_datasets.py"
} finally {
  Pop-Location
}
