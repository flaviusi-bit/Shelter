# Shelter architecture

## Initial stack

- Frontend: React + TypeScript + PWA
- Backend: Spring Boot + Java 21
- Database: PostgreSQL 16
- Migrations: Flyway
- Local infrastructure: Docker Compose
- Production: Docker on a VPS, with Cloudflare in front

## Core domains

1. Animals
2. Medical records
3. Treatments and medication administrations
4. Vaccinations
5. Deworming / antiparasitic treatments
6. Veterinary visits
7. Laboratory results
8. Documents and photos
9. Locations and quarantine
10. Users and roles
11. Tasks and reminders
12. Audit history

## Design principles

- Every medication administration is an auditable event.
- Scheduled treatments are separate from actual administrations.
- Animal history must remain available after status changes such as adoption or foster placement.
- Veterinary protocols are data entered/confirmed by authorized staff; the application does not autonomously prescribe medication.
- Infrastructure must remain portable so production can move between VPS providers without redesigning the application.
