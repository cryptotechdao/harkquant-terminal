@echo off
rem HarkQuant Terminal - start the server and open it as an Edge app window (same pattern as Hark Titans).
rem   Start-HarkQuant-Terminal.bat            http://127.0.0.1:8702/
rem   Start-HarkQuant-Terminal.bat lan        also reachable from your phone on the same Wi-Fi (binds 0.0.0.0, needs a token)
setlocal
cd /d "%~dp0"
set "PORT=8702"
set "HOST=127.0.0.1"
set "URL=http://127.0.0.1:%PORT%/"
if not exist ".venv\Scripts\python.exe" goto noinstall
call ".venv\Scripts\activate.bat"

if /i "%~1"=="lan" (
  set "HOST=0.0.0.0"
  if not exist "state" mkdir "state"
  if not exist "state\hqt_token.txt" python -c "import secrets; open('state/hqt_token.txt','w').write(secrets.token_urlsafe(24))"
)
if /i "%~1"=="lan" set /p HQT_TOKEN=<"state\hqt_token.txt"
if /i "%~1"=="lan" echo LAN mode: settings/keys changes need the token in state\hqt_token.txt

rem ---- already running? just open the window
powershell -NoProfile -Command "try { Invoke-WebRequest -UseBasicParsing '%URL%health' -TimeoutSec 2 | Out-Null; exit 0 } catch { exit 1 }"
if not errorlevel 1 goto open

start "HarkQuant Terminal" /min cmd /k python -m hqterm serve --host %HOST% --port %PORT%

echo Waiting for HarkQuant Terminal on %URL% ...
set /a TRIES=0
:wait
powershell -NoProfile -Command "try { Invoke-WebRequest -UseBasicParsing '%URL%health' -TimeoutSec 2 | Out-Null; exit 0 } catch { exit 1 }"
if not errorlevel 1 goto open
set /a TRIES+=1
if %TRIES% GEQ 90 goto timeout
timeout /t 2 /nobreak >nul
goto wait

:open
set "EDGE=msedge"
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" set "EDGE=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" set "EDGE=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"
start "" "%EDGE%" --app=%URL%
echo Terminal opened. Close the minimized "HarkQuant Terminal" console window to stop the server.
endlocal
exit /b 0

:timeout
echo The server did not answer within 3 minutes. Check the "HarkQuant Terminal" console window for errors.
pause
exit /b 1

:noinstall
echo HarkQuant Terminal is not installed yet: run Install-HarkQuant-Terminal.bat first.
pause
exit /b 1
