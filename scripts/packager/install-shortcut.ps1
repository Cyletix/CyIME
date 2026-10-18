# Add "CyIME 打包器" to the Start menu with the CyIME icon. Re-run after moving the repository or Python.
# Remove: delete "%APPDATA%\Microsoft\Windows\Start Menu\Programs\CyIME 打包器.lnk".
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$python = (& py -3 -c "import sys, pathlib; print(pathlib.Path(sys.executable).with_name('pythonw.exe'))").Trim()
if (-not (Test-Path -LiteralPath $python)) { throw "pythonw.exe not found: $python" }
$programs = [Environment]::GetFolderPath('Programs')
$link = Join-Path $programs 'CyIME 打包器.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($link)
$shortcut.TargetPath = $python
$shortcut.Arguments = '-B "' + (Join-Path $PSScriptRoot 'server.py') + '"'
$shortcut.WorkingDirectory = $root
$shortcut.IconLocation = (Join-Path $PSScriptRoot 'cyime-packager.ico') + ',0'
$shortcut.Description = 'CyIME Android 打包器'
$shortcut.Save()
Write-Host "Start menu shortcut: $link"
