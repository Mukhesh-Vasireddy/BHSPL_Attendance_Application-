@echo off
cd /d "%~dp0"
set "JAVA_EXE=..\..\jdk17.0.18_9\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=java"
"%JAVA_EXE%" -jar ..\target\attendance-sync-agent.jar > ..\target\sync_agent_startup.log 2>&1
