@echo off
set "JAVA_HOME=%~dp0jdk17.0.18_9"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Cleaning and recompiling project...
call "%~dp0mvnw.cmd" clean compile

echo Starting BHSPL Attendance Management System...
echo.

"%~dp0mvnw.cmd" exec:java -D"exec.mainClass"="com.bhspl.Main"

pause
