[CmdletBinding()]
param([int]$AdbPort = 5037, [string]$Serial = '',
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $dir = 'app/build/reports/layout-gate'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    if (Test-Path -LiteralPath "$dir/passed.json") { Remove-Item -LiteralPath "$dir/passed.json" }
    if (-not (Test-Path -LiteralPath $Adb)) { throw 'Layout gate: adb not found. Specify -Adb.' }
    if (-not $Serial) {
        $devices = @(& $Adb -P $AdbPort devices | Where-Object { $_ -match '^([^\s]+)\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
        if ($devices.Count -ne 1) { throw 'Layout gate requires one authorized device/emulator, or explicit -Serial. No passing result was generated.' }
        $Serial = $devices[0]
    }
    $abi = (& $Adb -P $AdbPort -s $Serial shell getprop ro.product.cpu.abi | Out-String).Trim()
    $metadata = Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
    $entry = @($metadata.elements | Where-Object { @($_.filters | Where-Object { $_.filterType -eq 'ABI' -and $_.value -eq $abi }).Count -gt 0 })
    if ($entry.Count -ne 1) { throw "No matching Debug APK for $abi" }
    $apk = Join-Path 'app/build/outputs/apk/debug' $entry[0].outputFile
    & $Adb -P $AdbPort -s $Serial install -r $apk
    if ($LASTEXITCODE) { throw 'Layout gate: app installation failed.' }
    & $Adb -P $AdbPort -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE) { throw 'Layout gate: test installation failed.' }
    $classes = 'com.kingzcheung.xime.ui.LayoutGeometryGateTest,com.kingzcheung.xime.ui.PanelLayoutAuditTest,com.kingzcheung.xime.ui.EditKeyboardLayoutTest,com.kingzcheung.xime.ui.ExpandedCandidateLayoutTest,com.kingzcheung.xime.ui.OfflineEditionTest,com.kingzcheung.xime.ui.HandwritingInkAppearanceTest,com.kingzcheung.xime.ui.VisualStyleIntegrationTest,com.kingzcheung.xime.ui.IconAppearanceTest,com.kingzcheung.xime.ui.VisualLabResourcesTest,com.kingzcheung.xime.ui.LanguageSettingsOrderTest,com.kingzcheung.xime.ui.SpeechSettingsLayoutGateTest,com.kingzcheung.xime.ui.SettingsSecondaryLayoutGateTest,com.kingzcheung.xime.ui.MenuSettingsShortcutTest,com.kingzcheung.xime.ui.HandwritingToggleTest,com.kingzcheung.xime.ui.InputModeOrderTest,com.kingzcheung.xime.ui.FixedKeyboardResizeTest,com.kingzcheung.xime.ui.ResizeControlsContrastTest,com.kingzcheung.xime.ui.SettingsShortcutActivityTest,com.kingzcheung.xime.ui.LearningDataTest'
    $classes += ',com.kingzcheung.xime.ui.VerificationCodeLayoutTest,com.kingzcheung.xime.settings.KeyboardPaletteUpgradeTest,com.kingzcheung.xime.ui.KeyColorRolesTest'
    $expectedTests = 0
    foreach ($testClass in $classes.Split(',')) {
        $testSource = Join-Path 'app/src/androidTest/java' (($testClass -replace '\.', '/') + '.kt')
        if (-not (Test-Path -LiteralPath $testSource)) { throw "Layout gate test source missing: $testSource" }
        $expectedTests += @(Select-String -LiteralPath $testSource -Pattern '^\s*@Test\b').Count
    }
    $report = & $Adb -P $AdbPort -s $Serial shell am instrument -w -e class $classes com.cyletix.cyime.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Out-String
    $code = $LASTEXITCODE
    $report | Set-Content -Encoding utf8 "$dir/result.txt"
    Write-Output $report
    $success = [regex]::Match($report, 'OK \((\d+) tests\)')
    if ($code -ne 0 -or -not $success.Success -or [int]$success.Groups[1].Value -lt $expectedTests -or $report -match 'FAILURES|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Layout gate FAILED. Fix reported element dimensions before packaging. Report: $dir/result.txt"
    }
    $resourceOutput = 'tools/visual-lab/android-resources.json'
    if (& git status --porcelain -- $resourceOutput) {
        $resourceOutput = Join-Path $dir 'android-resources.json'
        Write-Warning "Preserving local visual-lab resources; exporting this run to $resourceOutput"
    }
    & $Adb -P $AdbPort -s $Serial pull /sdcard/Android/data/com.cyletix.cyime/files/visual-lab-resources.json $resourceOutput
    if ($LASTEXITCODE) { throw 'Preview resources export failed; do not fall back to invented icons or colors.' }
    [pscustomobject]@{ version=$entry[0].versionName; bundledModels=($entry[0].outputFile -match '-full-'); apkSha256=(Get-FileHash $apk -Algorithm SHA256).Hash;
        serial=$Serial; tests=[int]$success.Groups[1].Value; checkedAt=(Get-Date).ToString('o'); classes=$classes } |
        ConvertTo-Json | Set-Content -Encoding utf8 "$dir/passed.json"
} finally { Pop-Location }
