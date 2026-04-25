@echo off
set "SCRIPT_PATH=%~dp0invisible_sync.vbs"
set "STARTUP_FOLDER=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "SHORTCUT_PATH=%STARTUP_FOLDER%\BHSPL_Attendance_Java_Sync.lnk"

echo Setting up automatic background synchronization...
echo Script: %SCRIPT_PATH%
echo.

powershell -Command "$WshShell = New-Object -ComObject WScript.Shell; $Shortcut = $WshShell.CreateShortcut('%SHORTCUT_PATH%'); $Shortcut.TargetPath = 'wscript.exe'; $Shortcut.Arguments = '\"%SCRIPT_PATH%\"'; $Shortcut.WorkingDirectory = '%~dp0'; $Shortcut.Save()"

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Background sync will now start automatically whenever you turn on your computer.
) else (
    echo [ERROR] Failed to set up autostart. Please run this script as Administrator.
)

echo.
echo Starting the background sync now...
wscript.exe "%SCRIPT_PATH%"

pause
