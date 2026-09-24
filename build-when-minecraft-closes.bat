@echo off
setlocal
title Kung: rebuild whenever Minecraft closes

rem Minecraft runs as javaw.exe; Gradle and the launcher do not.
set "GAME=javaw.exe"
set "BUILD=%~dp0build-and-copy-jar_26.1.2.bat"

if not exist "%BUILD%" (
    echo Build script not found:
    echo %BUILD%
    pause
    exit /b 1
)

echo Watching for Minecraft. Close this window to stop.
echo.

:cycle
call :waitFor running
call :waitFor closed
echo.
echo [%time:~0,8%] Minecraft closed. Building...
rem The build script pauses on its own; stdin from NUL lets those pauses fall through.
call "%BUILD%" <nul
if errorlevel 1 (
    call :notify "Kung build failed" "See the window for the error."
) else (
    call :notify "Kung jar ready" "Installed. Start Minecraft to use it."
)
echo [%time:~0,8%] Waiting for the next Minecraft session...
goto cycle

:waitFor
tasklist /FI "IMAGENAME eq %GAME%" | find /I "%GAME%" >nul
if "%~1"=="running" (if errorlevel 1 (timeout /t 2 /nobreak >nul & goto waitFor))
if "%~1"=="closed" (if not errorlevel 1 (timeout /t 2 /nobreak >nul & goto waitFor))
exit /b 0

:notify
echo [%time:~0,8%] %~1
powershell -NoProfile -WindowStyle Hidden -Command ^
 "Add-Type -AssemblyName System.Windows.Forms;" ^
 "$icon = New-Object System.Windows.Forms.NotifyIcon;" ^
 "$icon.Icon = [System.Drawing.SystemIcons]::Information;" ^
 "$icon.Visible = $true;" ^
 "$icon.ShowBalloonTip(5000, '%~1', '%~2', [System.Windows.Forms.ToolTipIcon]::Info);" ^
 "Start-Sleep -Seconds 6; $icon.Dispose()" >nul 2>nul
exit /b 0
