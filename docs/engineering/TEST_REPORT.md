# AeroMatch – Test Report

Only results that were actually run are listed. "Skipped" and "not run" are never reported as passed. Labels: **[FACT]** observed · **[ASSUMPTION]** not directly observed.

## Run 1 – Sprint 0 local verification (2026-11-04)
Environment: Windows 11, JDK 25 (code targets Java 21), Node 22.14, npm 10.9, Docker **not installed**.

| Check | Result | Notes |
|---|---|---|
| Backend `mvn -B verify` | **[FACT]** exit 0, BUILD SUCCESS | 4 tests run, 0 failed, 3 skipped |
| `AeroMatchApplicationTests` | **[FACT]** 1 run, passed | uses `test` profile with DataSource/JPA/Flyway excluded, so it does not exercise the database |
| `PostgresBootTest` (3 tests) | **[FACT]** skipped | Docker not found; **not exercised locally** |
| Frontend `npm ci`, `npm run lint`, `npm run build` | **[FACT]** all exit 0 | `/` and `/_not-found` prerendered; npm printed audit warnings |
| `npm audit --omit=dev` | **[FACT]** 0 vulnerabilities | read-only audit run |
| Docker Compose, Docker images | **[FACT]** not run | no Docker locally |

## Run 2 – CI on commit `ff30c83` (2026-11-04)
GitHub Actions run 41828666137 (event: push to main).

| Job / step | Result |
|---|---|
| backend: checkout, setup-java, "Build and test (incl. Postgres boot test)", "Docker image builds" | **[FACT]** success |
| frontend: checkout, setup-node, `npm ci`, lint, build | **[FACT]** success |
| Test counts / whether `PostgresBootTest` ran unskipped | **[ASSUMPTION]** ran, since `ubuntu-latest` provides Docker; logs not readable without GitHub login. Tracked in TB-010 |

## Spec acceptance scenarios (section 13.1) – status
All **not testable yet** (no domain code exists): cross-company access denied; acceptance does not confirm booking; expired quote rejected; expired capacity excluded; concurrent acceptance; invalid weight/dimension rejection; agent quotes only authorised requests.

## Known test gaps
- Security skeleton tests exist since TB-002 (see Run 3). Still missing: tenant-isolation and role-based authorization tests (TB-003/TB-004), MFA tests.
- Frontend has only a Node built-in test for the API client (TB-009 owns a proper runner).
- Local runs cannot validate Flyway/Hibernate; only CI does (**[FACT]** per skip above).

## Run 3 - TB-002 (security skeleton) and TB-005 frontend slice, local (2026-11-04)
Environment as Run 1, plus `io.zonky.test:embedded-postgres` 2.1.0 (test scope; runs a real PostgreSQL 14.15, no Docker needed).

| Check | Result |
|---|---|
| Backend `mvn -B verify` (re-run by the main session, not only by the implementing agent) | **[FACT]** exit 0; 102 run, 0 failed, 6 skipped |
| `InMemorySecurityFlowTest` (36), `JdbcSecurityFlowTest` (40, real PostgreSQL + Flyway V2 + `JdbcIndexedSessionRepository`) | **[FACT]** pass |
| Others: `Argon2PasswordEncoderTest` 3, `PermitAllRoutesTest` 3, `SecurityPropertiesValidationTest` 7, `ApiDocs*Test` 3, cookie tests 3, context test 1 | **[FACT]** pass |
| `PostgresBootTest` (6) | **[FACT]** skipped locally (no Docker). Its Testcontainers wiring and the changed `openApiIsNotPublicByDefault` assertion are **not exercised until CI**. A temporary copy of an earlier version ran 6/6 against embedded PostgreSQL |
| Frontend `npm run lint`, `npm test` (7/7 pass), `npm run build` | **[FACT]** exit 0 |
| Security properties proven by tests | deny-by-default incl. unmapped paths; CSRF (missing/wrong/foreign-Origin/`null` Origin/Referer, rotation on login); session fixation; 12 h absolute timeout with a mutable clock (11h59 ok, 12h01 denied, rotation does not extend); 30 min idle against the real JDBC store; cookie attributes incl. raw Tomcat header; POST-only logout invalidates the server session; 401/403/400/415 never 500; docs closed by default |
| Not verified | real login (TB-004), tenant isolation (TB-003), behind a real reverse proxy (forwarded headers, `__Host-` over HTTPS), Argon2 timing on the target host, frontend against the real backend, Docker image builds |
| Security review | one read-only review of the TB-002 code found 0 Blockers, 1 High and several Medium/Low items; all fixed with tests and re-verified |
