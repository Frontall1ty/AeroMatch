# AeroMatch – Technical Backlog

Ranked by repository evidence (audit of commit `ff30c83`). Security and tenant isolation first. Air freight only. Labels: **[FACT]** / **[ASSUMPTION]** / **[DECISION NEEDED]** / **[RECOMMENDATION]**.
Status values: `Open` · `Blocked` · `Approved by human` (written only by the human; the scheduled task works only on items with this status) · `In progress` · `Done`. Nothing here is implemented yet. No item may start without human approval.

Review rules: items touching authentication, authorization, tenant scope, or booking transitions need `security-reviewer`; schema/state-machine/auth design needs `software-architect`; domain wording/fields need `logistics-domain-analyst`; every item needs `qa-engineer` verification before commit.

---
## TB-001 – Decide authentication architecture
- **Priority:** 1 (Blocker)
- **Description:** **[FACT]** The spec leaves JWT vs cookie session open ("session/JWT"); no ADR exists; the frontend client has no credentials/token handling. Produce a decision with options (HttpOnly cookie session + CSRF, or short-lived JWT + refresh), trade-offs for a Next.js frontend, and a recommendation.
- **Dependencies:** none.
- **Acceptance criteria:** ADR recorded in `DECISION_LOG.md` with the human's approval; covers token/session storage, refresh/logout, CSRF, CORS credentials, rate limiting; reviewed by architect and security-reviewer.
- **Status:** Open. Decided by owner in ADR-007 and independently reviewed (2026-11-04); awaiting owner approval of the review recommendations before closing

## TB-002 – Spring Security with deny-by-default and correct error mapping
- **Priority:** 2 (High)
- **Description:** **[FACT]** `spring-boot-starter-security` is not a dependency; all endpoints, including actuator and Swagger, are open. **[FACT]** The catch-all `Exception` handler would turn future 401/403 into 500. Add Security with deny-by-default, an explicit allow-list (health only), 401/403 handlers, password encoder.
- **Dependencies:** TB-001.
- **Acceptance criteria:** unauthenticated request to any non-allow-listed path returns 401; forbidden returns 403 (never 500); tests prove it; security-reviewer approves; `mvn verify` green.
- **Status:** Open. Owner decisions U-011, U-012, U-016, U-019 recorded as D-009 to D-012 (2026-11-04); not yet `Approved by human`, so the scheduled task will not pick it up

## TB-003 – Tenant (company) isolation foundation and IDOR test harness
- **Priority:** 3 (High)
- **Description:** **[FACT]** Nothing resolves a company from the current user; no tenant-scope tests exist. Provide a company-scope mechanism usable by every repository/service, plus a reusable test harness proving a user of company A cannot read or modify company B data by guessing IDs.
- **Dependencies:** TB-001, TB-002, TB-004 (identity schema).
- **Acceptance criteria:** cross-company read/list/update returns 404/403 in tests for the first protected resource; harness reusable for later modules; list endpoints always company-scoped; architect and security-reviewer approve.
- **Status:** Blocked (TB-002 and TB-004 first; owner's unblock rule covers only U-011/12/16/19, so U-014 and U-015 are still unresolved: **[DECISION NEEDED]** whether they also gate this item)

## TB-004 – Identity and company schema with RBAC
- **Priority:** 4 (High)
- **Description:** **[FACT]** Migration is `SELECT 1;`; no `users`, `companies`, `company_members`, `refresh_tokens`, `audit_logs`. Create via Flyway: UUID keys, UTC timestamps, unique constraints, roles PLATFORM_ADMIN, COMPANY_ADMIN, FORWARDER_OPERATOR, SHIPPER_USER, AGENT_OPERATOR, VIEWER. Audit log for security-relevant changes.
- **Dependencies:** TB-001; company types/roles from TB-007 (**[DECISION NEEDED]**).
- **Acceptance criteria:** migration applies on PostgreSQL in CI (Testcontainers); Hibernate `validate` passes; constraint tests; no hard deletes of auditable records.
- **Status:** Blocked (needs U-011 and U-014 PLATFORM_ADMIN model; owner's unblock rule listed U-011/12/16/19 only: **[DECISION NEEDED]** whether U-014 also gates this item)

## TB-005 – Environment-specific OpenAPI/Swagger and CORS
- **Priority:** 5 (Medium-High)
- **Description:** **[FACT]** Swagger UI and `/api/v1/openapi` are enabled in every profile; `PostgresBootTest` asserts OpenAPI is served. **[FACT]** CORS is configured only for actuator (`management.endpoints.web.cors`), not `/api/**`. Add a `prod` profile that disables Swagger/OpenAPI (or requires admin auth), configure CORS for `/api/**` from an explicit origin list per environment, and adjust tests.
- **Dependencies:** TB-002 (CORS credentials depend on TB-001).
- **Acceptance criteria:** in `prod` profile Swagger/OpenAPI return 404 or 401; dev profile keeps them; CORS allows only configured origins (no `*` with credentials); tests for both profiles.
- **Status:** Open. Owner decisions U-011, U-012, U-016, U-019 recorded as D-009 to D-012 (2026-11-04); not yet `Approved by human`, so the scheduled task will not pick it up

## TB-006 – Provider-only booking confirmation: domain rules and tests
- **Priority:** 6 (High, invariant)
- **Description:** **[FACT]** ADR-006 separates quote acceptance and booking confirmation; no code exists yet. Define the booking/quote/shipment state transitions, who may trigger each (CONFIRMED only by the booking's provider company), the evidence recorded (**[DECISION NEEDED]**: what reference/proof), and shared wording for API and UI.
- **Dependencies:** TB-007; TB-004/TB-003 before implementation.
- **Acceptance criteria:** written transition table approved by architect, security-reviewer, and logistics analyst; test cases listed: acceptance never sets CONFIRMED; non-provider cannot confirm; expired quote cannot be accepted; concurrent acceptance consistent.
- **Status:** Open (design/spec only; no implementation)

## TB-007 – Resolve blocking domain decisions (spec section 18)
- **Priority:** 7 (Blocker for Sprints 1-4)
- **Description:** **[DECISION NEEDED]** Who publishes capacity (airline cargo sales, GSA/GSSA, forwarder, agent); who gives booking confirmation and what evidence is stored; company verification method and first user roles; volumetric divisor default and unit rules (cm vs m³); quote price all-in vs itemised and currencies; platform's legal role in Uzbekistan (needs a qualified expert, not engineering).
- **Dependencies:** human and field input (forwarder/agent interviews per business document).
- **Acceptance criteria:** each question answered or explicitly deferred in `DECISION_LOG.md`, with owner and date.
- **Status:** Open

## TB-008 – Reconcile business and technical documents
- **Priority:** 8 (Medium)
- **Description:** **[FACT]** Module and entity lists differ between the two documents (e.g. `quote_revisions` only in the business doc; Matching/Documents/Airports modules only in the spec); the business roadmap describes road/sea/multimodal phases; "available capacity" / `available_kg` wording can read as a guarantee. Produce one agreed module/entity list, mark road/sea as out-of-scope future vision, and use "reported availability" wording with source and last-verified time.
- **Dependencies:** TB-007 (partly).
- **Acceptance criteria:** single glossary and entity list in docs; analyst and architect approve; no road/sea/multimodal artifacts in the repo.
- **Status:** Open

## TB-009 – Frontend hardening before auth work
- **Priority:** 9 (Medium)
- **Description:** **[FACT]** `NEXT_PUBLIC_API_BASE_URL` is baked at build time with a silent localhost fallback; `cacheComponents`/`partialPrefetching` experimental flags are enabled and unused; no test runner or `typecheck` script; boilerplate README/SVGs; Google Fonts fetched at build; `@types/node ^20` vs Node 22. Fix env handling (fail fast outside dev), remove unneeded experimental flags, add typecheck and a test runner, remove boilerplate.
- **Dependencies:** TB-001 for credentials handling in the API client.
- **Acceptance criteria:** production build fails if the API URL is unset; `npm run typecheck`, `lint`, `build`, and at least one unit test pass in CI; no boilerplate files.
- **Status:** Open

## TB-010 – CI and container hygiene; close the local-vs-CI test gap
- **Priority:** 10 (Low-Medium)
- **Description:** **[FACT]** Locally `PostgresBootTest` is skipped (no Docker) so Flyway/Hibernate are validated only in CI; base images and Actions are not pinned; no container HEALTHCHECK or JVM memory flag; dev password duplicated in three files; stale notes in `CLAUDE.md`/README; `frontend/AGENTS.md` references docs not present on a fresh clone. **[ASSUMPTION]** CI ran `PostgresBootTest` unskipped; confirm by reading CI logs or surfacing counts in the job summary.
- **Dependencies:** none.
- **Acceptance criteria:** CI prints/uploads Surefire counts and fails if the Postgres test is skipped; images pinned by digest or minor tag; healthchecks added; docs consistent.
- **Status:** Open
