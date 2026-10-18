@echo off
setlocal
rem CyIME packager: no arguments opens the GUI; with arguments it is the CLI (packager.cmd build --help).
chcp 65001 >nul
set "PYTHONIOENCODING=utf-8"
if "%~1"=="" goto gui
where py >nul 2>nul
if not errorlevel 1 (
  py -3 -B "%~dp0scripts\packager\cyime_packager.py" %*
  exit /b %errorlevel%
)
python -B "%~dp0scripts\packager\cyime_packager.py" %*
exit /b %errorlevel%
:gui
where pyw >nul 2>nul
if not errorlevel 1 (
  start "" pyw -3 -B "%~dp0scripts\packager\server.py"
  exit /b 0
)
start "" pythonw -B "%~dp0scripts\packager\server.py"
