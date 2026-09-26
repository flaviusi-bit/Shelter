# OneDrive backup and restore

Completed Shelter Management backup folders can live inside a OneDrive-synchronised directory.

Create: `Shelter Management/Backups`

Set `ONEDRIVE_BACKUP_DIR` to its local path.

Example with Windows/Git Bash:
`export ONEDRIVE_BACKUP_DIR="/c/Users/<user>/OneDrive/Shelter Management/Backups"`

Create a backup:
`./scripts/backup-onedrive.sh`

Each timestamped folder contains `database.dump`, `documents.tar.gz`, and `SHA256SUMS`.

Restore on a replacement computer:
1. Install Shelter Management and its local PostgreSQL/Docker components.
2. Sign in to OneDrive and make the backup folder available locally.
3. Set `ONEDRIVE_BACKUP_DIR`.
4. Choose a timestamped backup folder.
5. Stop Shelter Management.
6. Run `./scripts/restore-onedrive.sh --confirm <backup-folder-name>`.

The restore verifies SHA-256 checksums, restores PostgreSQL, and restores uploaded documents.

Do not put PostgreSQL's live data directory inside OneDrive. Only completed backup artifacts should be synchronised.

The future Windows installer can expose these operations through a graphical Backup/Restore wizard.
