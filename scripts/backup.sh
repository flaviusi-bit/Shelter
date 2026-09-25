#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="\${BACKUP_DIR:-./backups}"
DOCUMENTS_PATH="\${SHELTER_DOCUMENTS_PATH:-./data/documents}"
PGHOST="\${PGHOST:-localhost}"
PGPORT="\${PGPORT:-5432}"
PGDATABASE="\${PGDATABASE:-\${POSTGRES_DB:-shelter}}"
PGUSER="\${PGUSER:-\${POSTGRES_USER:-shelter}}"
PGPASSWORD="\${PGPASSWORD:-\${POSTGRES_PASSWORD:-change-me}}"

timestamp="\$(date -u +%Y%m%dT%H%M%SZ)"
target="\${BACKUP_DIR}/\${timestamp}"
mkdir -p "\${target}"

export PGPASSWORD
echo "Backing up PostgreSQL database '\${PGDATABASE}'..."
pg_dump --format=custom --file="\${target}/database.dump" --host="\${PGHOST}" --port="\${PGPORT}" --username="\${PGUSER}" "\${PGDATABASE}"

echo "Backing up documents from '\${DOCUMENTS_PATH}'..."
if [ -d "\${DOCUMENTS_PATH}" ]; then
  tar -C "\${DOCUMENTS_PATH}" -czf "\${target}/documents.tar.gz" .
else
  echo "Document directory does not exist; creating an empty archive."
  tar -czf "\${target}/documents.tar.gz" -T /dev/null
fi

(cd "\${target}" && sha256sum database.dump documents.tar.gz > SHA256SUMS)

echo "Backup completed: \${target}"
ls -lh "\${target}"
