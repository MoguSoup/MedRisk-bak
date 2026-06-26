$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "medrisk-conda.ps1")

$logDir = Join-Path $root "logs"
$processFile = Join-Path $logDir "medrisk-processes.json"
$stdinFile = Join-Path $logDir "empty.stdin"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
if (-not (Test-Path $stdinFile)) {
  New-Item -ItemType File -Path $stdinFile | Out-Null
}

function Start-MedRiskProcess($name, $file, $arguments, $workingDirectory) {
  $stdout = Join-Path $logDir "$name.out.log"
  $stderr = Join-Path $logDir "$name.err.log"
  $process = Start-Process -FilePath $file -ArgumentList $arguments -WorkingDirectory $workingDirectory -RedirectStandardInput $stdinFile -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
  [PSCustomObject]@{ name = $name; pid = $process.Id; stdout = $stdout; stderr = $stderr }
}

function Invoke-Checked($file, $arguments) {
  & $file @arguments
  if ($LASTEXITCODE -ne 0) {
    throw "$file failed with exit code $LASTEXITCODE"
  }
}

function Resolve-Tool($primary, $fallback) {
  $cmd = Get-Command $primary -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $cmd = Get-Command $fallback -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  throw "Required tool not found: $primary"
}

function Test-PortListening([int]$port) {
  return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
}

function Test-PortBindable([int]$port) {
  $listener = $null
  try {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse("127.0.0.1"), $port)
    $listener.Start()
    return $true
  } catch {
    return $false
  } finally {
    if ($listener) {
      $listener.Stop()
    }
  }
}

function Resolve-FrontendPort {
  if (Test-PortListening 5173) {
    return 5173
  }
  if (Test-PortBindable 5173) {
    return 5173
  }

  foreach ($port in @(5241, 5484, 5500, 5600, 3000, 4173, 9000)) {
    if ((-not (Test-PortListening $port)) -and (Test-PortBindable $port)) {
      Write-Host "Frontend port 5173 is not bindable on this Windows host; using $port instead."
      return $port
    }
  }
  throw "No available frontend port found. Tried 5173, 5241, 5484, 5500, 5600, 3000, 4173 and 9000."
}

function Resolve-Neo4jEndpoint {
  $uriText = if ([string]::IsNullOrWhiteSpace($env:MEDRISK_NEO4J_URI)) { "bolt://localhost:7687" } else { $env:MEDRISK_NEO4J_URI }
  try {
    $uri = [Uri]$uriText
  } catch {
    throw "Invalid MEDRISK_NEO4J_URI '$uriText'. Expected a Bolt URI such as bolt://localhost:7687."
  }

  if ($uri.Scheme -notin @("bolt", "neo4j")) {
    throw "Invalid MEDRISK_NEO4J_URI '$uriText'. Expected scheme bolt:// or neo4j://."
  }

  $hostName = if ([string]::IsNullOrWhiteSpace($uri.Host)) { "localhost" } else { $uri.Host }
  $port = if ($uri.Port -gt 0) { $uri.Port } else { 7687 }
  [PSCustomObject]@{ Uri = $uriText; Host = $hostName; Port = $port }
}

function Test-TcpConnect([string]$hostName, [int]$port, [int]$timeoutMs = 2000) {
  $client = [System.Net.Sockets.TcpClient]::new()
  $async = $null
  try {
    $async = $client.BeginConnect($hostName, $port, $null, $null)
    if (-not $async.AsyncWaitHandle.WaitOne($timeoutMs, $false)) {
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

function Assert-Neo4jAvailable {
  $endpoint = Resolve-Neo4jEndpoint
  if (-not (Test-TcpConnect $endpoint.Host $endpoint.Port)) {
    throw "Neo4j is required before starting MedRisk backend, but $($endpoint.Uri) is not reachable. Start your external Neo4j service first, or set MEDRISK_NEO4J_URI/MEDRISK_NEO4J_USERNAME/MEDRISK_NEO4J_PASSWORD to a reachable instance."
  }
  Write-Host "Neo4j:   $($endpoint.Uri)"
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
$modelPython = Resolve-MedRiskPython
Assert-Neo4jAvailable
$frontendPort = Resolve-FrontendPort

$items = @()

if (-not (Test-PortListening 8090)) {
  Push-Location (Join-Path $root "medrisk_model_service")
  try {
    $previousNoUserSite = $env:PYTHONNOUSERSITE
    $env:PYTHONNOUSERSITE = "1"
    Invoke-Checked $modelPython @("-s", "-m", "pip", "install", "-i", "https://pypi.org/simple", "-r", "requirements.txt")
    $items += Start-MedRiskProcess "model-service" $modelPython @("-s", "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8090") (Get-Location).Path
  } finally {
    $env:PYTHONNOUSERSITE = $previousNoUserSite
    Pop-Location
  }
}

if (-not (Test-PortListening 8080)) {
  Push-Location (Join-Path $root "medrisk_backend")
  try {
    $items += Start-MedRiskProcess "backend" $mvnTool @("clean", "spring-boot:run", "-Dspring-boot.run.profiles=demo") (Get-Location).Path
  } finally {
    Pop-Location
  }
}

if (-not (Test-PortListening $frontendPort)) {
  Push-Location (Join-Path $root "medrisk_frontend")
  try {
    $frontendDir = (Get-Location).Path
    if (-not (Test-FrontendDependencies $frontendDir)) {
      Invoke-Checked $npmTool @("install")
    }
    $viteTool = Join-Path $frontendDir "node_modules\.bin\vite.cmd"
    $items += Start-MedRiskProcess "frontend" $viteTool @("--host", "127.0.0.1", "--port", [string]$frontendPort) $frontendDir
  } finally {
    Pop-Location
  }
}

if ($items.Count -gt 0) {
  $existing = @()
  if (Test-Path $processFile) {
    try {
      $parsed = Get-Content -Path $processFile -Raw | ConvertFrom-Json
      $existing = @()
      foreach ($record in @($parsed)) {
        if ($record.PSObject.Properties.Name -contains "value") {
          $existing += @($record.value)
        } else {
          $existing += $record
        }
      }
    } catch {
      $existing = @()
    }
  }
  $newNames = @($items | ForEach-Object { $_.name })
  $activeExisting = @($existing | Where-Object {
      $_.pid -and
      ($newNames -notcontains $_.name) -and
      (Get-Process -Id $_.pid -ErrorAction SilentlyContinue)
    })
  @($activeExisting + $items) | ConvertTo-Json -Depth 3 | Set-Content -Encoding UTF8 $processFile
}
Write-Host "MedRisk AI services are starting. Logs: $logDir"
Write-Host "Frontend: http://localhost:$frontendPort"
Write-Host "Backend:  http://localhost:8080/api/health"
Write-Host "Model:    http://localhost:8090/health"
Write-Host "Neo4j Browser: http://localhost:7474"
