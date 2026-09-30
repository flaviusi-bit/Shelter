# Shelter Management System

A web application for managing an animal shelter: animals, medical records, treatments, medication administrations, vaccinations, deworming, veterinary visits, documents, users, tasks and reminders.

## Repository

GitHub: `flaviusi-bit/Shelter`

Current development branch: `initial-architecture`

## Stack

- Frontend: React + TypeScript + PWA
- Backend: Spring Boot + Java 21
- Database: PostgreSQL 16
- Database migrations: Flyway
- Local infrastructure: Docker Compose
- CI: GitHub Actions
- Deployment target: Docker Desktop on Windows 11

## Local development

Copy `.env.example` to `.env` and adjust values if required.

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Start the backend:

```bash
cd backend
mvn spring-boot:run
```

The API runs on port 8080.

Start the frontend:

```bash
cd frontend
npm install
npm run dev
```

The development UI runs on port 5173.

## Architecture status

The MVP application, medical workflows, authentication, role-based access control, task/reminder flows and containerized deployment stack are implemented.

The supported local deployment target is Windows 11 with Docker Desktop and Docker Compose:
- Nginx serves the PWA and reverse-proxies `/api/*` to the backend.
- Spring Boot provides the API and stores medical documents on persistent filesystem storage.
- PostgreSQL 16 provides persistent application data.
- The backend and PostgreSQL ports are bound to localhost; the frontend is exposed through the configured frontend port (80 by default).

No VPS or public domain is required for the Windows 11 deployment.


## Current MVP modules

- Animal registry with unique animal codes, photos by URL, identity, intake and location data
- Authentication and shelter roles
- Treatment plans and generated administration schedules
- Treatment administration audit trail using the authenticated user
- Medical history, vaccinations and deworming
- Medical document records with authenticated filesystem-backed uploads
- Operational dashboard with treatment administration queue
- Tasks/reminders with priorities, due dates, assignment and completion/skip tracking
- Responsive PWA-oriented frontend

### Current task/reminder note

Tasks can be created manually and completed or skipped from the dashboard. Automatic task generation from vaccination/deworming due dates is implemented and runs through the scheduled medical-reminder synchronization.


### Document file storage

Medical documents can now be uploaded directly from the PWA. The backend stores file bytes on the configured filesystem path (SHELTER_DOCUMENTS_PATH, default ./data/documents) and keeps document metadata in PostgreSQL. Uploads are limited to 25 MB and restricted to common PDF/image/document MIME types. File access is authenticated and scoped to the animal that owns the document.

For VPS deployment, mount the document directory on persistent storage and include it in the backup strategy.

### Production backup and restore

PostgreSQL data and uploaded documents use separate persistent Docker volumes. The repository also includes disaster-recovery scripts under scripts/.

Create a backup from the project root:

~~~bash
chmod +x scripts/backup.sh scripts/restore.sh
./scripts/backup.sh
~~~

A backup contains:
- a PostgreSQL custom-format dump
- a compressed archive of uploaded documents
- SHA-256 checksums

Backups are timestamped in ./backups/<UTC timestamp>/ by default. Set BACKUP_DIR to a separate mounted disk or remote backup target.

Restore is deliberately destructive and requires an explicit confirmation:

~~~bash
./scripts/restore.sh --confirm ./backups/<UTC timestamp>
~~~

Stop the application before restore. The restore replaces the database contents and document directory. Keep backups outside the VPS whenever possible and periodically perform a test restore.

### Docker Compose deployment shape

`docker compose up -d --build` starts the Nginx frontend, Spring Boot backend and PostgreSQL.

- Frontend: `http://localhost/` by default; change `FRONTEND_PORT` if required.
- Backend API: bound to `127.0.0.1:${BACKEND_PORT:-8080}` for local administration/troubleshooting.
- PostgreSQL: bound to `127.0.0.1:${POSTGRES_PORT:-5432}`.
- PostgreSQL data persists in `shelter-postgres-data`.
- Uploaded documents persist in `shelter-documents-data`.
- Backup output defaults to `./backups` and is mounted into the backend at `/app/data/backups`.

For the Windows 11 deployment, keep the Docker volumes and backup directory on reliable local storage and copy completed backups to a separate location such as OneDrive. The PowerShell wrapper in `scripts/windows-shelter-backup.ps1` provides the Windows backup/restore workflow.
