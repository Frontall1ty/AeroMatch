# AeroMatch – Product Roadmap (air freight only)

Sources: `AeroMatch_Proje_Tanitim_Dokumani.docx` (business) and `AeroMatch_Teknik_Mimari_ve_Gelistirme_Specifikasyonu.docx` section 16 (sprints). Labels: **[FACT]** / **[ASSUMPTION]** / **[DECISION NEEDED]** / **[RECOMMENDATION]**.

**[FACT]** MVP scope is air freight only. The business document's later road/sea/multimodal phases are **future vision, not planned work**, and must not be built or scaffolded now.
**[FACT]** Sprint durations are not committed in the spec. No dates are promised here.

## Sprint plan (from spec section 16)
| Sprint | Output | Status |
|---|---|---|
| 0 Foundation | Repo, CI, Compose, PostgreSQL, migration, OpenAPI | **Done** (commit `ff30c83`, CI green; Postgres boot test not exercised locally) |
| 1 Identity | Register/login, company, membership, RBAC | Not started. Blocked by decisions below |
| 2 Shipment | Shipment/item CRUD, validation, document metadata | Not started |
| 3 Capacity & Quote | Capacity offers, quote creation/listing | Not started |
| 4 Matching & Booking | Filtering/ranking, quote acceptance, provider booking confirmation | Not started |
| 5 Tracking & Admin | Shipment events, notifications, audit, admin screens | Not started |
| 6 Pilot hardening | Security tests, backup, monitoring, UAT | Not started |

## Roadmap adjustment (recommendation)
**[RECOMMENDATION]** Insert a "Security foundation" slice at the start of Sprint 1, before any business endpoint: authentication decision, deny-by-default Spring Security, tenant-isolation test harness, environment-specific Swagger/CORS. Rationale: the audit found every endpoint is currently open and no tenant isolation exists.

## Gates before Sprint 1 can start
1. **[DECISION NEEDED]** Authentication architecture (JWT vs cookie session).
2. **[DECISION NEEDED]** Company types, roles, and verification method (depends on who publishes capacity and who confirms bookings).
3. **[DECISION NEEDED]** Legal role of the platform in Uzbekistan (needs a qualified expert; not decided by engineering).

## Pilot readiness (from business document, unchanged)
**[FACT]** First success criterion: a few companies run real shipment requests, receive multiple valid quotes, and some bookings are verifiably confirmed by providers. Field validation with forwarders/agents (spec section 19 checklist) is outstanding and is a business task.
