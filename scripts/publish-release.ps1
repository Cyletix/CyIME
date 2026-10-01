[CmdletBinding()]
param([Parameter(Mandatory)][string]$NotesFile, [string]$ReleaseTag = "")
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $version = [regex]::Match((Get-Content app/build.gradle.kts -Raw), 'versionName = "([^"]+)"').Groups[1].Value
    if (-not $ReleaseTag) { $ReleaseTag = $version }
    if ($ReleaseTag -notmatch ("^" + [regex]::Escape($version) + "(-r[0-9]+)?$")) {
        throw "Release tag must match the application version, optionally with a revision suffix."
    }
    $assets = @()
    $commit = (git rev-parse HEAD).Trim()
    foreach ($edition in @('standard', 'full')) {
        $directory = "app/build/outputs/apk/editions/$version/$edition/release"
        $receipt = Get-Content "$directory/latest.json" -Raw | ConvertFrom-Json
        $gate = Get-Content "$directory/layout-gate.json" -Raw | ConvertFrom-Json
        if ($receipt.buildType -ne 'release' -or $receipt.layoutGate -ne 'passed' -or $gate.version -ne $version -or
            $gate.bundledModels -ne ($edition -eq 'full') -or $receipt.sourceCommit -ne $commit) {
            throw "Release blocked: no matching successful gate for $edition."
        }
        if (@($receipt.files).Count -ne 1 -or $receipt.files[0].path -notlike "*-arm64-v8a.apk") {
            throw "Release blocked: each edition must contain exactly one ARM64 delivery APK."
        }
        foreach ($item in $receipt.files) {
            if ((Get-FileHash -LiteralPath $item.path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $item.sha256) {
                throw "Release blocked: APK changed since validation: $($item.path)"
            }
            $assets += $item.path
        }
    }
    $sourcePaths = @('app/src/main', 'app/build.gradle.kts', 'app/build-logic', 'patches', 'gradle', 'scripts/verify-layout.ps1')
    if ((git diff --ignore-submodules=dirty HEAD -- @sourcePaths) -or
        (git ls-files --others --exclude-standard -- @sourcePaths)) {
        throw 'Release blocked: source changed since validation. Commit and rebuild.'
    }
    foreach ($source in @(@('app/src/main/jni/librime', 'cyime-librime.patch'),
        @('app/src/main/jni/librime-lua-deps', 'cyime-lua-android.patch'),
        @('app/src/main/assets/rime', 'cyime-rime-schema.patch'))) {
        & git -C $source[0] apply --reverse --check (Join-Path $root "patches/$($source[1])")
        if ($LASTEXITCODE) { throw "Release blocked: source patch does not match $($source[0])" }
    }
    & gh release create $ReleaseTag @assets --target $commit --title "CyIME $ReleaseTag" --notes-file $NotesFile --latest
    if ($LASTEXITCODE) { throw 'GitHub release failed.' }
} finally { Pop-Location }
