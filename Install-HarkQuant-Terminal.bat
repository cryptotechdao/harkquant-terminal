@echo off
rem HarkQuant Terminal - one-time install: Python venv, requirements, desktop shortcut (same pattern as Hark Titans).
setlocal
cd /d "%~dp0"
set "PY=python"
where py >nul 2>nul && set "PY=py -3"
%PY% -c "import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)"
if errorlevel 1 goto nopython

if not exist ".venv\Scripts\python.exe" (
  echo Creating virtual environment .venv ...
  %PY% -m venv .venv
)
if not exist ".venv\Scripts\python.exe" goto nopython
".venv\Scripts\python.exe" -m pip install --upgrade pip
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 goto pipfail
if not exist "state" mkdir "state"

echo Creating desktop shortcut ...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$d=[Environment]::GetFolderPath('Desktop'); $w=New-Object -ComObject WScript.Shell; $root='%~dp0';" ^
  "$s=$w.CreateShortcut((Join-Path $d 'HarkQuant Terminal.lnk')); $s.TargetPath=(Join-Path $root 'Start-HarkQuant-Terminal.bat'); $s.WorkingDirectory=$root; $s.IconLocation=(Join-Path $root 'static\icons\harkquant.ico'); $s.WindowStyle=7; $s.Description='HarkQuant Terminal - market news and analysis'; $s.Save()"

echo Checking live sources once ...
".venv\Scripts\python.exe" -m hqterm check
echo.
echo Done. Double-click "HarkQuant Terminal" on the desktop.
echo In the Edge window use  ...  menu ^> Apps ^> Install this site as an app  to pin it like a Windows app.
pause
exit /b 0

:nopython
echo Python 3.11 or newer is required: https://www.python.org/downloads/windows/  (tick "Add python.exe to PATH")
pause
exit /b 1

:pipfail
echo pip install failed - check the messages above (internet connection / proxy).
pause
exit /b 1
