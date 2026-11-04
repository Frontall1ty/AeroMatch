# AeroMatch – project instructions

Air-cargo quote and booking coordination platform (B2B). Source of truth: the two `.docx` documents in the repo root.
Stack: modular monolith – Spring Boot 3.x / Java 21 (`backend/`), Next.js + TypeScript (`frontend/`), PostgreSQL, Docker (`infra/`).
Out of scope: microservices, Kafka, Kubernetes, event sourcing, AI matching, payments/escrow, live airline APIs.

## Non-negotiable rules
- **A booking is never described or stored as confirmed unless the provider confirmed it.** Quote ACCEPTED, booking REQUESTED, and booking CONFIRMED are different states (ADR-006).
- Company-level data isolation is enforced in every backend query, not only in the UI.
- Capacity data always carries source, last-verified time, validity.
- Run build and tests before every commit. Never commit failing code.
- No secrets in Git.

## Multi-agent workflow
Project subagents live in `.claude/agents/`:

| Agent | Role | Edits files? |
|---|---|---|
| `software-architect` | Reviews major architectural decisions, module boundaries, data model, state machines | No |
| `backend-developer` | Implements `backend/` tasks | Only assigned files in `backend/` |
| `frontend-developer` | Implements `frontend/` tasks | Only assigned files in `frontend/` |
| `qa-engineer` | Runs builds, tests, integration and acceptance checks; reports honestly | No |
| `security-reviewer` | Reviews authn/authz, tenant isolation, documents, sensitive operations | No |

Collaboration rules:
1. The **main agent** coordinates, integrates, and makes final decisions. Subagents report to it only.
2. **Never have two agents edit the same file at the same time.** The main agent assigns disjoint file sets (backend vs frontend vs infra); parallel work is allowed only on disjoint files.
3. Architect reviews any major architectural decision (new module, schema change, state-machine change, auth approach) before implementation.
4. Security reviewer reviews anything touching authentication, authorization, tenant scope, documents, or quote/booking transitions before it is committed.
5. QA verifies builds, tests, and integration before each commit; its report is attached to the commit decision. "Not run" is never reported as "passed".
6. Subagents do not commit or push; the main agent commits after green verification.
7. Do not start the next sprint until the current sprint is verified.

## Local environment notes
- `JAVA_HOME` on this machine points to a missing JDK 21; use `JAVA_HOME="C:/Program Files/Java/jdk-25"` (code targets Java 21).
- Docker is not installed locally; Postgres-backed tests run in CI or after installing Docker.
