#!/usr/bin/env bash
set -euo pipefail

if [ "\${1:-}" != "--confirm" ]; then
  echo "Usage: $0 --confirm <backup-directory>"
  exit 2
fi

BACKUP_DIR="\${2:-}"
if [ -z "\${BACKUP_DIR}" ]; then echo "Backup directory is required."; exit 2; fi
if [ ! -f "\${BACKUP_DIR}/database.dump" ] || [ ! -f "\${BACKUP_DIR}/documents.tar.gz" ]; then
  echo "Backup directory must contain database.dump and documents.tar.gz."
  exit 2
fi

DOCUMENTS_PATH="\${SHELTER_DOCUMENTS_PATH:-./data/documents}"
PGHOST="\${PGHOST:-localhost}"
PGPORT="\${PGPORT:-5432}"
PGDATABASE="\${PGDATABASE:-\${POSTGRES_DB:-shelter}}"
PGUSER="\${PGUSER:-\${POSTGRES_USER:-shelter}}"
PGPASSWORD="\${PGPASSWORD:-\${POSTGRES_PASSWORD:-change-me}}"
export PGPASSWORD

if [ -f "\${BACKUP_DIR}/SHA256SUMS" ]; then
  (cd "\${BACKUP_DIR}" && sha256sum --check SHA256SUMS)
fi

echo "WARNING: this replaces database '\${PGDATABASE}' and document files in '\${DOCUMENTS_PATH}'."
echo "Stop the application before continuing."
read -r -p "Type RESTORE to continue: " confirmation
if [ "\${confirmation}" != "RESTORE" ]; then echo "Restore cancelled."; exit 1; fi

pg_restore --clean --if-exists --no-owner --host="\${PGHOST}" --port="\${PGPORT}" --username="\${PGUSER}" --dbname="\${PGDATABASE}" "\${BACKUP_DIR}/database.dump"

mkdir -p "\${DOCUMENTS_PATH}"
find "\${DOCUMENTS_PATH}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
tar -C "\${DOCUMENTS_PATH}" -xzf "\${BACKUP_DIR}/documents.tar.gz"

echo "Restore completed."
