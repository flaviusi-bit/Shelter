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
- Production target: Docker on a VPS, with Cloudflare in front

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

The repository currently contains the project foundation and initial database schema. The next implementation step is the domain model and APIs for animals, treatment plans and medication administrations, followed by authentication, vaccinations, deworming, veterinary visits and attachments.

No VPS or domain is required during development. Production infrastructure will be added later without changing the application architecture.


## Current MVP modules

- Animal registry with unique animal codes, photos by URL, identity, intake and location data
- Authentication and shelter roles
- Treatment plans and generated administration schedules
- Treatment administration audit trail using the authenticated user
- Medical history, vaccinations and deworming
- Medical document records linked to external file URLs
- Operational dashboard with treatment administration queue
- Tasks/reminders with priorities, due dates, assignment and completion/skip tracking
- Responsive PWA-oriented frontend

### Current document storage note

Documents currently store a URL rather than binary file content. This keeps PostgreSQL focused on metadata and leaves object/file storage to the future VPS/Cloudflare deployment layer.

### Current task/reminder note

Tasks can be created manually and completed or skipped from the dashboard. Automatic generation from vaccination/deworming due dates is a planned enhancement.


### Document file storage

Medical documents can now be uploaded directly from the PWA. The backend stores file bytes on the configured filesystem path (SHELTER_DOCUMENTS_PATH, default ./data/documents) and keeps document metadata in PostgreSQL. Uploads are limited to 25 MB and restricted to common PDF/image/document MIME types. File access is authenticated and scoped to the animal that owns the document.

For VPS deployment, mount the document directory on persistent storage and include it in the backup strategy.
