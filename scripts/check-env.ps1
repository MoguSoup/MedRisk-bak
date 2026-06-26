$ErrorActionPreference = "Stop"

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptRoot "medrisk-conda.ps1")

Write-Host "MedRisk AI environment check"
Write-Host "PowerShell: $($PSVersionTable.PSVersion)"

function Show-Command {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Name,

    [string[]]$VersionArgs = @("--version")
  )

  $cmd = Get-Command $name -ErrorAction SilentlyContinue
  if (-not $cmd) {
    Write-Host "${name}: NOT FOUND" -ForegroundColor Yellow
    return
  }
  Write-Host "${name}: $($cmd.Source)"
  try {
    & $cmd.Source @VersionArgs
  } catch {
    Write-Host "$name version check failed: $($_.Exception.Message)" -ForegroundColor Yellow
  }
}

Show-Command "java"
Show-Command "mvn" @("-version")
Show-Command "node"
Show-Command "npm"
Show-Command "python"
Show-Command "docker"

try {
  $modelPython = Resolve-MedRiskPython
  Write-Host "MedRisk model Python: $modelPython"
  & $modelPython -s --version
} catch {
  Write-Host "MedRisk model Python: NOT FOUND" -ForegroundColor Yellow
  Write-Host $_.Exception.Message -ForegroundColor Yellow
}

Write-Host "Listening ports:"
Get-NetTCPConnection -State Listen -LocalPort 5173,8080,8090,3306 -ErrorAction SilentlyContinue |
  Select-Object LocalAddress, LocalPort, OwningProcess
