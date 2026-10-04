@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if not errorlevel 1 (
  py -3 "%~dp0scripts\auto-deliver.py" %*
  exit /b
)
where python >nul 2>nul
if not errorlevel 1 (
  python "%~dp0scripts\auto-deliver.py" %*
  exit /b
)
echo Python 3.10+ is required. The project's build log previously found Python 3.14.
exit /b 1
