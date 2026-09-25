# Backup and restore

The scripts in this directory are intended for VPS disaster recovery.

## Backup

Requirements:
- PostgreSQL client tools (\`pg_dump\`, \`pg_restore\`)
- \`tar\`, \`gzip\`, \`sha256sum\`
- network access to PostgreSQL

From the project root:

~~~bash
chmod +x scripts/backup.sh scripts/restore.sh
./scripts/backup.sh
~~~

By default backups are written to \`./backups/<UTC timestamp>/\`.

For Docker Compose, run the backup from a shell that can reach the published PostgreSQL port, or override:

~~~bash
PGHOST=localhost PGPORT=5432 PGDATABASE=shelter PGUSER=shelter PGPASSWORD='your-password' ./scripts/backup.sh
~~~

Set \`BACKUP_DIR\` to place backups on a separate disk or mounted backup target.

## Restore

Stop the application first. Then:

~~~bash
./scripts/restore.sh --confirm ./backups/<UTC timestamp>
~~~

The script verifies checksums when \`SHA256SUMS\` exists, replaces the database contents, and replaces the document directory contents. Type \`RESTORE\` when prompted.

**Important:** keep backups outside the application container and, ideally, on separate storage. A backup on the same VPS does not protect against VPS disk failure. Test restores periodically.
