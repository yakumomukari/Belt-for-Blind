[CmdletBinding()]
param(
    [string]$Port,
    [switch]$Flash,
    [switch]$Monitor
)

$ErrorActionPreference = "Stop"
$originalIdfPath = $env:IDF_PATH
$originalPath = $env:PATH
$idfAliasDrive = $null
$locationPushed = $false
$idfScriptPath = $null
$pythonPath = $null

function Invoke-Idf {
    param([string[]]$IdfArguments)

    if ([System.IO.Path]::GetExtension($idfScriptPath) -eq ".py") {
        & $pythonPath $idfScriptPath @IdfArguments
    } else {
        & $idfScriptPath @IdfArguments
    }
    if ($LASTEXITCODE -ne 0) {
        throw "idf.py $($IdfArguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

try {
    $idfCommand = Get-Command idf.py -ErrorAction SilentlyContinue
    if (-not $idfCommand) {
        throw "idf.py was not found. Run this script from an ESP-IDF PowerShell terminal."
    }

    $idfScriptPath = $idfCommand.Source
    $idfRoot = $env:IDF_PATH
    if ([string]::IsNullOrWhiteSpace($idfRoot)) {
        $idfRoot = Split-Path (Split-Path $idfScriptPath -Parent) -Parent
    }
    $idfRoot = [System.IO.Path]::GetFullPath($idfRoot)

    if ($idfRoot -match '\s' -or $idfRoot -match '[^\x00-\x7F]') {
        $idfAliasDrive = @("Z:", "Y:", "X:", "W:", "V:", "U:", "T:") |
            Where-Object { -not (Test-Path "$_\") } |
            Select-Object -First 1
        if (-not $idfAliasDrive) {
            throw "No free drive letter is available for the ESP-IDF ASCII path alias."
        }

        & subst.exe $idfAliasDrive $idfRoot
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create the ESP-IDF path alias $idfAliasDrive."
        }

        $env:IDF_PATH = "$idfAliasDrive\"
        $env:PATH = "$idfAliasDrive\tools;$env:PATH"
        $idfScriptPath = "$idfAliasDrive\tools\idf.py"
    }

    if ([System.IO.Path]::GetExtension($idfScriptPath) -eq ".py") {
        $pythonCommand = Get-Command python.exe -ErrorAction SilentlyContinue
        if (-not $pythonCommand) {
            throw "python.exe was not found in the active ESP-IDF environment."
        }
        $pythonPath = $pythonCommand.Source
    }

    $stagingSuffix = if ($idfAliasDrive) { "-$($idfAliasDrive.TrimEnd(':'))" } else { "" }
    $stagingDirectory = Join-Path ([System.IO.Path]::GetTempPath()) "BeltForBlindFirmware$stagingSuffix"
    $stagingMainDirectory = Join-Path $stagingDirectory "main"
    $buildDirectory = "build"

    New-Item -ItemType Directory -Path $stagingMainDirectory -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "CMakeLists.txt") -Destination $stagingDirectory -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "sdkconfig.defaults") -Destination $stagingDirectory -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "main\CMakeLists.txt") -Destination $stagingMainDirectory -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "main\main.c") -Destination $stagingMainDirectory -Force

    Push-Location $stagingDirectory
    $locationPushed = $true

    $sdkConfigPath = Join-Path $stagingDirectory "sdkconfig"
    $targetIsEsp32 = Test-Path $sdkConfigPath
    if ($targetIsEsp32) {
        $targetIsEsp32 = Select-String -Path $sdkConfigPath -Pattern '^CONFIG_IDF_TARGET="esp32"$' -Quiet
    }

    if (-not $targetIsEsp32) {
        Invoke-Idf @("-B", $buildDirectory, "set-target", "esp32")
    }

    Invoke-Idf @("-B", $buildDirectory, "build")

    if ($Flash -or $Monitor) {
        $availablePorts = @([System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object)

        if ([string]::IsNullOrWhiteSpace($Port)) {
            if ($availablePorts.Count -eq 1) {
                $Port = $availablePorts[0]
                Write-Output "Using the only detected serial port: $Port"
            } elseif ($availablePorts.Count -eq 0) {
                throw "No serial port was detected. Reconnect the ESP32 with a USB data cable and check Device Manager."
            } else {
                throw "Multiple serial ports were detected: $($availablePorts -join ', '). Specify one with -Port COMx."
            }
        } elseif ($Port -notin $availablePorts) {
            $availableText = if ($availablePorts.Count -eq 0) { "none" } else { $availablePorts -join ", " }
            throw "Serial port $Port does not exist. Available ports: $availableText."
        }

        $actions = @()
        if ($Flash) {
            $actions += "flash"
        }
        if ($Monitor) {
            $actions += "monitor"
        }
        Invoke-Idf (@("-B", $buildDirectory, "-p", $Port) + $actions)
    }

    Write-Output "Firmware build directory: $stagingDirectory\build"
}
finally {
    if ($locationPushed) {
        Pop-Location
    }
    $env:PATH = $originalPath
    if ($null -eq $originalIdfPath) {
        Remove-Item Env:IDF_PATH -ErrorAction SilentlyContinue
    } else {
        $env:IDF_PATH = $originalIdfPath
    }
    if ($idfAliasDrive) {
        & subst.exe $idfAliasDrive /D 2>$null
    }
}
