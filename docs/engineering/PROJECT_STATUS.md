# AeroMatch – Project Status

Last updated: 2026-11-04. Baseline commit: `ff30c83` (Sprint 0). Labels used throughout these documents:
**[FACT]** verified in the repo or CI · **[ASSUMPTION]** believed but not verified · **[DECISION NEEDED]** unresolved, human decides · **[RECOMMENDATION]** suggested, not decided.

## Scope
**[FACT]** AeroMatch is an AIR-FREIGHT-only B2B platform: shipment request → supplier quote → comparison → quote acceptance → booking request → provider confirmation → tracking event. Road, sea, and multimodal are out of MVP scope.
**[FACT]** A booking is confirmed only when the provider confirms it. Quote accepted, booking requested, and booking confirmed are distinct states (ADR-006).

## Current phase
Sprint 0 (foundation) complete. Sprint 1 (identity) **not started** and not approved to start.

## Verified state
| Area | State | Evidence |
|---|---|---|
| Repo | main at `ff30c83`, pushed to `origin/main` | `git ls-remote` returned `ff30c83` |
| CI run #1 | **[FACT]** green: backend job (build, tests, backend Docker image build) and frontend job (`npm ci`, lint, build) succeeded | GitHub Actions API, run 41828666137 |
| Backend | Spring Boot 3.5.6 / Java 21 skeleton; Flyway baseline `SELECT 1;`; OpenAPI config; error handler; 12 module packages are empty `package-info.java` | audit |
| Frontend | Next.js 16 skeleton: landing page + API health widget + `apiFetch` wrapper; `features/*` empty | audit |
| Infra | Compose with PostgreSQL 16 (127.0.0.1 only); `.env.example`; no deployment config | audit |
| Local verification | backend `mvn verify` and frontend lint/build passed locally; `PostgresBootTest` skipped locally (no Docker) | QA report |
| Test counts in CI | **[ASSUMPTION]** `PostgresBootTest` ran (not skipped) on `ubuntu-latest`; logs were not readable without GitHub login | CI API shows step success only |

## Not implemented (confirmed by audit)
Spring Security / any authentication · API authorization · tenant (company) isolation · users/companies/memberships schema · audit log · RBAC · CORS for `/api/**` (only actuator CORS is set) · environment-specific Swagger/OpenAPI exposure · frontend auth pages and role-aware layout · OpenAPI-generated types.

## Unresolved decisions
See `DECISION_LOG.md`: authentication architecture (JWT vs cookie session), who publishes capacity, who confirms bookings and with what evidence, company verification method, volumetric divisor and unit rules, Uzbek legal role of the platform.

## Engineering system
- Six agents in `.claude/agents/` (architect, backend, frontend, QA, security, logistics analyst). The main session orchestrates.
- Scheduled task "AeroMatch Autonomous Engineering Cycle": see `AGENT_WORK_LOG.md` for its verified activation status.

## Next
Awaiting human approval before any implementation. Top priorities are in `TECHNICAL_BACKLOG.md` (security and tenant isolation first).
