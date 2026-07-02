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

function Write-Status([string]$message) {
  [Console]::Out.WriteLine($message)
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

function Invoke-LoggedChecked($file, $arguments, $logPath) {
  & $file @arguments *> $logPath
  if ($LASTEXITCODE -ne 0) {
    Write-Status "$file failed with exit code $LASTEXITCODE. Last log lines from ${logPath}:"
    if (Test-Path $logPath) {
      Get-Content -Path $logPath -Tail 40 -ErrorAction SilentlyContinue
    }
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

function Get-PortFromCommandLine([string]$commandLine) {
  if ($commandLine -and $commandLine -match "--port\s+(\d+)") {
    return [int]$Matches[1]
  }
  return $null
}

function Get-RegisteredFrontendPort {
  if (-not (Test-Path $processFile)) {
    return $null
  }

  try {
    $records = @(Get-Content -Path $processFile -Raw | ConvertFrom-Json)
  } catch {
    return $null
  }

  foreach ($record in $records) {
    if ($record.name -ne "frontend" -or -not $record.pid) {
      continue
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)" -ErrorAction SilentlyContinue
    if (-not $process) {
      continue
    }

    if ($record.PSObject.Properties.Name -contains "port" -and $record.port -and (Test-PortListening ([int]$record.port))) {
      return [int]$record.port
    }

    $port = Get-PortFromCommandLine $process.CommandLine
    if ($port -and (Test-PortListening $port)) {
      return $port
    }
  }
  return $null
}

function Get-ProjectFrontendPorts {
  $frontendRoot = Join-Path $root "medrisk_frontend"
  $escapedFrontendRoot = [regex]::Escape($frontendRoot)
  $ports = @()

  $processes = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -and
    $_.CommandLine -match $escapedFrontendRoot -and
    $_.CommandLine -match "vite(\.cmd|\.js)"
  }

  foreach ($process in $processes) {
    $port = Get-PortFromCommandLine $process.CommandLine
    if ($port -and (Test-PortListening $port)) {
      $ports += $port
    }
  }

  return @($ports | Sort-Object -Unique)
}

function Resolve-FrontendPort {
  $preferredPorts = @(5173, 5241, 5484, 5500, 5600, 3000, 4173, 9000)
  $registeredPort = Get-RegisteredFrontendPort
  if ($registeredPort) {
    Write-Status "Frontend already running on registered port $registeredPort."
    return $registeredPort
  }

  $existingProjectPorts = @(Get-ProjectFrontendPorts)
  foreach ($port in $preferredPorts) {
    if ($existingProjectPorts -contains $port) {
      Write-Status "Frontend already running on port $port."
      return $port
    }
  }

  if (Test-PortBindable 5173) {
    return 5173
  }

  if (Test-PortListening 5173) {
    Write-Status "Frontend port 5173 is already in use by another process; using a fallback port."
  }

  foreach ($port in @($preferredPorts | Where-Object { $_ -ne 5173 })) {
    if ((-not (Test-PortListening $port)) -and (Test-PortBindable $port)) {
      Write-Status "Frontend port 5173 is not bindable on this Windows host; using $port instead."
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

  if ($hostName -ieq "neo4j") {
    $uriText = "$($uri.Scheme)://localhost:$port"
    $hostName = "localhost"
    $env:MEDRISK_NEO4J_URI = $uriText
    Write-Status "Neo4j URI used Docker Compose service name 'neo4j'; Windows demo startup will use $uriText."
  }

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

function Get-DockerAvailabilityMessage {
  $docker = Get-Command "docker" -ErrorAction SilentlyContinue
  if (-not $docker) {
    return "Docker CLI was not found, so the local Neo4j Docker quick-start command cannot run on this machine."
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
    return "Docker is available. You can start a local Neo4j container with the command shown above."
  }

  return "Docker CLI is installed, but Docker Desktop/daemon is not reachable. Start Docker Desktop first, or start an installed local Neo4j service."
}

function New-Neo4jUnavailableMessage($endpoint) {
  $dockerMessage = Get-DockerAvailabilityMessage
  @"
Neo4j is required before starting MedRisk backend, but $($endpoint.Uri) is not reachable.
Local check: $($endpoint.Host):$($endpoint.Port) is not accepting Bolt connections.
Windows demo startup uses local Neo4j only; it will not connect to med_tencent automatically and should not require opening med_tencent:7687 to the public internet.

Fix one of these local options:
  1. Start your installed local Neo4j service and keep Bolt listening on $($endpoint.Host):$($endpoint.Port).
  2. If Docker is available, run:
     docker run --name medrisk-neo4j -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/medrisk-neo4j -d neo4j:5.26-community

$dockerMessage
"@
}

function Assert-Neo4jAvailable {
  $endpoint = Resolve-Neo4jEndpoint
  if (-not (Test-TcpConnect $endpoint.Host $endpoint.Port)) {
    throw (New-Neo4jUnavailableMessage $endpoint)
  }
  Write-Status "Neo4j:   $($endpoint.Uri)"
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

function Test-HttpReady([string]$uri, [string]$expectedText = "") {
  try {
    $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 3
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 400) {
      return $false
    }
    if (-not [string]::IsNullOrWhiteSpace($expectedText) -and $response.Content -notmatch [regex]::Escape($expectedText)) {
      return $false
    }
    return $true
  } catch {
    return $false
  }
}

function Show-LogTail([string[]]$paths) {
  foreach ($path in $paths) {
    if (Test-Path $path) {
      Write-Output "Last log lines from ${path}:"
      Get-Content -Path $path -Tail 40 -ErrorAction SilentlyContinue
    }
  }
}

function Wait-HttpReady {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Name,

    [Parameter(Mandatory = $true)]
    [string]$Uri,

    [string]$ExpectedText = "",

    [int]$TimeoutSeconds = 60,

    [string[]]$LogPaths = @()
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-HttpReady $Uri $ExpectedText) {
      Write-Output "$Name ready: $Uri"
      return
    }
    Start-Sleep -Seconds 2
  }

  Show-LogTail $LogPaths
  throw "$Name did not become ready within $TimeoutSeconds seconds: $Uri"
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
    Write-Status "Checking model-service Python dependencies..."
    Invoke-LoggedChecked $modelPython @("-s", "-m", "pip", "install", "--disable-pip-version-check", "-q", "-i", "https://pypi.org/simple", "-r", "requirements.txt") (Join-Path $logDir "model-service-install.log")
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
      Write-Status "Installing frontend dependencies..."
      Invoke-LoggedChecked $npmTool @("install", "--silent") (Join-Path $logDir "frontend-install.log")
    }
    $viteTool = Join-Path $frontendDir "node_modules\.bin\vite.cmd"
    $frontendItem = Start-MedRiskProcess "frontend" $viteTool @("--host", "127.0.0.1", "--port", [string]$frontendPort) $frontendDir
    $frontendItem | Add-Member -NotePropertyName port -NotePropertyValue $frontendPort
    $items += $frontendItem
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

Write-Output "Waiting for MedRisk AI services to become ready..."
Wait-HttpReady "Model service" "http://localhost:8090/health" "UP" 60 @(
  (Join-Path $logDir "model-service.out.log"),
  (Join-Path $logDir "model-service.err.log")
)
Wait-HttpReady "Backend" "http://localhost:8080/api/health" "UP" 120 @(
  (Join-Path $logDir "backend.out.log"),
  (Join-Path $logDir "backend.err.log")
)
Wait-HttpReady "Frontend" "http://localhost:$frontendPort" "" 60 @(
  (Join-Path $logDir "frontend.out.log"),
  (Join-Path $logDir "frontend.err.log")
)

Write-Output "MedRisk AI services are ready. Logs: $logDir"
Write-Output "Frontend: http://localhost:$frontendPort"
Write-Output "Backend:  http://localhost:8080/api/health"
Write-Output "Model:    http://localhost:8090/health"
Write-Output "Neo4j Browser: http://localhost:7474"
