@echo off
rem HarkQuant Terminal - pull the latest version from the private GitHub repo and refresh packages.
rem Needs Git for Windows (https://git-scm.com). Your state\ folder (settings, API keys, news database) is never touched.
setlocal
cd /d "%~dp0"
where git >nul 2>nul || (echo Git is not installed. Install Git for Windows first. & pause & exit /b 1)
if not exist ".git" (
  echo This folder is not a git copy. Set it up once with:
  echo   git clone https://github.com/cryptotechdao/harkquant-terminal.git
  pause & exit /b 1
)
git pull --ff-only || (echo Update failed. & pause & exit /b 1)
if exist ".venv\Scripts\python.exe" ".venv\Scripts\python.exe" -m pip install -q -r requirements.txt
echo HarkQuant Terminal is up to date. Restart it with Start-HarkQuant-Terminal.bat.
pause
endlocal
