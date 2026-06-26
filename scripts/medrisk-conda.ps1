function Join-MedRiskPythonPath {
  param(
    [Parameter(Mandatory = $true)]
    [string]$EnvPath
  )

  $isWindowsHost = ($env:OS -eq "Windows_NT") -or ($PSVersionTable.PSEdition -eq "Desktop") -or ($IsWindows -eq $true)
  if ($isWindowsHost) {
    return (Join-Path $EnvPath "python.exe")
  }

  return (Join-Path (Join-Path $EnvPath "bin") "python")
}

function Add-MedRiskPythonCandidate {
  param(
    [Parameter(Mandatory = $true)]
    [AllowEmptyCollection()]
    [System.Collections.Generic.List[string]]$Candidates,

    [string]$Path
  )

  if ([string]::IsNullOrWhiteSpace($Path)) {
    return
  }

  if (-not $Candidates.Contains($Path)) {
    [void]$Candidates.Add($Path)
  }
}

function Add-MedRiskEnvCandidate {
  param(
    [Parameter(Mandatory = $true)]
    [AllowEmptyCollection()]
    [System.Collections.Generic.List[string]]$Candidates,

    [string]$EnvPath
  )

  if ([string]::IsNullOrWhiteSpace($EnvPath)) {
    return
  }

  $normalizedEnvPath = $EnvPath.TrimEnd([char[]]@('\', '/'))
  if ((Split-Path -Leaf $normalizedEnvPath) -ieq "MedRisk") {
    Add-MedRiskPythonCandidate $Candidates (Join-MedRiskPythonPath $normalizedEnvPath)
  }
}

function Add-MedRiskRootCandidate {
  param(
    [Parameter(Mandatory = $true)]
    [AllowEmptyCollection()]
    [System.Collections.Generic.List[string]]$Candidates,

    [string]$CondaRoot
  )

  if ([string]::IsNullOrWhiteSpace($CondaRoot)) {
    return
  }

  $normalizedCondaRoot = $CondaRoot.TrimEnd([char[]]@('\', '/'))
  Add-MedRiskPythonCandidate $Candidates (Join-MedRiskPythonPath (Join-Path (Join-Path $normalizedCondaRoot "envs") "MedRisk"))
  Add-MedRiskEnvCandidate $Candidates $normalizedCondaRoot
}

function Get-MedRiskCondaCommand {
  $command = Get-Command "conda.exe" -ErrorAction SilentlyContinue
  if (-not $command) {
    $command = Get-Command "conda" -ErrorAction SilentlyContinue
  }
  return $command
}

function Get-MedRiskCommandPath {
  param(
    [Parameter(Mandatory = $true)]
    $Command
  )

  if ($Command.Path) {
    return $Command.Path
  }

  return $Command.Source
}

function Resolve-MedRiskPython {
  $candidates = New-Object 'System.Collections.Generic.List[string]'

  if ($env:MEDRISK_CONDA_ROOT) {
    Add-MedRiskRootCandidate $candidates $env:MEDRISK_CONDA_ROOT
  }

  if ($env:CONDA_PREFIX) {
    Add-MedRiskEnvCandidate $candidates $env:CONDA_PREFIX
  }

  $knownCondaRoots = @(
    "D:\IDEA\Anaconda",
    "D:\IDEA\anaconda",
    "$env:USERPROFILE\anaconda3",
    "$env:USERPROFILE\miniconda3",
    "C:\ProgramData\anaconda3",
    "C:\ProgramData\miniconda3"
  )

  foreach ($root in $knownCondaRoots) {
    Add-MedRiskRootCandidate $candidates $root
  }

  $condaCommand = Get-MedRiskCondaCommand
  if ($condaCommand) {
    $condaPath = Get-MedRiskCommandPath $condaCommand
    if ($condaPath) {
      $condaFolder = Split-Path -Parent $condaPath
      $condaRootFromCommand = Split-Path -Parent $condaFolder
      Add-MedRiskRootCandidate $candidates $condaRootFromCommand
    }

    try {
      $condaBase = (& $condaPath info --base 2>$null | Select-Object -First 1)
      if ($condaBase) {
        Add-MedRiskRootCandidate $candidates $condaBase.Trim()
      }
    } catch {
      # Best-effort discovery only. Explicit candidates still decide success.
    }

    try {
      $envList = & $condaPath env list --json 2>$null | ConvertFrom-Json
      foreach ($envPath in $envList.envs) {
        Add-MedRiskEnvCandidate $candidates $envPath
      }
    } catch {
      # Best-effort discovery only. Explicit candidates still decide success.
    }
  }

  foreach ($candidate in $candidates) {
    if (Test-Path $candidate) {
      return (Resolve-Path $candidate).Path
    }
  }

  throw @"
MedRisk conda environment Python was not found.
Create a conda environment named MedRisk, or set MEDRISK_CONDA_ROOT to the Anaconda/Miniconda root directory.
Do not use medrisk_model_service\.venv or a random PATH python for the model service.
"@
}
