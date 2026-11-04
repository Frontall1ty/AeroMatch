# AeroMatch – Decision Log

Labels: **[FACT]** recorded decision · **[DECISION NEEDED]** unresolved · **[RECOMMENDATION]** proposal awaiting approval. Only the human approves decisions; agents propose.

## Accepted decisions (from the technical spec, section 17)
| ID | Decision | Status |
|---|---|---|
| ADR-001 | Modular monolith (no microservices, Kafka, Kubernetes) | **[FACT]** Accepted |
| ADR-002 | PostgreSQL | **[FACT]** Accepted |
| ADR-003 | REST API + OpenAPI | **[FACT]** Accepted |
| ADR-004 | Deterministic matching (no AI/ML in the MVP) | **[FACT]** Accepted |
| ADR-005 | Manual / semi-manual capacity; no automation claim without verified airline integration | **[FACT]** Accepted |
| ADR-006 | Quote acceptance and booking confirmation are separate; only the provider confirms a booking | **[FACT]** Accepted (invariant) |

## Decisions made in the Sprint 0 session
| ID | Decision | Status |
|---|---|---|
| D-001 | MVP scope is AIR FREIGHT only; road/sea/multimodal are out of scope | **[FACT]** Stated by the project owner |
| D-002 | Spring Boot 3.5.6, Java 21 target (built with JDK 25 locally via `--release` 21), springdoc 2.8.13 | **[FACT]** Implemented; **[RECOMMENDATION]** re-check Boot 3.5.x support window and latest patch (architect flagged possible end of OSS support) |
| D-003 | Base config has no DB credential defaults; dev defaults only in `application-local.yml` | **[FACT]** Implemented |
| D-004 | Postgres boot test uses Testcontainers, skipped without Docker, runs in CI | **[FACT]** Implemented |
| D-005 | Project subagents defined in `.claude/agents/`; main session orchestrates; no two agents edit the same file concurrently | **[FACT]** Adopted |
| D-006 | Development efficiency and verification policy (batching, minimal agents, targeted tests, optimization phase after MVP, one final audit); recorded in `CLAUDE.md` | **[FACT]** Stated by the project owner 2026-11-04 |
| D-007 | Scheduled task works only on backlog items with status `Approved by human`; edits only on feature branches | **[FACT]** Implemented in the task prompt |

## Unresolved decisions
| ID | Question | Options / notes | Needed before |
|---|---|---|---|
| U-001 | Authentication architecture | (a) HttpOnly+Secure+SameSite cookie session with CSRF; (b) short-lived JWT access + refresh rotation. **[RECOMMENDATION]** pending architect and security review; no recommendation recorded yet | Sprint 1 / TB-001 |
| U-002 | Who publishes capacity (airline cargo sales, GSA/GSSA, forwarder, cargo agent) | Business input required | Sprint 1 roles, Sprint 3 |
| U-003 | Who confirms a booking and what evidence is stored | Constraint: confirmation is provider-authorized | Sprint 4 / TB-006 |
| U-004 | Company verification method and first user roles | Spec checklist section 19 | Sprint 1 |
| U-005 | Volumetric divisor default and per-provider config; cm vs m³ unit rule | Must not be a hardcoded universal value | Sprint 2 |
| U-006 | Quote price shape: all-in vs itemised; currencies; surcharges and taxes | Business input | Sprint 3 |
| U-007 | Legal role of the platform in Uzbekistan (agent, broker, organizer, software provider) | **[ASSUMPTION]** needs a qualified legal expert; engineering does not decide | Before pilot |
| U-008 | First routes / pilot supplier group | From field interviews | Before pilot |
| U-009 | Capacity update frequency and trusted sources | Business input | Sprint 3 |
| U-010 | Handling of dangerous goods, live animals, temperature-controlled cargo | Spec: flag and manual provider review only | Sprint 2 |

## How to add a decision
Append a row with ID, date, decision, rationale, reviewers (architect / security / analyst), and approver. Never edit history; supersede with a new row.
