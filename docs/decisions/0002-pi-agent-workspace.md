# Pi agent workspace for Ask Goldy's

**Accepted with the owner on 15 November 2026; target behavior, not implemented.** Following the migration research and a decision interview, adopt Pi for Ask Goldy's inside the existing website. Pi owns agent behavior and native session semantics; Spring retains application authorization, governed business truth and durable application records. Use a thin worker adapter and separate analysis VM so we gain Pi's existing session/context capabilities without recreating them in Java or granting generated code production authority.

The owner confirmed the final agreement after explicitly choosing Pi regardless of comparative benefit and accepting simpler crash recovery. This authorizes updating the proposal and decision record only. It does not authorize implementation, infrastructure provisioning, production access, pushes, merges or deployment. The [migration proposal](../design/ask-goldys-pi-workspace-proposal-2026-11-15.md) supplies the change inventory, delivery slices and verification plan; engineering details there remain proposed unless confirmed below. The [governed-planning decision](0001-governed-conversational-planning.md) and existing business-query restrictions remain in force.

## Responsibility and isolation

- **Pi:** model/tool investigation loop, native conversation tree, model context, compaction, events, active-session follow-up and cancellation primitives. Pi adoption is fixed; a pinned-release compatibility spike establishes integration details and readiness rather than choosing between Pi and Spring AI.
- **Spring/Goldy's:** authentication, `PermissionService`, governed queries/calculations, user/job ownership, durable submission/status and budgets, storage of Pi-native snapshots, artifact access/retention and current-permission checks. No second Java context/summary engine or independently maintained transcript. Pi-native state is stored through a thin adapter; using PostgreSQL for that storage does not make Spring the owner of session semantics.
- **Analysis:** containers on a separate VM in the same Proxmox environment, potentially on the same physical server as production. The exact sandbox runtime remains subject to testing; ordinary container packaging alone is not proof of containment. Shared hardware retains a shared capacity/failure domain.
- **Production data:** access only through Spring's permission-checked tools and governed semantic/query services. Pi and the script sandbox have no direct database connection/credentials, source credentials or deployment authority. Do not introduce generic AI SQL or unauthorized raw/canonical reads.
- **Read-only business behavior:** the agent cannot mutate application records, grants, source systems or application code. It may write analysis scripts, temporary workspace files and generated artifacts; Goldy's may persist conversations and job/file records. Governed dashboard saves remain explicit user actions under existing authorization. Application-code changes remain later owner/GM-only scope with separate release authority; that scope grants no salary access.

## Product and data handling

The first release supports weekly investigations over authorized Goldy's data, comparisons of uploaded files with authorized application data, and downloadable traceable reports/spreadsheets. Authoritative metrics keep their existing calculations. Exploratory calculations identify their inputs, formulas, assumptions and limitations. Existing visual direction, widgets and query-only saved dashboards remain.

Real authorized application data and necessary uploaded content may be sent to the configured model provider for analysis. Disabling business writes is not a disclosure control: minimize payloads, enforce current permissions and verify actual provider configuration/handling without assuming zero retention. This is a product permission, not permission for the documentation task to make live calls. Provider processing does not authorize sending private records to search engines or arbitrary websites.

General web research is included through controlled public-only search/fetch tools. Retrieval receives public research requests rather than private conversation/data; scripts remain offline. Constrain outbound queries/URLs and public-network destinations, isolate untrusted retrieval/parsing, preserve citations and reject uncertain requests for public-only reformulation. Prompt-injection resistance rests on restricted tools and egress, not prompts alone; it cannot guarantee that every malicious source is detected or that answers cannot be misled.

## Attachments, artifacts and permissions

Private exploratory attachments are temporary workspace inputs, distinct from ingestion into the authoritative source model. Original attachment bytes remain intact while retained. Promotion is a separate authorized ingestion workflow that writes the byte-faithful original to the immutable raw ledger. This is a narrow target-architecture clarification to the existing every-ingested-payload rule; it does not permit mutation/deletion of source-ledger records or bypass the business read direction. Implementation must align the affected invariant/contract documentation with this decision before shipping the new path.

Upload ownership does not grant rights to sensitive contents. Existing pay/personal-data restrictions still apply to supplied files; unknown sensitive content requires approved classification/reviewer policy before manager model processing. Neither the uploader's assertion nor a model's classification alone is an authorization bypass.

Initial inputs: PDF, PNG/JPEG, DOCX, XLSX, CSV/TSV and plain text, including extraction from scans/images. Unreadable or uncertain extraction is visible and never filled with invented values. Initial outputs: PDF, PNG, DOCX, XLSX and CSV. Reject macros, executables, password-protected files and legacy Office formats initially, with useful conversion guidance.

Conversations, uploads and generated artifacts are private to their owning user initially. No artifact-sharing links or automated sending; existing dashboard sharing retains its own rules. Current permissions govern historical content, summaries, previews, context and downloads. If old data dependencies cannot be established, restrict the affected history rather than leak it. Downloaded files cannot be recalled after revocation.

## Confirmed provisional limits and retention

These starting values are adjustable by explicitly authorized users; the agent cannot increase its own budget. They are limits, not estimates of actual usage or hosting cost.

| Item | Initial agreement |
|---|---|
| Input size | 25 MB per file; five files and 100 MB total per investigation |
| Processing size | 100 PDF pages or 100,000 spreadsheet rows; visible limit/partial states, no silent truncation |
| Investigation budget | A$5 model/search charges and 15 minutes |
| Daily budget | A$50 model/search charges across the venue; image extraction, retries and summarization count; infrastructure hosting excluded |
| Attachments/artifacts | Expire after 30 days unless explicitly kept; expiry is visible and Keep is permission-checked |
| Conversations | Remain until deleted; old transcripts may identify expired source files/downloads |
| Deletion | Remove workspace content subject to approved holds/issued-report obligations and backup expiry; kept files do not bypass conversation deletion or current permissions |

## Execution, recovery and rollout

Browser disconnect or drawer closure does not cancel the investigation; reconnect restores progress. Stop cancels active work and all queued follow-ups, preserves completed results/artifacts and labels incomplete findings. It must stop server/sandbox work, not merely the browser stream. Messages entered during work wait visibly and can be edited/removed before execution.

A worker crash produces a visible **interrupted** investigation. Preserve saved history and completed artifacts, then offer **explicit user retry**. No automatic mid-investigation replay in release one, no custom tool-by-tool recovery journal and no Pi Durable dependency. Pi supplies active-session behavior; a small surrounding application layer retains locking, duplicate-submission protection, revocation, budgets and stale-worker publication protection. Session snapshots are not running-process checkpoints.

Pilot with existing owner accounts first, using permitted real data after implementation safety checks. Manager rollout is blocked on an explicit department/seniority/data/export permission matrix. Until separately revised, managers receive no pay-derived information, including wage totals and labour-cost ratios. Owner/GM titles grant no additional data authority by themselves. New permission resources remain owner-only through explicit rows, not a hierarchy bypass.

## Consequences and remaining decisions

This decision replaces the research's conditional Pi-selection recommendation, synthetic-only approval gate, deferred image/OCR scope, 90-day conversation-retention suggestion and automatic crash-replay design. Historical research notes remain evidence rather than current policy. Retain Spring AI for unrelated invoice/OCR/classification consumers until separately migrated; retiring Ask's old loop does not authorize deleting that shared dependency.

Still required: manager permission matrix; exact sensitive-file classification/reviewer grants; issued-report/hold/backup retention and recovery targets; later application-code release authority. Engineering work must verify the pinned release, sandbox/runtime/Proxmox isolation, configured model/search handling and pricing, extraction quality and resource capacity. Acceptance tests remain mandatory; this decision is not production readiness. Existing production safeguards and reporting-first priority continue to bind.
