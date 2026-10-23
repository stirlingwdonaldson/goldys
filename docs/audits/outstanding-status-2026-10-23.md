# Audit reconciliation — 23 October 2026

Baseline: main `8d6fa0a`. This register separates source changes from production acceptance.
The imported requirements package is local, untracked reference material; its proposed contracts
are not automatically approved delivery commitments. Deputy is explicitly excluded by the user.

| Finding | Current status | Evidence / remaining boundary |
|---|---|---|
| Drift D1–D12 | Main corrective implementations merged in #203 | `data-handling-drift-plan.md`, approved implementation table; original findings describe older code. Historical repair and production acceptance remain distinct. |
| CTB product groups / processing health / PDF reads | Corrections merged in #206 | `ops/recovery/2026-10-23/README.md`; old failure history and sale-line recovery are not repaired by deployment alone. |
| Waste dates, detail retrieval and values | Corrections merged in #205 | V99 and CTB wastage tests; no current-stock or COGS assertion. |
| Recipe qualification loss, G10 | Implemented on this task branch | V100; canonical facade → resolved projection → semantic API/tool → live/demo recipe display. Source quantities, zero, unknown and broken-link state remain distinct. |
| Remaining sales-detail calendar coverage, G13/G15 | Implemented on this task branch | Sale-item/deleted-sale executors use shared coverage; a missing category operand makes the day unresolved. Expected population/source completeness remains unverified. |
| Frontend F01/F12 | Query migration implemented; locally verified | Scoped Query provider and abortable API reads; sharing identical keys, native invalidation and session/mode isolation replace per-instance read state. |
| Frontend F02/F04 | Action/editor repairs implemented; locally verified | Failed saves retain edits, pending saves preserve subsequent edits, dirty exit/restore protection, independent preview/history errors. No server optimistic-concurrency claim. |
| Frontend F05 | Explorer corrections implemented; locally verified | URL-backed layer/entity/applied filters/pagination, Apply, visible missing values and current-page sorting label. Backend metadata still lacks complete typed columns. |
| Frontend F06/F08 | Stream/contract corrections implemented; locally verified | Abort/generation fencing, reader cleanup, CRLF framing; shared version-aware widget/dashboard/draft/provenance validation. |
| Frontend F03/F10/F11 | Transport/auth/test-gate foundation already exists | `api/client.ts`, `response.ts`, current-user provider and `.github/workflows/ci.yml`. CI runs Vitest; the old AGENTS assertion was stale. |
| Frontend F07 | Relevant controls/tables reused where touched | Main #197 already modernised several screens. No wholesale design-system replacement. |
| Frontend F09 | Privacy follow-up implemented; locally verified | Telemetry drops unknown/free-text query parameters and retains only bounded navigation metadata. Sampling is unchanged pending measurement. |
| G01/G02 reports/exports | Blocked by OD-13 | Actual report contents, preparation/sign-off/reissue, export/access contract. No report scheduler or generic export endpoint is built. |
| G04 actual attendance | Blocked by evidence/eligibility | Booking-size proxy remains qualified; no fabricated observed covers. |
| G05/G06/G10 stock/consumption/valuation | Partly implemented; remaining behaviour blocked | Counts/waste observations exist. Conversions, movements, valuation and delivery/commitment semantics need validation. |
| G08/G14 identity/effective history | Partly corrected; historical/cross-source work blocked | Source revision/export identity and effective precision cannot be guessed. |
| G16/G17 selected-product forecasts/scenarios | Blocked by identity and operating contracts | Existing revenue snapshot/model capabilities retained. |
| G20/G21 responsibility/follow-up workflows | Blocked by assignments/grants/authority | Do not introduce grants or a proposal/approval lifecycle from job titles. |
| G24 operating acceptance | Not established by source inspection | Production permission/recovery/service acceptance needs its own evidence and authorization. |

## Decisions already answered

The owner's later decisions in `docs/data-handling-drift-plan.md` (Business decisions recorded from
the user's follow-up) supersede conflicting earlier open-question text: Australia/Melbourne;
Monday–Sunday reporting; simplified 02:00 trading cutoff with exact boundary still pending;
GST-exclusive performance from evidenced components; Invoice purchases rather than COGS;
withdrawn Food cost %; wages-only qualification; booking-size attendance proxy; explicit intended
aggregate/detail access audiences; prominent incomplete subtotals; and the five-percentage-point
uncertainty principle with method/calibration still unresolved. These decisions do not settle
source tax bases, actual account grants, historical repair or all subquestions under an OD id.

## Verification

Local gates passed: 1,639 backend tests (two live evaluations skipped), 638 frontend tests,
frontend typecheck/lint/build, and four production-build browser regressions with intercepted APIs.
See `docs/superpowers/plans/2026-10-23-outstanding-audit-fixes.md` for actual commands, bounded
request counts and remaining limits. Static quality review retains 107 gating heuristics; the
changes remain uncommitted. No remote CI or production acceptance is asserted. Older reports'
passing tests or deployment statements are not evidence of this branch's current acceptance.
