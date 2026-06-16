Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")

Dim scriptDir
scriptDir = FSO.GetParentFolderName(WScript.ScriptFullName)

WshShell.Run Chr(34) & scriptDir & "\run_agent.bat" & Chr(34), 0

Set FSO = Nothing
Set WshShell = Nothing
