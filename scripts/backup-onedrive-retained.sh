#!/usr/bin/env bash
set -euo pipefail

if [ -z "${ONEDRIVE_BACKUP_DIR:-}" ]; then
  echo "ONEDRIVE_BACKUP_DIR is required."
  exit 2
fi

RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
if ! [[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]] || [ "$RETENTION_DAYS" -lt 1 ]; then
  echo "BACKUP_RETENTION_DAYS must be a positive integer."
  exit 2
fi

export BACKUP_DIR="$ONEDRIVE_BACKUP_DIR"
"$(dirname "$0")/backup.sh"

latest="$(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -nr | head -n 1 | cut -d' ' -f2-)"
if [ -z "$latest" ] || [ ! -f "$latest/SHA256SUMS" ]; then
  echo "Backup completed but no checksum manifest was found."
  exit 1
fi

echo "Verifying backup integrity: $latest"
(cd "$latest" && sha256sum --check SHA256SUMS)

echo "Removing backup folders older than ${RETENTION_DAYS} days..."
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -mtime +"$RETENTION_DAYS" -print -exec rm -rf {} +

echo "OneDrive backup completed and verified with ${RETENTION_DAYS}-day retention."
