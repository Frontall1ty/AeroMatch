# AeroMatch

B2B air-cargo shipment request, quote and booking coordination platform (Tashkent / Uzbekistan first).
Business overview and technical spec: the two `.docx` files in this repo. Working rules and the multi-agent workflow: [CLAUDE.md](CLAUDE.md).

Architecture: modular monolith. `backend/` Spring Boot 3.x (Java 21) + PostgreSQL + Flyway, `frontend/` Next.js + TypeScript, `infra/` Docker.

> A quote being accepted is not a confirmed booking. Only the provider's confirmation confirms a booking.

## Run locally

Prerequisites: JDK 21+, Maven 3.9+, Node 22+, Docker (for PostgreSQL).

```bash
cp .env.example .env
docker compose -f infra/docker/docker-compose.yml --env-file .env up -d
```

```bash
cd backend && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

The `local` profile supplies dev database credentials; the base config has no DB defaults.

```bash
cd frontend && cp .env.example .env.local && npm install && npm run dev
```

- API health: http://localhost:8080/actuator/health
- OpenAPI UI: http://localhost:8080/api/v1/swagger-ui.html
- Web: http://localhost:3000

## Build and test

```bash
cd backend && mvn -B verify
```

```bash
cd frontend && npm ci && npm run lint && npm run build
```

The backend context test runs without a database. Postgres-backed integration tests arrive with Sprint 1.

## Status

Sprint 0 (foundation) – see section 16 of the technical spec for the sprint plan.
