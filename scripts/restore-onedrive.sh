#!/usr/bin/env bash
set -euo pipefail

if [ -z "${ONEDRIVE_BACKUP_DIR:-}" ]; then
  echo "ONEDRIVE_BACKUP_DIR is required."
  exit 2
fi

if [ "${1:-}" != "--confirm" ] || [ -z "${2:-}" ]; then
  echo "Usage: $0 --confirm <backup-folder-name>"
  exit 2
fi

BACKUP_NAME="${2}"
if [[ ! "${BACKUP_NAME}" =~ ^[0-9]{8}T[0-9]{6}Z$ ]]; then
  echo "Invalid backup folder name." >&2
  exit 2
fi

export BACKUP_DIR="${ONEDRIVE_BACKUP_DIR}"
exec "$(dirname "$0")/restore.sh" --confirm "${BACKUP_DIR}/${BACKUP_NAME}"
