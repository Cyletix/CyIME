# 同步手写、词库和滑行练习。优先设置为登录及每天运行；计划任务不可用时设置当前用户登录启动项。
# 移除时删除计划任务「CyIME 手写样本」或当前用户启动文件夹中的「CyIME 贡献样本同步.lnk」。
param(
    [string]$Time = "23:30",
    [string]$Python = ""
)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$script = Join-Path $repo "tools\contrib\sync_contrib.py"
if (-not $Python) {
    $cmd = Get-Command python -ErrorAction SilentlyContinue
    if (-not $cmd) { throw "找不到 python，用 -Python 指定路径" }
    $Python = $cmd.Source
}
& $Python -c "import numpy, onnxruntime, modelscope_hub" 2>$null
if ($LASTEXITCODE -ne 0) { throw "$Python 缺 numpy、onnxruntime 或 modelscope-hub" }

# 用 pythonw 运行，不弹控制台窗口；输出由脚本自己写进日志
$pythonw = Join-Path (Split-Path $Python) "pythonw.exe"
if (-not (Test-Path $pythonw)) { throw "找不到 $pythonw" }
$log = "D:\CyIME-Data\handwriting_training\contrib\task.log"
New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null
$arguments = "-X utf8 `"$script`" --log `"$log`""
$shortcutPath = Join-Path ([Environment]::GetFolderPath("Startup")) "CyIME 贡献样本同步.lnk"
$taskName = "CyIME 手写样本"
try {
    $action = New-ScheduledTaskAction -Execute $pythonw -Argument $arguments -WorkingDirectory $repo
    $trigger = @((New-ScheduledTaskTrigger -Daily -At $Time), (New-ScheduledTaskTrigger -AtLogOn -User ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name)))
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 30) `
        -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings `
        -Description "同步手写、词库与滑行练习并回放手写" -Force -ErrorAction Stop | Out-Null
    if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction Stop)) { throw "未找到已注册的计划任务" }
} catch {
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = $pythonw
    $shortcut.Arguments = $arguments
    $shortcut.WorkingDirectory = $repo
    $shortcut.Save()
    if (-not (Test-Path -LiteralPath $shortcutPath)) { throw "未能创建登录启动项" }
    Write-Host "已设置为当前用户登录时同步，日志 $log"
    return
}
if (Test-Path -LiteralPath $shortcutPath) { Remove-Item -LiteralPath $shortcutPath }
Write-Host "已注册：登录及每天 $Time 运行，日志 $log"
