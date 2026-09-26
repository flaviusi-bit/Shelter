#!/usr/bin/env bash
set -euo pipefail

if [ -z "${ONEDRIVE_BACKUP_DIR:-}" ]; then
  echo "ONEDRIVE_BACKUP_DIR is required."
  exit 2
fi

export BACKUP_DIR="${ONEDRIVE_BACKUP_DIR}"
exec "$(dirname "$0")/backup.sh"
