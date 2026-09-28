# Build from the repository root, archive obsolete APKs, then print verified output paths.
[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')][string]$BuildType = 'Release',
    [string[]]$GradleArguments = @()
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Push-Location $repoRoot
try {
    $config = Get-Content 'app/build.gradle.kts' -Raw
    $version = [regex]::Match($config, 'versionName = "([^"]+)"').Groups[1].Value
    $versionCode = [int][regex]::Match($config, 'versionCode = (\d+)').Groups[1].Value
    if (-not $version) { throw 'Cannot determine app version.' }
    $kind = $BuildType.ToLowerInvariant()
    $apkRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'app/build/outputs/apk'))
    $archive = Join-Path $apkRoot ('archive/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    foreach ($variant in @('debug', 'release')) {
        $output = Join-Path $apkRoot $variant
        if (-not (Test-Path -LiteralPath $output)) { continue }
        $old = @(Get-ChildItem -LiteralPath $output -File -Filter '*.apk' | Where-Object {
            $variant -eq $kind -or -not $_.Name.StartsWith("CyIME-$version-")
        })
        foreach ($file in $old) {
            # Only generated files directly in these two output folders may be moved.
            if ($file.DirectoryName -ne $output) { throw 'Unexpected APK path.' }
            $destination = [IO.Path]::GetFullPath((Join-Path $archive $variant))
            if (-not $destination.StartsWith($apkRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe archive path.' }
            New-Item -ItemType Directory -Force -Path $destination | Out-Null
            Move-Item -LiteralPath $file.FullName -Destination $destination
        }
        if ($old.Count -gt 0 -and @(Get-ChildItem -LiteralPath $output -File -Filter '*.apk').Count -eq 0) {
            foreach ($name in @('output-metadata.json', 'latest.json')) {
                $metadata = Join-Path $output $name
                if (Test-Path -LiteralPath $metadata) { Move-Item -LiteralPath $metadata -Destination (Join-Path $archive $variant) }
            }
        }
    }
    & ./gradlew.bat ":app:assemble$BuildType" --no-daemon @GradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Build failed. Previous packages remain in $archive" }
    $output = Join-Path $apkRoot $kind
    $metadata = Get-Content (Join-Path $output 'output-metadata.json') -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne 'com.cyletix.cyime') { throw 'Unexpected application ID.' }
    $files = foreach ($element in $metadata.elements) {
        if ($element.versionName -ne $version -or $element.versionCode -ne $versionCode) { throw 'APK metadata version mismatch.' }
        $apk = Get-Item -LiteralPath (Join-Path $output $element.outputFile)
        if ($apk.DirectoryName -ne $output) { throw 'Unexpected APK output location.' }
        [pscustomobject]@{ path=$apk.FullName; bytes=$apk.Length; sha256=(Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
    [pscustomobject]@{ version=$version; versionCode=$versionCode; buildType=$kind; files=@($files) } |
        ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $output 'latest.json')
    Write-Host "Built CyIME $version ($BuildType). Old packages: $archive"
    $files | Format-Table path, bytes -AutoSize
} finally { Pop-Location }