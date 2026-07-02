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

function Show-DemoFlywayGuard {
  $repoRoot = Split-Path -Parent $ScriptRoot
  $demoConfig = Join-Path $repoRoot "medrisk_backend\src\main\resources\application-demo.yml"
  $defaultConfig = Join-Path $repoRoot "medrisk_backend\src\main\resources\application.yml"

  if (-not (Test-Path $demoConfig)) {
    Write-Host "Demo Flyway guard: application-demo.yml NOT FOUND" -ForegroundColor Yellow
    return
  }

  $demoText = Get-Content -Path $demoConfig -Raw
  $hasBaseline = $demoText -match "(?m)^\s*baseline-on-migrate:\s*true\s*$"
  $hasValidateDisabled = $demoText -match "(?m)^\s*validate-on-migrate:\s*false\s*$"

  if ($hasBaseline -and $hasValidateDisabled) {
    Write-Host "Demo Flyway guard: OK (local H2 demo checksum validation disabled)"
  } else {
    Write-Host "Demo Flyway guard: WARNING" -ForegroundColor Yellow
    Write-Host "  application-demo.yml must keep baseline-on-migrate=true and validate-on-migrate=false for the local H2 demo database." -ForegroundColor Yellow
    Write-Host "  Without this, stale data/medrisk-demo Flyway checksums can stop backend startup and make login return 500." -ForegroundColor Yellow
  }

  if (Test-Path $defaultConfig) {
    $defaultText = Get-Content -Path $defaultConfig -Raw
    if ($defaultText -match "(?m)^\s*validate-on-migrate:\s*false\s*$") {
      Write-Host "Default Flyway guard: WARNING" -ForegroundColor Yellow
      Write-Host "  Do not disable Flyway validation in application.yml; Linux Docker Compose/MySQL should keep default validation." -ForegroundColor Yellow
    }
  }
}

function Test-TcpConnect([string]$HostName, [int]$Port, [int]$TimeoutMs = 1000) {
  $client = [System.Net.Sockets.TcpClient]::new()
  $async = $null
  try {
    $async = $client.BeginConnect($HostName, $Port, $null, $null)
    if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
      return $false
    }
    $client.EndConnect($async)
    return $true
  } catch {
    return $false
  } finally {
    if ($async -and $async.AsyncWaitHandle) {
      $async.AsyncWaitHandle.Close()
    }
    $client.Close()
  }
}

function Show-DockerAvailability {
  $docker = Get-Command "docker" -ErrorAction SilentlyContinue
  if (-not $docker) {
    Write-Host "Docker daemon: NOT FOUND (local Neo4j Docker quick-start is unavailable)" -ForegroundColor Yellow
    return
  }

  $previousErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    & $docker.Source info 1>$null 2>$null
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }
  if ($exitCode -eq 0) {
    Write-Host "Docker daemon: OK"
  } else {
    Write-Host "Docker daemon: NOT REACHABLE (start Docker Desktop before using the Neo4j Docker quick-start)" -ForegroundColor Yellow
  }
}

function Show-Neo4jGuard {
  $uriText = if ([string]::IsNullOrWhiteSpace($env:MEDRISK_NEO4J_URI)) { "bolt://localhost:7687" } else { $env:MEDRISK_NEO4J_URI }
  Write-Host "Neo4j URI: $uriText"

  try {
    $uri = [Uri]$uriText
  } catch {
    Write-Host "Neo4j URI: INVALID (expected bolt://localhost:7687 or neo4j://localhost:7687)" -ForegroundColor Yellow
    return
  }

  if ($uri.Scheme -notin @("bolt", "neo4j")) {
    Write-Host "Neo4j URI: INVALID SCHEME (expected bolt:// or neo4j://)" -ForegroundColor Yellow
    return
  }

  $hostName = if ([string]::IsNullOrWhiteSpace($uri.Host)) { "localhost" } else { $uri.Host }
  $port = if ($uri.Port -gt 0) { $uri.Port } else { 7687 }

  if ($hostName -ieq "neo4j") {
    Write-Host "Neo4j URI: WARNING (neo4j is the Docker Compose service name; Windows local startup checks localhost:$port)" -ForegroundColor Yellow
    Write-Host "  Update local .env to MEDRISK_NEO4J_URI=bolt://localhost:$port. Keep bolt://neo4j:$port only for Linux Docker Compose." -ForegroundColor Yellow
    $hostName = "localhost"
  }

  if (Test-TcpConnect $hostName $port) {
    Write-Host "Neo4j Bolt: OK (${hostName}:$port)"
  } else {
    Write-Host "Neo4j Bolt: NOT REACHABLE (${hostName}:$port)" -ForegroundColor Yellow
    Write-Host "  Windows demo startup requires a local Neo4j Bolt listener. It does not auto-connect to med_tencent or open remote Bolt." -ForegroundColor Yellow
    Write-Host "  Start a local Neo4j service, or when Docker is available run: docker run --name medrisk-neo4j -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/medrisk-neo4j -d neo4j:5.26-community" -ForegroundColor Yellow
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

Show-DemoFlywayGuard
Show-Neo4jGuard
Show-DockerAvailability

Write-Host "Listening ports:"
Get-NetTCPConnection -State Listen -LocalPort 5173,8080,8090,3306,7474,7687 -ErrorAction SilentlyContinue |
  Select-Object LocalAddress, LocalPort, OwningProcess
