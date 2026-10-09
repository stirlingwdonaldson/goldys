# Frontend migration roadmap — proposed review slices

Status: **proposed sequencing**, not an approved executable implementation plan.
Baseline: `f6140b8`. Review [target architecture](frontend-target-architecture.md) first.
Each slice should get an implementation plan, focused regression tests and its own review.
No push, merge or deployment is implied. Source audit precedes migration; live measurements
and authenticated fixtures still need collection.

## Tracking

- [x] Inspect checked-out HEAD, existing implementation/tests and dependencies.
- [x] Inventory query consumers, direct async workflows and error ownership.
- [x] Restore existing frozen dependencies and execute baseline checks.
- [x] Inspect backend auth, dashboard, explorer and correlation contracts.
- [ ] Complete production-like authenticated browser baseline and API captures.
- [ ] Review architecture and dependency proposals.
- [ ] Approve written first-slice spec and executable plan; select execution method.
- [ ] Implement and verify each slice below; update audit/verification after each.

## Ordered PRs

### PR 0 — Audit and architectural review artifacts (this branch)

Problem/root cause: findings were hypotheses without checkout-specific evidence.
Affected files: the five `docs/frontend-*.md` deliverables. Adds no product dependencies or
behavior changes. Records existing tests/checks and demo browser limitations. Rollback:
revert documentation. Residual risk: no live authenticated verification; proposals unapproved.

### PR 1 — Reliable transport and authentication lifecycle

Depends on architecture review. Affected: `lib/api/client.ts`, `errors.ts`, `index.ts`,
current-user provider, user menu, auth pages; contract tests. Preserve Api adapters and server
auth. Add standard Headers, structured status/cause/header-correlation, abort classification,
expected-body semantics (including backend empty 200 delete), explicit redirect diagnostics.
Clear stale identity, fence profile refreshes, consume logout rejection and expose pending/error.
MSW can enter here for real transport integration tests.

Rejected: switching to Axios or synthesizing sign-out from any parse error. Regression cases:
Headers/tuples, CSRF/content types, 204/empty 200 vs required JSON, malformed/error envelopes,
500/401/403/redirect/network/abort, late profile responses, logout failure and stale role UI.
Rollback: revert transport/auth commits together before dependent cache work; preserve wire
contracts. Risk: current server authentication entrypoint may redirect; capture behavior in
fixtures rather than change Spring security implicitly.

### PR 2 — Scoped Query foundation and Overview bootstrap

Depends on PR 1. Add `@tanstack/react-query`, `lib/query` provider/defaults and dashboard keys/
options; extend read Api signatures/live helpers with optional signals. Integrate mode and
session-generation clear/cancel sequencing in app providers. Migrate Overview bootstrap only
first; keep the already consolidated backend endpoint. No generic useApiData replacement hook.

Tests: concurrent same-key dedupe, abort of replaced scope, demo/live hydration transition,
same-role different-session isolation, logout/login/focus sequencing, bootstrap loading/error/
empty/refresh/stale and permission states. Run all checks and compare bundle/request measurements.
Rollback: restore bootstrap consumer and provider/read-option changes; remove Query only if
unused. Risk: missing stable user ID means conservative clearing on every identity revalidation.

### PR 3 — Dashboard library queries and mutations

Depends on PR 2. Affected: dashboards-page-view/card, dashboard feature queries/mutations,
draft-save success integration. Migrate library/templates; templates enabled on demand if the
UX supports it. Catch pin/create/delete failures, explicit pending states and Sonner; target
library/detail invalidation. Start pessimistically. Pin retry=false despite PUT.

Tests: rapid pin, rejection with no unhandled promise, template success/failure, no library
reload unmount destroying editor, denied list, draft create/update routing. Preserve pinned-first
sorting/template private-copy behavior. Rollback this feature alone; keep scoped foundation.
Risk: mutation succeeds but follow-up refresh fails; show success plus stale view rather than
pretending the action failed and inviting duplicate creation.

### PR 4 — Runtime dashboard/widget/auth contracts

Depends on PR 1; coordinate with PRs 2–3 at the Api seam. Add Zod. Define version-aware
dashboard/query/layout/auth and discriminated widget schemas; infer types, retain nullable trust,
permission outcomes and provenance. Apply at saved-document/render/auth/SSE trust boundaries.
Use structured path/code diagnostics and safe user messages; no silent drop-to-empty behavior.

Tests: backend-compatible fixtures, current persisted version, unknown version, invalid nested
queries/layout, unknown renderer, legitimate null/zero/empty strings, denied widgets, optional
draft dashboardId. Evaluate OpenAPI export separately, without two handwritten contracts.
Rollback validation applications together with schema/type changes; never mutate stored
documents in this slice. Risk: stricter parsing reveals legacy data; document accepted versions
and explicit migration adapters before rejecting a historically valid document.

### PR 5 — Dashboard editor ownership and dirty protection

Depends on PRs 3–4. Add RHF + resolvers. Separate form, widget list/field array, persisted
preview, revisions and save/restore/delete actions. Query owns server snapshots, RHF owns edits.
Retain current grid and document payload. Label persisted preview; no new drag/drop/layout API.
Add dirty close/navigation guards, save-generation protection and restore/delete confirmations.

Tests: existing editor suite, dirty refetch, edits during save, failed load retry recovery,
preview/history failure not empty, duplicate/reorder/resize IDs, restore dirty confirmation,
permission placeholders and delete failure. Browser: create/edit/save/restore/delete and leave
dirty flow. Rollback editor composition/form adoption together; unchanged persisted format
makes rollback data-safe. Risk: concurrent editors have no proven version-conflict API; do not
claim optimistic concurrency protection beyond detecting remote refetch changes.

### PR 6 — Rules and reconciliation server-state/actions

Depends on PR 2 and failure conventions. Migrate rules/recompute/audit/products and record/
exceptions/provenance reads. Query mutations own pending/error; consume RuleEditor callback
rejections; preserve existing destructive-rule confirmation. Replace reload chains with feature
invalidation including affected resolved/business data. Inline persistent-view failures.

Tests: rule edit/delete failures, override conflict/permission/reason retention, recompute active
poll completion, audits failing independently, null unresolved values, no automatic write retry.
Rollback rules and reconciliation in independent commits. Risk: asynchronous recomputation
means invalidation alone does not guarantee immediately updated resolved values.

### PR 7 — Explorer decomposition and shareable query semantics

Depends on PR 2. Split raw/canonical/resolved files, URL parse/serialize and reusable results/
pager/inspection UI. Begin with native Next router, explicit Apply and validated URL values.
Query keys include applied filter/entity/page/size. Reset page on filter/entity changes; browser
back/forward replays state. Label current-page sorting or disable it. Keep descriptors separate
from inferred row keys; absence visibly distinct from empty strings and zero.

Tests: deep links, Apply/page-reset, back/forward, entity changes and late page responses,
empty/error/permission states, missing-value rendering, raw details/provenance and keyboard
inspection. Do not add unsupported server sorting/filtering. Rollback page composition and URL
changes together. Risk: actual metadata has no column schema; typed/schema-complete exploration
requires separately coordinated backend work. nuqs only after demonstrated boilerplate benefit.

### PR 8 — Remaining reads, connector invalidation and shell deduplication

Depends on PR 2; precede this with affected-domain invalidation decisions from PR 6. Migrate
sales/reservations/staff/kitchen/data-health/logs/invoice/provenance/pipeline and shell. Remove
manual focus listeners in favor of auth-aware query lifecycle. Count helpers expose partial
failure diagnostics, not success-shaped missing business data. Retire useApiData only when
the consumer inventory reaches zero and injected Api tests still work.

Tests: connector business-status failure vs transport failure, upload failure, active completion,
shared shell/page dedupe, dependent graph reads and null data. Measure live controlled request
counts before/after. Rollback one feature at a time; keep hook until its last consumer is gone.
Risk: pipeline count fanout still needs distinct endpoint calls; Query cannot eliminate requests
for different keys or make row-query counts cheap on the server.

### PR 9 — Cancellable chat controller and persisted conversations

Depends on PRs 2 and 4. Keep native SSE and local token buffer. Introduce lifecycle, abort,
generation fence and reader cleanup. Query owns history/list/metadata and mutations; never tokens.
Validate answers/widgets with structured diagnostics; preserve trusted registry and draft update
routing. Cancel active thread before deletion/reset/scope replacement.

Tests: delayed old callbacks/finally after reset/new turn, unmount, abort without toast, split
LF/CRLF chunks, malformed frames/content type, thread completion invalidation, conversation
rename/delete errors. Browser regression for cancel/reset and save draft. Rollback controller/
hook changes together. Risk: browser abort does not prove backend generation stops; no automatic
chat retry or server cancellation endpoint is invented.

### PR 10 — Demonstrated presentation consolidation

Depends on migrated affected views. Consolidate raw/log table patterns where behavior matches,
shared inspector/pager/state feedback and accessible controls. Reuse current formatters, cards,
widgets and tokens. Preserve density/responsiveness/role presentation; no design-framework add.

Tests: keyboard/focus/labels, null/zero/empty, role-sensitive views; browser screenshots at
desktop/mobile. Rollback individual shared-pattern extraction. Risk: overgeneralizing different
tables; keep lightweight widget tables if their semantics differ.

### PR 11 — CI, monitoring privacy and measured performance

Test integration begins earlier; this slice completes `ci.yml` Vitest and Playwright jobs,
browser fixtures/artifacts and privacy/reporting checks. Preserve self-hosted relay. Add
safe correlation tags and single-owner async reporting. Review 100% trace sampling and query
parameter collection against actual deployment load/privacy needs; do not change blindly.
Measure route bundles, requests, load/interaction/long tasks before selecting graph/editor
dynamic imports. Keep React Flow; no layout package without a requirement.

Tests: reporting dedupe, sensitive-content exclusion, identity cleanup, relay regression,
high-value E2E for auth/dashboard/editor/explorer/rules/chat. CI uses frozen Bun install and
matched Playwright browsers; production mode for measurements. Rollback CI/observability/
performance separately. Risk: demo tests are not live authorization evidence; retain backend
security tests and authenticated integration fixture coverage.

## Every PR review record

Include problem/affected files, evidence/root cause, solution and rejected alternatives,
dependency changes, preserved/intentional behavior, tests added, commands and actual exit
results, migration/rollback steps and residual risks. Update `frontend-verification.md` with
counts and matching environment/sample methodology. Never describe proposed improvements as
measured wins. Keep commits concern-specific and task branches short-lived; no direct main commit.

## Completion accounting

Use `git diff --numstat <audit-base>...<migration-head>` plus explicit infrastructure file
list to distinguish removed mechanisms from moved lines. Record consolidated pattern IDs,
dependency lock diffs and same-fixture HTTP counts. Compare production browser medians and
route build sizes at both refs. Coverage percentages require an actual coverage run; a passing
test count is not coverage. List remaining contract, concurrency and metadata debt explicitly.
