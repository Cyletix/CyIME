<# Export launcher PNGs from the same Android renderer used by the keyboard logo.
   Install the current Debug APK and androidTest APK first. This does not change icon settings. #>
[CmdletBinding()]
param([string]$Serial = 'emulator-5554', [int]$Port = 5037,
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$resources = Join-Path $repo 'app/src/main/res'
$drawable = Join-Path $resources 'drawable-nodpi'
$adaptive = Join-Path $resources 'mipmap-anydpi-v26'
New-Item -ItemType Directory -Force -Path $drawable,$adaptive | Out-Null
$result = & $Adb -P $Port -s $Serial shell am instrument -w -r -e class 'com.kingzcheung.xime.ui.IconAppearanceTest#exportLauncherAssetsFromProductionGenerator' 'com.cyletix.cyime.test/androidx.test.runner.AndroidJUnitRunner'
if (($result -join "`n") -notmatch 'OK \(1 test\)') { throw ($result -join "`n") }
foreach ($style in @('neon','glass','facet','frost')) {
    foreach ($suffix in @('','_foreground')) {
        $name = "cyime_$style$suffix"
        & $Adb -P $Port -s $Serial pull "/sdcard/Android/data/com.cyletix.cyime/files/$name.png" (Join-Path $drawable "$name.png")
        if ($LASTEXITCODE) { throw "Failed to export $name" }
    }
    $color = if ($style -eq 'frost') { '#F3EFFA' } else { '#14111F' }
    @"
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/cyime_${style}_background" />
    <foreground android:drawable="@drawable/cyime_${style}_foreground" />
</adaptive-icon>
"@ | Set-Content -Encoding utf8 (Join-Path $adaptive "cyime_$style.xml")
    @"
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="$color" />
</shape>
"@ | Set-Content -Encoding utf8 (Join-Path $resources "drawable/cyime_${style}_background.xml")
}
Write-Host 'Exported four icon materials from the production generator. Rebuild APK to package them.'
