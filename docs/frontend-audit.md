# Frontend audit

## Scope and evidence

Audited 2026-10-09 against local HEAD `f6140b8e2fa13d0c26f1e168c2dfe0bc689e0a18`
(`chore: point Sentry example URLs at the ops-sentry VM (.41)`). This is the checked-out
commit, not a claim that GitHub main was synchronized. Audit branch:
`chore/frontend-modernisation-audit`. Existing untracked files were present before the audit.

Scope: `frontend/`; backend source inspected for contracts and authorization. No product
code or dependencies changed. Target architecture and migration documents are proposals
for review. Implementation and live regression verification remain outstanding.

Evidence categories: **source** establishes an implementation fact; **executed check**
establishes a result in this environment; **browser** establishes observed demo behavior.
Source analysis does not establish request savings or production performance.
See [verification](frontend-verification.md) for commands, environment and measurements.

## Findings revalidated

Severity: high = data integrity, identity isolation or lost edits; medium = reliability or
maintenance cost; low = bounded inconsistency. IDs F09–F12 below name the four foundation
concerns because the brief did not assign individual names to those IDs.

| Finding | Severity / status | Verified evidence and consequence |
| --- | --- | --- |
| F01 Server state | Medium; confirmed | `frontend/lib/use-api-data.ts:15–61` holds per-instance data/loading/error, clears data on every reload, and cancels only publication with a boolean. It does not abort HTTP, deduplicate, cache, or invalidate by resource. 37 production call sites in 17 consumer files. |
| F02 Async errors | High; confirmed | `components/dashboards/dashboards-page-view.tsx:46–60` pin/template handlers have no catch; editor `:238–249` deletion has no catch and revision failure becomes `[]`. `components/rules/rule-editor.tsx:91–99` ignores the async save callback. Logout's `finally` refresh does not consume rejection (`components/app-shell/user-menu.tsx:24–29`). |
| F03 Transport/contracts | High; confirmed with existing safeguards | `lib/api/client.ts:19–22` casts HeadersInit to a string record; `:37` supports 204, `:39` casts JSON, `:43–64` normalizes parse errors but loses status, causes and response-header correlation. Validating code/message alone does not validate envelope fields. Abort would become NETWORK_ERROR. Redirected HTML becomes a parse error. |
| F04 Editor ownership | High; confirmed | `components/dashboards/dashboard-editor.tsx` is 585 lines; fetching, preview, form, widget manipulation, revisions and actions share one component. No dirty-state navigation protection. Save reapplies returned data while inputs remain editable (`:214–235`), risking loss of edits made during save. Restore overwrites local fields. Retry succeeds without clearing loadError (`:317–325`). |
| F05 Explorer semantics | High; confirmed, partial decomposition already present | `app/(app)/data/page.tsx` is 401 lines, with separate Raw/Canonical/Resolved functions in the same file. URL params initialize state only (`:33–41`); controls do not update URLs. Raw text filters fetch per keystroke (`:94–117`) without resetting page. Entity/domain changes already reset page (`:290–293`, `:357–360`). `components/data-explorer/generic-table.tsx:16–24` infers columns from sampled rows and replaces absent values with empty strings. Sorting is local to the current page, without a visible scope label. |
| F06 Streaming lifecycle | High; confirmed | `components/ask-goldys/use-ask-goldys.ts:40–69` reset does not stop or fence callbacks; a replaced/reset stream can append old text and set working=false on a newer turn. `stream.ts:190–251` has no signal, content-type verification, reader cleanup, structured failure or explicit lifecycle. Its parser assumes LF SSE delimiters. |
| F07 Visual consistency | Medium; partially confirmed | Shared DataTable, tokens, form primitives, cards, state components and Sonner already exist. Raw explorer and logs render custom tables (`data/page.tsx:179`, `logs/page.tsx:56`); widget table is another deliberate bounded presentation (`widgets/table-widget.tsx:17`). Explorer native selects differ from Radix controls. This supports targeted consolidation, not replacing the design system. |
| F08 Widget trust boundary | High; confirmed with strong existing foundation | `components/widgets/parse.ts:74–123` checks schemaVersion=2 and type, but malformed nested values become null/empty or are dropped without diagnostics. `ask-goldys/stream.ts:25–119` defaults invalid layout to zero and silently drops invalid draft queries/widgets. Saved dashboard fetch/render responses bypass this parser. Keep the trusted registry (`widgets/registry.tsx:20–26`); never execute AI code. |
| F09 Observability | Medium; confirmed | Sentry client/server/edge configs and tested self-hosted `/monitoring` relay exist. React boundaries report exceptions; caught async errors generally do not. `instrumentation-client.ts:21` tracesSampleRate=1.0; `lib/sentry-options.ts:14–24` disables bodies, cookies, user info and AI content but permits query params. Privacy must account for free-text filters. |
| F10 Authentication freshness | High; confirmed | `components/app-shell/current-user-provider.tsx:44–56` never clears user on failure and treats every UNPARSEABLE_RESPONSE as signed out, including invalid success JSON and proxy/server errors. No generation/abort guard. Role-sensitive header reads user even without authenticated status (`app-header.tsx:22,59`). Api identity remains the live singleton when users change. |
| F11 Regression/CI | Medium; confirmed | 43 Vitest files / 186 tests pass after dependency restoration. `.github/workflows/ci.yml:43–45` runs typecheck/lint/build but omits tests. MSW and Playwright are not declared; transport/auth-provider and cancellation race coverage are absent from the test-file inventory. Existing editor tests cover happy paths and permission placeholders. |
| F12 Performance integration | Medium; confirmed opportunity, no savings claimed | `dashboard/page.tsx:17–18` already uses bootstrap; preserve this. Shell separately reads summary/connectors (`shell-status.tsx:29–30`); data-health repeats them (`data-health/page.tsx:34–35`), logs repeat connectors. Pipeline performs `4 + sources + canonicalEntities + resolvedDomains` reads (`pipeline-map.tsx:32–43`). No `next/dynamic` calls found in frontend TSX. Bundle baseline in verification. |

Paths in the finding table that start with `app/`, `components/` or `lib/` are relative to
`frontend/`. Lines refer to the audited commit.

## Feature and dependency boundaries

- Delivery: Next App Router `frontend/app/(app)`, root typography, app-shell and auth routes.
- Identity and mode: current-user provider; demo-mode provider selects stable `Api` adapters.
- Business reads: Overview/bootstrap, sales, reservations, labour/staff, inventory/kitchen.
- Operator workflows: reconciliation, resolution rules, connectors/data health, logs.
- Inspection: raw/canonical/resolved explorer, invoice graph, pipeline, provenance.
- Reporting: saved dashboards, revisions, templates, widget renderer and Ask Goldy's SSE.
- Persistence: Spring services and PostgreSQL; browser has no authoritative business storage.
- Shared presentation: `components/ui`, states, data-table, layout, data-display, formatting.

Ripwire `ripwire frontend --report`: 217 indexed files, 819 symbols, 501 edges, 112 call-graph
clusters and no detected cycles. Many symbols are isolated: this map is a navigation aid,
not proof of full import-graph coverage. High-leverage contracts: widgets/types, api/types,
current-user-provider, api/client, chat stream. Dependency inventory:
[dependency decisions](frontend-dependency-decisions.md).

## Complete useApiData consumer inventory

Production call sites only; paths relative to `frontend/`. Callback indirection in drill-in
and compound pipeline reads are included. The hook definition is excluded.

| File | Call-site lines | Resources |
| --- | --- | --- |
| `app/(app)/dashboard/page.tsx` | 18 | bootstrap |
| `app/(app)/data/page.tsx` | 103, 242, 261, 265, 327, 331 | raw list/detail, canonical descriptors/rows, resolved descriptors/rows |
| `app/(app)/data-health/page.tsx` | 34, 35, 36 | connectors, summary, activity |
| `app/(app)/kitchen/page.tsx` | 67, 68 | inventory lines/summary |
| `app/(app)/logs/page.tsx` | 24 | connectors |
| `app/(app)/reconciliation/page.tsx` | 48, 49, 50 | daily/product exceptions, audit |
| `app/(app)/reservations/page.tsx` | 70, 71 | summary, covers |
| `app/(app)/sales/page.tsx` | 36 | daily sales |
| `app/(app)/staff/page.tsx` | 17 | labour summary |
| `components/app-shell/shell-status.tsx` | 29, 30 | summary, connectors |
| `components/conversations/conversations-screen.tsx` | 47, 250 | thread list/history |
| `components/dashboards/dashboards-page-view.tsx` | 39, 40 | library, templates |
| `components/inventory/invoice-graph-view.tsx` | 29, 34, 42 | suppliers, invoices, lines |
| `components/pipeline/pipeline-map.tsx` | 61 | compound pipeline reads |
| `components/reconciliation/drill-in.tsx` | 37, 38 | passed record fetcher, rules |
| `components/rules/resolution-rules-content.tsx` | 36, 37, 38, 39 | rules, recompute, audit, products |
| `components/trust/provenance-view.tsx` | 23 | metric/date provenance |

These are **17 consumer files** and **37 call sites**. The hook itself makes the eighteenth
file in a text search including its definition.

## Direct calls, async state and error-handling inventory

All production transport owners: `lib/api/client.ts:28` (ordinary browser fetch),
`components/ask-goldys/stream.ts:196` (SSE browser fetch), and
`app/monitoring/route.ts:62` (server Sentry relay). `lib/api/live.ts:36–172` maps the Api
methods; `lib/api/provenance.ts:10` and `invoice-graph.ts:9,20,29` are live read helpers.
`lib/api/index.ts:6,11,27,37,45` defines profile, health, signup, login and logout.
The interface is `lib/api/types.ts:440–503`; demo implementations are in `lib/api/demo.ts`.

Manual async workflows outside useApiData's reads:

| Owner | Direct calls / error paths | Existing pending and feedback |
| --- | --- | --- |
| `current-user-provider.tsx:39–57` | getCurrentUser; promise catch | status/error/user; stale user retained |
| `user-menu.tsx:24–29` | logout; finally without catch | no pending/failure UI |
| `app/login/page.tsx:29–32`, `app/signup/page.tsx:30–35` | login, signup then login; catch | local form pending/error; retain |
| `dashboards-page-view.tsx:46–60` | toggle pin, create template; no catch | creating flag only; manual list reload |
| `dashboard-editor.tsx:164–201,214–266,324` | detail/render effects; update/delete/revisions/restore/retry | local loading/preview/saving/saveError/savedAt/revisions; generic or swallowed errors |
| `data-health/page.tsx:43–80` | runConnector, uploadCsv; catch | running/uploading flags, Sonner; manual connector + shell refresh |
| `reconciliation/page.tsx:99–124` | saveOverride/saveProductOverride; catch | saving + Sonner; three reloads + shell refresh |
| `resolution-rules-content.tsx:48–69,133–137` | delete/save rule | delete pending/confirmation/toast; save callback rejection uncaught in RuleEditor |
| `conversations-screen.tsx:112–140` | rename/delete thread; catch | saving/error and confirmation; deletion only resets pending on failure |
| `ask-goldys/answer-block.tsx:84–109` | updateDashboard/saveDashboard; catch | saving/saved/error; no library invalidation |
| `ask-goldys/use-ask-goldys.ts:26–69` | streamChat; catch/finally | working/error/token fields; no cancellation fence |
| `pipeline-map.tsx:11–52` | descriptors, raw counts, rules, canonical/resolved counts | intentionally partial result via orNull; errors lack per-resource diagnostics |

Component names above are relative to their feature directory under `frontend/components`;
page paths are relative to `frontend`. Hook reads and drill-in callbacks are in the preceding
inventory. All catch handlers also include transport JSON/network catches, raw payload
pretty-print fallback (`data/page.tsx:395–400`), widget/answer/SSE parsing (`stream.ts:209–238`),
and relay envelope/network handling (`monitoring/route.ts:20,42,78`). React exception reporters:
`app/global-error.tsx:13`, `app/(app)/error.tsx:20`, `components/states/error-boundary.tsx:28`.
These are separate from caught action errors. No Sentry.setUser usage was found.

## Demonstrated shared-pattern candidates

1. Per-resource read state: 37 hook instances plus editor and authentication effects.
2. Action pending/error/reload sequences: library, editor, rules, connectors, overrides,
   conversation actions and draft save. Use Query mutations directly with feature ownership.
3. Canonical/resolved explorer sections: same descriptor selection, loading/error/empty,
   generic-table and pager flow; extract a shared results/pager pattern after separating files.
4. Explorer native form controls vs existing Input/Select/Label conventions.
5. Three direct HTML table presentations plus shared DataTable: raw/log table consolidation
   is plausible; do not force widget tables into a heavyweight interactive table.
6. Two identical CSRF cookie readers (`lib/api/client.ts:5`, `ask-goldys/stream.ts:172`).
7. Handwritten widget/query parsing and TypeScript interfaces; consolidate high-risk schemas.

These are candidates, not a count of completed consolidations. Cards are often legitimate
feature-specific compositions. Existing formatter and state components should be reused.

## Backend contracts relevant to migration

Source verification, not a live authenticated response capture:

| Boundary | Actual backend contract | Migration implication |
| --- | --- | --- |
| Authentication | `config/SecurityConfig.java:35–89`: session form login at `/api/auth/login`, form email/password, CSRF cookie/readback for protected writes, logout 204. `:92–110` login returns profile or 401 INVALID_CREDENTIALS. | Preserve credentials same-origin and content types. Native fetch redirect handling must distinguish login HTML from JSON. |
| Current identity | `api/CurrentUserController.java:15–18`, `auth/StaffProfileSummary.java`: displayName/department/seniority only. | No stable subject identifier. Use explicit cache clearing and session generations; never display-name cache partitioning. External session replacement cannot be inferred from matching profile fields. |
| Saved dashboards | `api/SavedDashboardController.java:32–120`; `application/SavedDashboardApplicationService.java:454–492`: documents version/layout/queries/filters/visibility/pin/audit dates; render outcome widget or deniedResource with trust. | Validate documents and render specs separately. Pin is a toggle even though HTTP PUT; never retry automatically. Delete controller is void without explicit 204 annotation: support successful empty 200 for void endpoints too. |
| Permissions | SavedDashboardApplicationService `:316–358`: ownership, sharing/visibility and resource permissions; render applies per-metric READ checks (`:245–260`). | Preserve denied widgets and sharing. Client cache never authorizes; server remains authoritative. |
| Explorer | `api/DataExplorerController.java:37–84`: raw filters source/fetcher/method/from/to + page/size; canonical/resolved accept page/size only. `application/DataExplorerService.java:35–62`: all require connectors READ. | Server pagination exists, arbitrary sorting/filtering and SQL do not. |
| Explorer metadata | `semantic/EntityDescriptor.java`, `GenericRow.java`: descriptor id/label/placeholder; string-valued column maps, no typed column schema. `reconciliation/DataExplorerQueryImpl.java:88–103,127–139` fixed descending tradingDate and omitted null fields. | Render absence explicitly; cannot infer full schema from rows. Rich typed metadata requires a coordinated future backend contract. |
| Correlation | `api/ApiErrorResponse.java:9–13`: code/message/correlationId/fields; `config/CorrelationIdFilter.java:28–45`: X-Correlation-ID response header and Sentry tag. | Read header fallback even on malformed/empty body; retain status and original cause. |
| Chat/history | `conversational/ChatController.java`, `ThreadController.java`, `AnswerPayload.java`, `DashboardDraft.java`: POST threadId/message, SSE and persisted history; draft carries optional dashboardId. | Keep token state out of Query. Current frontend already carries dashboardId (`stream.ts:119`); old memory of its absence is superseded. |

Backend paths in this section are relative to `backend/src/main/java/com/goldys/platform`.
No Springdoc/OpenAPI dependency was found in `backend/build.gradle`, and no OpenAPI spec was
found in inspected backend configuration/source. Generation is a separate contract-export
evaluation; do not introduce a second handwritten contract set.

## Audit conclusions

Preserve the single bootstrap, typed Api seam, demo adapter, trusted registry, shared table,
formatters, role helpers, existing tests and Sentry relay. Prioritize identity/error/transport
correctness before broad query migration. Runtime validation needs diagnostics rather than
silently discarding malformed business data. Product performance benefits remain unmeasured.
