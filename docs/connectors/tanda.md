# Tanda connection and validation

Owner direction, 26 November 2026: Tanda replaces Deputy. No new Deputy ingest is
accepted (the old webhook returns 410). Retained historical evidence is unchanged.

## Implemented and not yet accepted

The read-only `tanda-api` connector captures source bytes through `ConnectorRunner`
and the immutable ledger. Data health offers the existing owner-authorised Run now
action. Staff & labour links to connection health and explicitly awaits validated
Tanda figures. This is **capture readiness, not completed workforce reporting**.

Public schema access was verified on 26 November 2026 at
[Tanda OpenAPI](https://my.tanda.co/api/v2/openapi), linked by the
[official reference](https://my.tanda.co/api/v2/docs). No account token or real account
samples were available during implementation. Envelope tests are synthetic transport
tests, not a measured parser hit rate. Canonical publication remains blocked by
`TANDA_MAPPING_PENDING`; even a fully captured run is PARTIAL, never a reporting success.

## Secure token entry

Run interactively on the backend host, as the account that will own the mounted secret:

```sh
python3 ops/tanda-token.py ~/.config/goldys-secrets/tanda-token
```

The prompt hides input. The script creates a 0700 directory and a 0600 file outside
this checkout, refuses unsafe directories and existing destinations, and never
prints the token. It does not encrypt the file: protection is operating-system
permissions and the host's disk protections. Use a new filename for rotation.
Do not put the token in chat, command arguments, source control or browser storage.
The agent must not read this file after entry.

Deployment configuration must mount this file read-only into the backend container
and set `TANDA_TOKEN_FILE` to its **container-visible** path. The backend process
must own/read the 0600 file. Only the path goes into configuration. This change
does not edit or operate production Compose; deployment needs separate approval.
Once configured, open Data health → Tanda → Run now. A restart is needed if changing
the configured path, but replacing a token at the same path is read on the next run.

## Capture contract and limits

- Fixed HTTPS origin, bearer header, GET only, no redirects, connection/request timeouts.
- Locations; paged teams; active/inactive staff with wages; schedules including cost
  and award interpretation; leave including inactive staff; timesheets with costs,
  oncosts, award interpretation and variance. Timesheet reads explicitly request
  `skip_status_update=true` to avoid Tanda's incidental status update behaviour.
- Default window: 30 calendar days ending today in Australia/Melbourne. Rosters and
  leave also include the next 14 days. `TANDA_HISTORY_DAYS` accepts 1–366. This is
  not an all-history backfill, incremental cursor, payroll export or live roster feed.
- Pay-period timesheet envelopes are fetched for each date; repeated envelopes remain
  evidence and must never be added as daily figures. Source IDs/date metadata are retained.
- Pages request 100 records; a short page terminates. Maximum 1,000 pages per endpoint,
  16 MiB per response. Requests paced at 400 ms; a shared-account 429 stops with a
  visible partial/failure, without silently skipping pages or retry storms.
- Raw bytes are persisted before array-shape inspection. Each record retains endpoint,
  query dates and page as source reference. Errors omit source bodies and credentials.
- Manual capture only while validation is outstanding. No automatic production job.

## Required completion evidence

After secure setup, capture a representative completed week and an upcoming roster.
Measure accepted/rejected records against real sanitised fixtures before writing
canonical mappings. Confirm pay-period boundaries; shift/schedule IDs and linkage;
paid/unpaid breaks; overnight and DST dates; approvals/deletions/corrections;
source costs versus wages, super and other oncosts; and coverage of inactive staff.
Explicitly map Tanda location/team IDs to the pub and approved FOH/BOH reporting
without guessing from names. Keep wages and full employment costs separate.

Then implement canonical → resolved → semantic reads for staff/shift detail and
period comparisons, extend typed live/demo APIs and the workforce UI, and validate
weekly totals against Tanda. Explainable review hints should include roster-hour
variance, incomplete/open shifts, approval backlog and missing-cost coverage; label
thresholds and evidence, never infer award breaches or staffing adequacy from totals.
Leave balances, availability, qualifications and payroll-specific datasets require
scope/schema review before extending capture. None are represented as delivered here.
