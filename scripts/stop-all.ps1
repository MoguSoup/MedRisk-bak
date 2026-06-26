$ErrorActionPreference = "Continue"

$root = Split-Path -Parent $PSScriptRoot
$processFile = Join-Path $root "logs\medrisk-processes.json"
$ports = @(5173, 8080, 8090)
$processIds = @()
$allProcesses = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue)

if (Test-Path $processFile) {
  try {
    $items = Get-Content -Raw -Encoding UTF8 $processFile | ConvertFrom-Json
    $processIds += @($items | ForEach-Object { $_.pid })
  } catch {
    Write-Host "Could not read process registry: $processFile"
  }
}

foreach ($port in $ports) {
  $connections = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
  foreach ($connection in $connections) {
    if ($connection.OwningProcess -gt 0) {
      $processIds += $connection.OwningProcess
    }
  }
}

$escapedRoot = [regex]::Escape($root)
$projectProcesses = $allProcesses | Where-Object {
  $commandLine = $_.CommandLine
  $commandLine -and
  $_.ProcessId -ne $PID -and
  $commandLine -match $escapedRoot -and
  $commandLine -match "uvicorn app\.main:app|spring-boot:run|vite|node_modules\\@esbuild|medrisk_frontend|medrisk_backend|medrisk_model_service"
}
$processIds += @($projectProcesses | ForEach-Object { $_.ProcessId })

$processById = @{}
foreach ($process in $allProcesses) {
  $processById[$process.ProcessId] = $process
}

$queue = @($projectProcesses | Where-Object {
  $_.CommandLine -match "medrisk_frontend|node_modules\\@esbuild|vite"
} | ForEach-Object { $_.ProcessId })
while ($queue.Count -gt 0) {
  $currentId = $queue[0]
  $queue = @($queue | Select-Object -Skip 1)
  if (-not $processById.ContainsKey($currentId)) {
    continue
  }
  $parent = $processById[$processById[$currentId].ParentProcessId]
  if (
    $parent -and
    $parent.ProcessId -ne $PID -and
    $parent.Name -in @("node.exe", "cmd.exe") -and
    $parent.CommandLine -match "npm|run dev"
  ) {
    if ($processIds -notcontains $parent.ProcessId) {
      $processIds += $parent.ProcessId
      $queue += $parent.ProcessId
    }
  }
}

$processIds |
  Where-Object { $_ -and $_ -ne $PID } |
  Sort-Object -Unique -Descending |
  ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }

if (Test-Path $processFile) {
  Remove-Item -LiteralPath $processFile -Force -ErrorAction SilentlyContinue
}

Write-Host "MedRisk AI local processes stopped."
