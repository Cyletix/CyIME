[CmdletBinding()]
param([ValidateSet('Debug','Release')][string]$BuildType='Release',
    [string[]]$GradleArguments=@(), [int]$AdbPort=5037, [string]$Serial='')
$ErrorActionPreference='Stop'
& "$PSScriptRoot/build-apk.ps1" -BuildType $BuildType -GradleArguments $GradleArguments -AdbPort $AdbPort -Serial $Serial
& "$PSScriptRoot/build-apk.ps1" -BuildType $BuildType -BundleModels -GradleArguments $GradleArguments -AdbPort $AdbPort -Serial $Serial
