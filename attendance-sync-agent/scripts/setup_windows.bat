@echo off
setlocal enabledelayedexpansion

set "SCRIPT_PATH=%~dp0invisible_agent.vbs"
set "STARTUP_FOLDER=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "SHORTCUT_PATH=%STARTUP_FOLDER%\BHSPL_Attendance_SyncAgent.lnk"

echo Setting up background SyncAgent autostart on Windows...
echo Script Location: %SCRIPT_PATH%
echo Shortcut Destination: %SHORTCUT_PATH%
echo.

powershell -Command "$WshShell = New-Object -ComObject WScript.Shell; $Shortcut = $WshShell.CreateShortcut('%SHORTCUT_PATH%'); $Shortcut.TargetPath = 'wscript.exe'; $Shortcut.Arguments = '\"%SCRIPT_PATH%\"'; $Shortcut.WorkingDirectory = '%~dp0'; $Shortcut.Save()"

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Windows autostart configured. SyncAgent will start headlessly on user login.
    echo Starting the background agent now...
    wscript.exe "%SCRIPT_PATH%"
) else (
    echo [ERROR] Failed to configure Windows autostart.
)

pause
