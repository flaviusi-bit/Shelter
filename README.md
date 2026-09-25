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
