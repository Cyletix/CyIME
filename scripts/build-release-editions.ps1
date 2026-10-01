# Always produce the standard and ready-to-use model editions; release delivery is ARM64.
[CmdletBinding()]
param(
    [string[]]$GradleArguments = @(),
    [int]$AdbPort = 5037,
    [string]$Serial = ''
)
$ErrorActionPreference = 'Stop'
foreach ($bundled in @($false, $true)) {
    & "$PSScriptRoot/build-apk.ps1" -BuildType Release -BundleModels:$bundled `
        -AdbPort $AdbPort -Serial $Serial -GradleArguments $GradleArguments
}
