# Synthetic Pi preview evidence — 16 November 2026

**Historical evidence:** observations below describe their original stage of work.
Later deployment and user-confirmed login/reporting are recorded in the
[current production follow-up](pi-production-followup-2026-11-16.md). Earlier “not deployed”, incomplete-slice
and unexecuted-CI statements are retained as history, not current status.

Local integration evidence; no production deployment or remote CI.

## Runtime image scans

| Image | Exact image ID | Critical/high | Other findings |
|---|---|---|---|
| worker | `sha256:1ac5f9c0d6f7f73b1698f0e8aee9e9f3e0e0b94c76e8ad3eacf5620368fb1879` | 0/0 | {'UNKNOWN': 3, 'MEDIUM': 16, 'LOW': 8} |
| analysis | `sha256:0d549da503c5c5c5bf9af77f983f938ebc663c8d7544ac4f9168ff9154f8a22f` | 0/0 | {'MEDIUM': 78, 'LOW': 10} |

Scanner: Trivy 0.76.2, pinned image `aquasec/trivy@sha256:8f6bb947dbc528902e11f2f5be52f7b519f6a39d5d148f41166f2040499768fb`. Scanned exported images with no Docker socket, application data or credentials mounted. Full JSON reports remain at `/opt/goldys-agent/scan/{worker,analysis}.json` on the analysis VM and `/tmp/goldys-pi-preview/{worker,analysis}-image-scan.json` on the development VM. Lower-severity findings are not waived.

## Observed live paths

- Real DeepSeek governed net-sales response: synthetic 2–8 November total A$9,100, with Spring provenance.
- Browser sign-in and saved investigation reconnection, including after the documented preview restart.
- Real file upload, bounded extraction, offline Python and private CSV download. Browser upload/follow-up/download/Keep reproduced sum 42.
- Real public search returned official `.gov.au` sources, persisted retrieval dates and cited answers. Free queries are disabled.
- Governed daily gross/net/GST CSV export registered as a private artifact.
- Browser explicit dashboard save from a real Pi draft. The model never writes dashboards.
- Browser Stop terminated an observed active sandbox in 4.2 seconds and cancelled a queued follow-up.
- Killing the worker container interrupted the attempt and removed its sandbox. Explicit browser Retry completed.
- A real investigation exhausted its request-reservation allowance; terminal BUDGET_EXHAUSTED preserved completed drafts and refused further model work.
- Isolated PostgreSQL backup restored into `goldys_pi_restore_verified`, with matching file-byte and native-snapshot hashes and request-ledger totals. This is synthetic DB restore evidence, not a production backup/hold policy.

## Containment and extraction

- IPv4, IPv6, DNS, private network, metadata and loopback/supervisor access denied inside gVisor.
- Filesystem, identity and environment isolation; output symlink/hardlink rejection.
- Memory exhaustion, file-size bound, deadline, disconnected caller/descendant termination and broker orphan cleanup.
- Full 100 MiB staged inputs and 100 MiB output verified under the 512 MiB broker limit, and a combined 100 MiB round trip through the actual worker under its 768 MiB cap. A 25 MiB artifact request parsed successfully and was rejected by stale-fence authorization.
- Synthetic CSV, XLSX, DOCX, PDF text and PNG OCR all recovered expected value 42; five generated output formats validated; CSV formula output rejected.
- No representative private business files were used; extraction accuracy on those remains unmeasured.

## Accounting

Each model/search dispatch first records its bound durably in Spring. SDK/agent automatic retries are disabled. Completion releases unused admission capacity; unknown/failed requests and interrupted attempts stay reserved. At the recorded checkpoint: A$30.852 reserved for 16 November, eight admitted jobs (five completed, one stopped, one interrupted, one budget-exhausted), three reconciled completions. Native snapshots contained 24 provider responses with reported usage: 69,988 input and 8,793 output tokens. These are reported token records and reservation reconciliation, **not provider invoice/FX reconciliation**. Admission was not reset to make tests pass.

## Verification and limitations

- Full backend: 2,067 tests, five skipped, zero failures/errors; Spotless passed.
- Worker: 20 tests plus type checking/build, also run inside the exact distroless runtime image.
- Python broker: four boundary tests; real VM acceptance above.
- Frontend: 847 tests, type checking, lint and preview build passed. Three browser fixtures passed (workspace and legacy Ask). Actual browser citations and conversation deletion also passed; stale access was denied.
- Ripwire quality-delta remains non-clean (73 gating findings at the final application-diff check); its name-based test gate reports 275 untested symbols and ambiguous reachability. Framework hooks produce false dead-code signals, but complexity/size findings also remain. Full functional suites are not a claim that these static gates passed.
- No manager grant, selective context declassification, production file policy, arbitrary research, general dataset export, owner acceptance, push/merge/deploy or remote CI.

See [preview operations](../../ops/pi-preview/README.md) for access, limits, stop/restart and logs.

## Signed-out access correction

A fresh browser opening `/dashboard` reproduced “Couldn't reach Goldy's”: `/api/me`
returned a 302 to `https://localhost:18443/login`, outside the frontend SSH tunnel.
Protected API requests now return JSON `401 AUTH_REQUIRED`, allowing the existing
frontend session gate to open its own login page. Login, CSRF and permission checks
remain in place. The preview backend was rebuilt and restarted with synthetic
fixtures restored.

The authentication integration regression passed. Actual Chromium checks on both
`http://127.0.0.1:3117` and `http://localhost:3117` passed fresh dashboard-to-login
navigation, sign-in, authenticated `/api/me`, and recovery after clearing cookies.
No additional model calls were needed. This verification does not cover the user's
browser or SSH client.

After this correction, the full backend suite passed: 2,068 tests, five skipped,
zero failures/errors; Spotless and the executable JAR build passed. Ripwire on this
increment reported no gating quality findings and an unchanged method signature.

## Ask entry-point completion

The preview now routes the existing Ask Goldy's header button, Ctrl/Cmd+J and
command-palette action to Pi. It no longer displays a competing Investigations
button. The default-off build retains legacy Ask, and legacy history remains
available. Legacy streaming requests accept JSON error responses as well as SSE,
so pre-stream failures retain their actual error code.

Verification for this correction:

- Frontend type checking, lint, all 848 tests in 147 files, and the live-preview
  production build passed. `TMPDIR=/tmp` avoids the expired agent temporary path
  that prevented the previous test run from executing tests.
- Two preview browser fixtures passed: legacy history authorization and the Pi
  workflow (single entry point, keyboard and palette navigation, reload, queue
  edits/removal, Stop, explicit retry and revocation). The legacy-engine composer
  fixture is explicitly skipped in Pi builds because its entry point is replaced;
  it remains enabled in default builds.
- The actual private website signed in with its synthetic account and submitted
  a fresh question through Pi, returning A$9,100 for 2–8 November. The completed
  answer survived reload; no browser JavaScript errors were observed. A typo in
  the verification script occurred after the live answer assertion; a corrected
  read-only recovery check verified the saved answer without another model call.
- The preview web/backend/tunnel and analysis worker/broker were active. Only
  the preview web process was rebuilt/restarted. No production service changed.
- Ripwire's frontend delta against the prior local commit reported zero quality
  regressions; WorkspaceDrawer's contract was unchanged. Its name-based test gate
  still reports 26 untested impacted symbols (25 in the blast radius); browser and
  unit evidence above does not make that static gate clean.

Backend/worker/sandbox code was unchanged in this correction; their earlier live
acceptance and test evidence was not rerun. The outstanding production limitations
in the preview operating guide remain applicable. Nothing was pushed or merged.
