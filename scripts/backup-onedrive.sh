#!/usr/bin/env bash
set -euo pipefail

if [ -z "${ONEDRIVE_BACKUP_DIR:-}" ]; then
  echo "ONEDRIVE_BACKUP_DIR is required."
  exit 2
fi

if [ -L "${ONEDRIVE_BACKUP_DIR}" ] || [ ! -d "${ONEDRIVE_BACKUP_DIR}" ]; then
  echo "ONEDRIVE_BACKUP_DIR must be an existing directory and must not be a symlink." >&2
  exit 2
fi
ONEDRIVE_BACKUP_DIR="$(cd -- "${ONEDRIVE_BACKUP_DIR}" && pwd -P)"

export BACKUP_DIR="${ONEDRIVE_BACKUP_DIR}"
exec "$(dirname "$0")/backup.sh"
