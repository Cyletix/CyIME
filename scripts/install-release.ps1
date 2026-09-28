# Explicit installation only. Normal updates preserve app and plugin data.
[CmdletBinding()]
param(
    [ValidateSet('all','app','plugins','uninstall')][string]$Action = 'app',
    [ValidateSet('gradle','ide')][string]$Source = 'gradle',
    [string]$Serial = '',
    [ValidateSet('Debug', 'Release')][string]$BuildType = 'Release'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Push-Location $repoRoot
try {
    $packageName = 'com.cyletix.cyime'
    $adbPath = @(
        if ($env:ANDROID_SDK_ROOT) { Join-Path $env:ANDROID_SDK_ROOT 'platform-tools/adb.exe' }
        if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' }
        if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe' }
    ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $adbPath) { $adbPath = (Get-Command adb -ErrorAction Stop).Source }
    if ($BuildType -eq 'Debug' -and ($Source -ne 'gradle' -or $Action -ne 'app')) {
        throw 'Debug installation supports the Gradle main app only.'
    }
    $devices = @(& $adbPath devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if ($LASTEXITCODE -ne 0) { throw '& $adbPath devices failed.' }
    if ($Serial) {
        if ($Serial -notin $devices) { throw 'Selected device is not connected and authorized.' }
    } elseif ($devices.Count -eq 1) { $Serial = $devices[0] }
    else { throw 'Select exactly one connected device with -Serial.' }
    function Invoke-AdbChecked([string[]]$Arguments) {
        & $adbPath -s $Serial @Arguments
        if ($LASTEXITCODE -ne 0) { throw 'ADB operation failed. No uninstall fallback is performed.' }
    }
    if ($Action -eq 'uninstall') {
        Invoke-AdbChecked @('uninstall', $packageName)
        foreach ($plugin in @('com.kingzcheung.xime.plugin.emoji','com.kingzcheung.xime.plugin.kaomoji')) {
            Invoke-AdbChecked @('uninstall', $plugin)
        }
        return
    }
    if ($Action -in @('all','app')) {
        $config = Get-Content 'app/build.gradle.kts' -Raw
        $version = [regex]::Match($config, 'versionName = "([^"]+)"').Groups[1].Value
        if (-not $version) { throw 'Cannot determine app version.' }
        $directory = if ($Source -eq 'ide') { 'app/release' } else { "app/build/outputs/apk/$($BuildType.ToLowerInvariant())" }
        $abi = ((& $adbPath -s $Serial shell getprop ro.product.cpu.abi) | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Cannot determine device ABI.' }
        if ($abi -notin @('arm64-v8a','armeabi-v7a','x86','x86_64')) { $abi = 'universal' }
        $label = if ($BuildType -eq 'Debug') { '-debug' } else { '' }
        $apk = Join-Path $directory "CyIME-$version$label-$abi.apk"
        if (-not (Test-Path -LiteralPath $apk)) { $apk = Join-Path $directory "CyIME-$version$label-universal.apk" }
        if (-not (Test-Path -LiteralPath $apk)) { throw "No $BuildType APK for $version. Run scripts/build-apk.ps1 -BuildType $BuildType first." }
        Write-Host "Updating $packageName from $apk (preserving data)"
        Invoke-AdbChecked @('install', '-r', (Resolve-Path -LiteralPath $apk).Path)
    }
    if ($Action -in @('all','plugins')) {
        foreach ($plugin in @('meme-bunny','kaomoji')) {
            $directory = if ($Source -eq 'ide') { "plugins/$plugin/release" } else { "plugins/$plugin/build/outputs/apk/release" }
            $apks = @(Get-ChildItem -LiteralPath $directory -Filter '*.apk' -ErrorAction SilentlyContinue)
            if ($apks.Count -ne 1) { throw "Expected exactly one plugin APK in $directory; select/build it before installing." }
            Invoke-AdbChecked @('install', '-r', $apks[0].FullName)
        }
    }
} finally { Pop-Location }