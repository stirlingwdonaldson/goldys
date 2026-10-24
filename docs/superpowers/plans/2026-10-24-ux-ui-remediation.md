# Prioritised UX/UI implementation plan

Date: 24 October 2026. Starting baseline: `bc96d7aa` (PR #216).
Source: the 24 October UX/UI expert audit (findings F01–F20, user stories
US-01–US-21, Nielsen heuristics H1–H10) and the locally supplied BMAD experience,
role/KPI and UX information architecture documents dated 14–15 October.
Architecture/scope remain governed by `docs/system-context.md` and `docs/prd.md`.
BMAD hypotheses do not create permission grants or approved business priorities.

**Rostering is excluded from this plan and implementation:** no coverage planning,
shift/availability/skills modelling, roster editing or rostering-specific journeys.
Audit US-06/F09 is excluded. Existing aggregate labour reporting is not expanded;
shared qualification fixes still apply consistently to its existing results.

## Ordering and completion rules

1. Correct misleading claims using existing fields before improving convenience.
2. Repair keyboard/recovery/navigation paths before adding new workflow surfaces.
3. Agree duties, scope and policy before committing to context/home/follow-up/report
   designs. Do not rank candidate business decisions on stakeholders' behalf.
4. Prove real source access, schema and coverage before dependent integration work.

Each increment is a reviewable change: existing helpers and contracts, targeted
regressions, full frontend gate, and controlled browser checks of changed flows.
Backend increments additionally require backend gates and architecture invariants.
No push, merge or deployment is included without explicit user approval.

Status meanings: **local** means implemented in this task checkout, with the
verification record below; **next** is ready without new stakeholder decisions or
data; **gated** has the stated prerequisite. Priority ranks remediation risk and
dependencies, not the pub's unvalidated operational priorities. No dates/estimates
are invented for gated work.

## Priority 0 — Truthful existing results (first implementation batch)

| Order / status | Audit trace | Smallest complete change / affected surfaces | Acceptance check |
|---|---|---|---|
| 0.1 local | F01; US-05; H1/5/8 | Always render returned qualifications in the shared component; remove obsolete hide preference/provider/Settings switch; update all dependent tests | With the old preference false, incomplete and companion notices remain beside sales, inventory, dashboard/query/forecast results after reload/navigation; no regex can suppress a notice group |
| 0.2 local | F02; US-05; H1/2/5 | Correct header collection labels/counts and status explanation using current ConnectorStatus fields | Partial, failed, running and never-run states never count as successful completed collection; newest run is not called evidence as-of; missing timestamps do not imply success |
| 0.3 local | F03; US-05/09; H1/2/5 | Remove null-to-zero fallbacks from Home subcounts | Unknown subcounts display unavailable values; explicit zero remains zero |
| 0.4 local | F04; US-01/05/14; H1/5/9 | Disclose failed and truncated queue reads on My work and Home; qualify counts and empty/zero summary copy | Failed discovery cannot say every feed delivers; server total beyond the returned page produces a persistent limit notice; zero open decisions does not imply verification |
| 0.5 local | F13; US-09/10; H2/4/5 | Align Home's attendance proxy and invoice-purchase meanings with detail screens and demo fixtures | Proxy is labelled; purchases never appear as COGS or food-cost %; Home and Kitchen synthetic purchase/waste amounts agree |

Implementation details and limits: [design](../specs/2026-10-24-ux-trust-fixes-design.md).
This batch needs no new data, permission mapping or stakeholder choice. It changes
presentation, not accounting/attendance definitions or source facts.

## Priority 1 — Existing interactions, no new data or policy

Land these as focused follow-ups after Priority 0. Complete the whole row, including
its callers/tests, before moving on; a screen that opens is not a passed user story.

| Order / status | Audit trace | Change and reuse boundary | Acceptance check |
|---|---|---|---|
| 1.1 next | F14; US-18; H1/9 | Keep typed auth/denied/network/server outcomes through the existing Ask hook/stream/answer path; retain failed question and provide retry | A 503 offers recovery rather than an access-denied title; true denial remains denial; retry needs no retyping; cancellation still blocks stale output |
| 1.2 next | F17; US-17/21; H3/4/7/9 | Give invoice rows a real named link/button; add persistent labels to auth/Ask inputs; add equivalent exact values to shared chart widgets | Keyboard-only invoice opening/closing restores focus; inputs retain visible names after typing; resolved chart values and gaps are available without pointer/visual inference |
| 1.3 next | F12 recovery subset; US-12; H1/9 | Expose polling/run-load failure, last-known status and Retry in existing Forecasting; preserve issued run identity | Interrupted checks visibly become last-known/unknown; retry recovers the same run; no new forecast model, product picker or assumptions are invented |
| 1.4 next | F15; US-04/16; H3/4/7 | Apply existing invoice/recipe/reconciliation URL-state pattern to Sales and Reservations provenance sheets | Copy/reload restores permitted metric/date selection; Back/Escape returns to parent filter/page and focus |
| 1.5 next | F19; US-07/20; H1/4/6/10 | Stop calling shipped Recipes “Coming soon”; use a truthful evidence label within the existing menu | Existing recipe evidence is discoverable without a future-features label; no speculative primary navigation redesign |
| 1.6 next | F08 existing-scope subset; US-04; H2/4/6 | Display actual ranges/calendars/comparators already returned by Sales/Reservations/Invoices and fix overstated entry copy | Fixed windows and incompatible comparisons are explicit; screen promises only content it actually provides; no invented shared backend range support |
| 1.7 next | F18 presentation subset; US-17; H1/6/8 | Add a direct route/jump to existing attention work on phones; preserve material caveats and scope; check long labels, tables and header width | A quick check reaches known issues without scanning unrelated evidence; no page-level overflow at 390 px; warnings remain readable at zoom; no unvalidated KPI/context ranking |
| 1.8 next | F04 remaining pagination; US-05/16; H1/7 | Inspect existing paged exception contracts; remove Reconciliation's assumption that its first 200 rows are exhaustive; preserve filters/search semantics | More than 200 real or controlled exceptions are reachable; counts/filter scope are explicit; failed/denied product reads differ from empty results; no unbounded fetch loop |
| 1.9 next | F16 available-help subset; US-20/21; H6/10 | Explain existing measure/state/trace tasks at their entry points; expose an account-recovery route only if the real supported process can be verified | Non-owner can interpret the current task and get a concrete permitted next step; no fabricated reset/support capability; unresolved support process is recorded |

No new backend API is assumed by this list. If tracing a row reveals an API/data
requirement, split out that dependent part and record its gate rather than faking it.
Formal accessibility scope and task-time targets remain open; fixing observed
keyboard/label barriers does not require waiting for that decision.

## Priority 2 — Stakeholder contracts before workflow implementation

These are not authorised implementation tickets by their presence here. They are
candidates for validation; their relative business priority remains open.

| Candidate / audit trace | Gate and concrete validation output | Implement only after that evidence | Story acceptance |
|---|---|---|---|
| Responsibility contexts/home; F05/06, US-01/02 | OD-01/02: actual duties, grants and recurring independent work; validate FOH/BOH/upper-management entry and equal management home choices | Minimal context-aware menu and entry composition; never infer authority from context or title | Assigned work appears, irrelevant/denied controls are not promoted, remembered invalid context explained, no widened grants |
| Weekly reports and contextual exports; F07, US-03/13 | OD-03/08/09/10/13: adopted report scope, meaning, readiness, timing, permitted contents, revision/handoff and sign-off | Retained report-instance contract and selected CSV/Excel flow using semantic queries; dashboard configuration revisions remain configuration | Original issued result and linked reissue remain distinct; export retains scope, numeric types, omissions and caveats; no automatic distribution |
| Owned local follow-up; F11, US-14/15/16 | OD-02/14: performer, owner, verifier/approver, unassigned/acceptance/due and reopening rules | Bounded local annotation/proposal/outcome flow using existing task context; preserve separate operative override authority | Save/Cancel; retained actor/context/version; retries/concurrent edits safe; local completion/external action/verified resolution distinct |
| Shared question/scope/comparator; F08 remainder, US-04 | OD-03/09: trading calendar, compatible populations and comparison meaning; verify current endpoint support | Only supported scope propagation through adopted decision/evidence paths | Selected scope survives navigation; incompatible/unsupported comparator explained rather than silently substituted |
| Forecast/scenario fitness; F12 remainder, US-11/12 | OD-04/09/13: actual decision horizon, selected target, assumptions and fitness/uncertainty contract | Task-facing explanation and retained local scenarios once model/input support exists | Original issue/cutoff/horizon immutable; scenarios visibly hypothetical; outcome comparisons use eligible actuals |
| Role/task help and acceptance; F16/17/18 remainder, US-17/20/21 | OD-12: actual working conditions, browsers/devices, accessibility scope and baseline task study | Task guidance and responsive refinements supported by observed sessions | Representative users complete the adopted task and recover from interruption; agreed thresholds measured rather than invented |

## Priority 3 — Real evidence before dependent capabilities

| Candidate / audit trace | Required evidence before design | Bounded outcome after validation |
|---|---|---|
| Stock through delivery and preparation; F10, US-07/08 | OD-05/06: observed usable/uncommitted holdings and cutoff, item/unit/pack conversions, effective recipes/yields, outstanding supply and actual delivery horizon; prove access on real samples | Explainable scoped stock/preparation/order option with unknown conversions/horizon visible; local save clearly separate from external order |
| Product/variant forecasting; F12, US-11 | Verified stable product/variant identity, sale units, history coverage and stockout/new-product fitness; model request supports that target | A real selectable product forecast with correct units/cutoff/uncertainty, not a picker over revenue-only output |
| Opening planning; F20, US-19 | OD-11 and one active owner opening decision; observed new-site evidence, explicit transfer assumptions and missing costs | Secondary, personally pinnable scenario comparison; current-pub facts distinct from assumptions; no second live-venue total |

None of these gates can be satisfied by synthetic demo fixtures. Existing recipe
and purchase/count/waste evidence is useful, but does not establish current usable
stock, an order quantity, consumed cost or a transferable new-site forecast.

## Change scope for the first batch

- Shared qualifiers and obsolete preference wiring: all callers retain the same
  `notices` contract; affected coverage/Home/browser tests updated.
- Shell collection chip: existing connector status enum/timestamps only; no change
  to connector health API, sidebar permissions or ingestion.
- Home KPI view and existing bootstrap demo: reservation labels/subcounts,
  purchase/waste meaning; no change to the backend DTO or permission checks.
- Queue hook, My work, Home preview and zero-decision band: carry limit metadata
  and checked-scope qualifications; keep reads bounded and existing focus/filter
  behaviour. The separate Reconciliation paging defect is explicit Priority 1.8.
- Local tests and documentation; no exports, configuration, migrations, source
  corrections, roster changes or new dependency.

## Verification and release record

- Initial targeted tests: 8 files / 61 tests passed. The first sandboxed attempt could not create Vitest worker temporary files; the rerun succeeded.
- Full frontend gate: `bun run typecheck`, `bun run lint`, `bun run test` (122 files / 689 tests) and `bun run build` passed. Typecheck/lint were repeated after the browser regressions were added; no build/typecheck overlap.
- Controlled browser checks: 8 Playwright tests passed across `ux-trust.spec.ts` and the updated `ui-small-fixes.spec.ts`, including 1440/390 px layouts, persistent caveats, partial/never-run states, unknown subcounts, purchase basis, failed checks and a 201-decision population. All browser API calls used fixtures; no live backend was exercised.
- Ripwire quality review: zero gating findings after one explicit historical-churn acknowledgement for the simplified `DataQualifications` component; eight minor findings remain (seven churn signals and five-line growth of My work). `DataQualifications` contract unchanged. Caller/change analysis is advisory: TSX calls are incompletely resolved; manual caller inspection and the full frontend suite supply the verification. `git diff --check` passed.
- Branch: `codex/ux-trust-fixes`; base `bc96d7aa`.
- Push/PR/CI/merge/deployment: not performed; explicit approval required.

Only mark a local batch verified after command output from this session. These
checks establish implementation behaviour, not live source completeness, financial
validity, stakeholder acceptance or formal accessibility conformance.
