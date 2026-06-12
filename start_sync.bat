@echo off
setlocal enabledelayedexpansion

:: Set java home and path
if "%JAVA_HOME%"=="" (
    set "JAVA_HOME=%~dp0jdk17.0.18_9"
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%~dp0"

echo [SYNC] Checking environment...
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK not found at %JAVA_HOME%. Please check the folder structure.
    pause
    exit /b 1
)

echo [SYNC] Ensuring project is compiled and dependencies are ready...
:: This ensures the driver is available in target/lib
call .\mvnw.cmd compile dependency:copy-dependencies -DoutputDirectory=target/lib -DincludeScope=runtime

if not exist "target\lib\mysql-connector-j-8.3.0.jar" (
    echo [ERROR] MySQL driver not found in target\lib. Check maven output above.
    pause
    exit /b 1
)

echo [SYNC] Starting background worker...
echo [SYNC] Logs: sync_stdout.log, Errors: sync_stderr.log
:: Run SyncWorker with all dependencies in target/lib
"%JAVA_HOME%\bin\java.exe" -cp "target/classes;target/lib/*" com.bhspl.SyncWorker > sync_stdout.log 2> sync_stderr.log

if %ERRORLEVEL% neq 0 (
    echo [SYNC] SyncWorker stopped with error code %ERRORLEVEL%.
    pause
)
