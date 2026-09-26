param(
  [ValidateSet("backup","restore")][string]$Action = "backup",
  [string]$OneDriveBackupDir = "",
  [string]$BackupName = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OneDriveBackupDir)) {
  $OneDriveBackupDir = Join-Path $env:USERPROFILE "OneDrive\Shelter Management\Backups"
}
New-Item -ItemType Directory -Force -Path $OneDriveBackupDir | Out-Null

$root = Split-Path -Parent $PSScriptRoot
$env:ONEDRIVE_BACKUP_DIR = $OneDriveBackupDir

if ($Action -eq "backup") {
  & bash (Join-Path $root "scripts\backup-onedrive-retained.sh")
  if ($LASTEXITCODE -ne 0) { throw "Backup failed with exit code $LASTEXITCODE." }
  Write-Host "Shelter backup completed in $OneDriveBackupDir"
  exit 0
}

if ([string]::IsNullOrWhiteSpace($BackupName)) {
  throw "BackupName is required for restore."
}

$backupPath = Join-Path $OneDriveBackupDir $BackupName
if (-not (Test-Path (Join-Path $backupPath "SHA256SUMS"))) {
  throw "Backup checksum file not found: $backupPath"
}

Push-Location $backupPath
try {
  & sha256sum --check SHA256SUMS
  if ($LASTEXITCODE -ne 0) { throw "Backup integrity verification failed." }
} finally {
  Pop-Location
}

$confirmation = Read-Host "This will replace the local database and documents. Type RESTORE to continue"
if ($confirmation -ne "RESTORE") {
  Write-Host "Restore cancelled."
  exit 1
}

& bash (Join-Path $root "scripts\restore-onedrive.sh") --confirm $BackupName
if ($LASTEXITCODE -ne 0) { throw "Restore failed with exit code $LASTEXITCODE." }
Write-Host "Shelter restore completed."
