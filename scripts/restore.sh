#!/usr/bin/env bash
set -euo pipefail

if [ "${1:-}" != "--confirm" ]; then
  echo "Usage: $0 --confirm <backup-directory>"
  exit 2
fi

BACKUP_DIR="${2:-}"
if [ -z "${BACKUP_DIR}" ]; then echo "Backup directory is required."; exit 2; fi
BACKUP_DIR="$(cd -- "${BACKUP_DIR}" 2>/dev/null && pwd -P)" || { echo "Backup directory is invalid."; exit 2; }
if [ ! -d "${BACKUP_DIR}" ] || [ ! -f "${BACKUP_DIR}/database.dump" ] || [ ! -f "${BACKUP_DIR}/documents.tar.gz" ]; then
  echo "Backup directory must contain database.dump and documents.tar.gz."
  exit 2
fi

DOCUMENTS_PATH="${SHELTER_DOCUMENTS_PATH:-./data/documents}"
mkdir -p "${DOCUMENTS_PATH}"
if [ -L "${DOCUMENTS_PATH}" ] || [ ! -d "${DOCUMENTS_PATH}" ]; then
  echo "Document directory must be a real directory and must not be a symlink." >&2
  exit 2
fi
DOCUMENTS_PATH="$(cd -- "${DOCUMENTS_PATH}" && pwd -P)"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-${POSTGRES_DB:-shelter}}"
PGUSER="${PGUSER:-${POSTGRES_USER:-shelter}}"
PGPASSWORD="${PGPASSWORD:-${POSTGRES_PASSWORD:-}}"
if [ -z "${PGPASSWORD}" ]; then
  echo "PGPASSWORD or POSTGRES_PASSWORD must be set."
  exit 2
fi
export PGPASSWORD

if [ -f "${BACKUP_DIR}/SHA256SUMS" ]; then
  (cd "${BACKUP_DIR}" && sha256sum --check SHA256SUMS)
fi

echo "Validating document archive contents..."
while IFS= read -r archive_entry; do
  archive_name="${archive_entry:1}"
  case "${archive_entry:0:1}" in
    l|h)
      echo "Document archive contains an unsupported link entry: ${archive_name}" >&2
      exit 2
      ;;
  esac
  if [[ "${archive_name}" = /* || "${archive_name}" == ../* || "${archive_name}" == */../* || "${archive_name}" == */.. ]]; then
    echo "Document archive contains an unsafe path: ${archive_name}" >&2
    exit 2
  fi
done < <(tar -tvzf "${BACKUP_DIR}/documents.tar.gz")

echo "WARNING: this replaces database '${PGDATABASE}' and document files in '${DOCUMENTS_PATH}'."
echo "Stop the application before continuing."
read -r -p "Type RESTORE to continue: " confirmation
if [ "${confirmation}" != "RESTORE" ]; then echo "Restore cancelled."; exit 1; fi

pg_restore --clean --if-exists --no-owner --host="${PGHOST}" --port="${PGPORT}" --username="${PGUSER}" --dbname="${PGDATABASE}" "${BACKUP_DIR}/database.dump"

find "${DOCUMENTS_PATH}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
tar -C "${DOCUMENTS_PATH}" -xzf "${BACKUP_DIR}/documents.tar.gz"

echo "Restore completed."
