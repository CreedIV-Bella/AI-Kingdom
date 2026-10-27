<#
AI Kingdoms auto-start installer (non-admin).
Creates a Windows Startup shortcut that launches the local watcher after login.
Watcher waits for Minecraft. No model or jar download, no registry mutation.
Run from voice/install_autostart.cmd.
#>
param([switch]$Remove)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$PythonW = Join-Path $Root "voice\.venv\Scripts\pythonw.exe"
$Watcher = Join-Path $Root "voice\auto_start.py"
$Startup = [Environment]::GetFolderPath("Startup")
$ShortcutPath = Join-Path $Startup "AI Kingdoms Minecraft.lnk"

if ($Remove) {
    if (Test-Path -LiteralPath $ShortcutPath) {
        Remove-Item -LiteralPath $ShortcutPath -Force
        Write-Host "AI Kingdoms auto-start shortcut removed."
    } else {
        Write-Host "There is no AI Kingdoms auto-start shortcut to remove."
    }
    Write-Host "An already running watcher will stop when you log out of Windows."
    exit 0
}

if (-not (Test-Path -LiteralPath $PythonW)) {
    throw "Missing $PythonW. Create the voice venv before enabling auto-start."
}
if (-not (Test-Path -LiteralPath $Watcher)) {
    throw "Missing $Watcher. Run git pull first."
}

$Shell = New-Object -ComObject WScript.Shell
$Shortcut = $Shell.CreateShortcut($ShortcutPath)
$Shortcut.TargetPath = $PythonW
$Shortcut.Arguments = '"' + $Watcher + '"'
$Shortcut.WorkingDirectory = $Root
$Shortcut.Description = "Starts Ollama and AI Kingdoms voice only when Minecraft opens"
$Shortcut.Save()

Write-Host "AI Kingdoms auto-start installed for your Windows user."
Write-Host "On next login it will wait for a CurseForge Minecraft client."
Write-Host "Logs: $env:USERPROFILE\AI-Kingdoms\auto-start.log"
Write-Host "You may keep launching Minecraft normally from CurseForge."
Start-Process -FilePath $PythonW -ArgumentList ('"' + $Watcher + '"') -WorkingDirectory $Root
Write-Host "Watcher started now as well."
