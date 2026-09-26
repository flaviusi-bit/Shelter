param(
  [string]$TaskName = "Shelter Management - Daily Backup",
  [string]$ScriptPath = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ScriptPath)) {
  $ScriptPath = Join-Path (Split-Path -Parent $PSScriptRoot) "scripts\windows-shelter-backup.ps1"
}
if (-not (Test-Path $ScriptPath)) {
  throw "Backup script not found: $ScriptPath"
}

$argument = '-NoProfile -ExecutionPolicy Bypass -File "' + $ScriptPath + '" -Action backup'
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $argument
$trigger = New-ScheduledTaskTrigger -Daily -At 3:00AM
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal -Description "Daily Shelter Management backup to the OneDrive backup folder." -Force | Out-Null
Write-Host "Scheduled task '$TaskName' installed. It runs daily at 03:00 for the current Windows user."
