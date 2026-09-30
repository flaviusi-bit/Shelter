#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
DOCUMENTS_PATH="${SHELTER_DOCUMENTS_PATH:-./data/documents}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-${POSTGRES_DB:-shelter}}"
PGUSER="${PGUSER:-${POSTGRES_USER:-shelter}}"
PGPASSWORD="${PGPASSWORD:-${POSTGRES_PASSWORD:-}}"

if [ -z "${PGPASSWORD}" ]; then
  echo "PGPASSWORD or POSTGRES_PASSWORD must be set." >&2
  exit 1
fi

mkdir -p "${BACKUP_DIR}"
while :; do
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  target="${BACKUP_DIR}/${timestamp}"
  if mkdir "${target}" 2>/dev/null; then
    break
  fi
  sleep 1
done

export PGPASSWORD
if [ -L "${DOCUMENTS_PATH}" ] || [ ! -d "${DOCUMENTS_PATH}" ]; then
  echo "Document directory must exist and must not be a symlink." >&2
  exit 2
fi
DOCUMENTS_PATH="$(cd -- "${DOCUMENTS_PATH}" && pwd -P)"
echo "Backing up PostgreSQL database '${PGDATABASE}'..."
pg_dump --format=custom --file="${target}/database.dump" --host="${PGHOST}" --port="${PGPORT}" --username="${PGUSER}" "${PGDATABASE}"

echo "Backing up documents from '${DOCUMENTS_PATH}'..."
if [ -d "${DOCUMENTS_PATH}" ]; then
  tar -C "${DOCUMENTS_PATH}" -czf "${target}/documents.tar.gz" .
else
  echo "Document directory does not exist; creating an empty archive."
  tar -czf "${target}/documents.tar.gz" -T /dev/null
fi

(cd "${target}" && sha256sum database.dump documents.tar.gz > SHA256SUMS)

echo "Backup completed: ${target}"
ls -lh "${target}"
