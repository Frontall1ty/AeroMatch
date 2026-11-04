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
- No authorization, tenant-isolation, or security tests (no security code exists).
- No frontend test runner.
- Local runs cannot validate Flyway/Hibernate; only CI does (**[FACT]** per skip above).
