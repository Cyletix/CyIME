@echo off
setlocal
set "CYIME_DELIVERY_ROOT=%~dp0."
for /f "delims=" %%I in ('git -C "%~dp0." config --local --get cyime.integrationRoot 2^>nul') do set "CYIME_DELIVERY_ROOT=%%I"
if not exist "%CYIME_DELIVERY_ROOT%\scripts\auto-deliver.py" (
  echo CyIME delivery entry is missing in "%CYIME_DELIVERY_ROOT%".
  echo Check the repository's cyime.integrationRoot setting.
  exit /b 1
)
where py >nul 2>nul
if not errorlevel 1 goto python_launcher
where python >nul 2>nul
if not errorlevel 1 goto python_direct
echo Python 3.10+ is required.
exit /b 1
:python_launcher
py -3 -B "%CYIME_DELIVERY_ROOT%\scripts\auto-deliver.py" --entry-root "%~dp0." %*
exit /b %errorlevel%
:python_direct
python -B "%CYIME_DELIVERY_ROOT%\scripts\auto-deliver.py" --entry-root "%~dp0." %*
exit /b %errorlevel%
