# 注册 Windows 计划任务「CyIME 手写样本」：每天 23:30 运行 tools/handwriting_training/contrib_ingest.py，
# 电脑当时没开就在下次开机后补跑。只读样本所在目录，结果写 D:\CyIME-Data\handwriting_training\contrib\。
# 删除任务：Unregister-ScheduledTask -TaskName "CyIME 手写样本" -Confirm:$false
param(
    [string]$Time = "23:30",
    [string]$Python = ""
)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$script = Join-Path $repo "tools\handwriting_training\contrib_ingest.py"
if (-not $Python) {
    $cmd = Get-Command python -ErrorAction SilentlyContinue
    if (-not $cmd) { throw "找不到 python，用 -Python 指定路径" }
    $Python = $cmd.Source
}
& $Python -c "import numpy, onnxruntime" 2>$null
if ($LASTEXITCODE -ne 0) { throw "$Python 缺 numpy 或 onnxruntime" }

# 用 pythonw 运行，不弹控制台窗口；输出由脚本自己写进日志
$pythonw = Join-Path (Split-Path $Python) "pythonw.exe"
if (-not (Test-Path $pythonw)) { throw "找不到 $pythonw" }
$log = "D:\CyIME-Data\handwriting_training\contrib\task.log"
New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null
$action = New-ScheduledTaskAction -Execute $pythonw -Argument "-X utf8 `"$script`" --log `"$log`"" -WorkingDirectory $repo
$trigger = New-ScheduledTaskTrigger -Daily -At $Time
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 30) `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName "CyIME 手写样本" -Action $action -Trigger $trigger -Settings $settings `
    -Description "收集用户贡献的手写样本并回放（tools/handwriting_training/contrib_ingest.py）" -Force | Out-Null
Write-Host "已注册：每天 $Time 运行，日志 $log"
