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

latest=""
while IFS= read -r backup_name; do
  if [[ "$backup_name" =~ ^[0-9]{8}T[0-9]{6}Z$ ]]; then
    if [ -z "$latest" ] || [[ "$backup_name" > "$latest" ]]; then
      latest="$backup_name"
    fi
  fi
done < <(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%f\n')

if [ -z "$latest" ] || [ ! -f "$BACKUP_DIR/$latest/SHA256SUMS" ]; then
  echo "Backup completed but no checksum manifest was found."
  exit 1
fi

echo "Verifying backup integrity: $BACKUP_DIR/$latest"
(cd "$BACKUP_DIR/$latest" && sha256sum --check SHA256SUMS)

echo "Removing generated backup folders older than ${RETENTION_DAYS} days..."
while IFS= read -r backup_path; do
  backup_name="$(basename "$backup_path")"
  if [[ "$backup_name" =~ ^[0-9]{8}T[0-9]{6}Z$ ]]; then
    if [ "$(find "$backup_path" -prune -mtime +"$RETENTION_DAYS" -print)" ]; then
      echo "$backup_path"
      rm -rf -- "$backup_path"
    fi
  fi
done < <(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%p\n')

echo "OneDrive backup completed and verified with ${RETENTION_DAYS}-day retention."
