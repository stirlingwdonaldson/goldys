# Tanda connection and validation

Owner direction, 26 November 2026: Tanda replaces Deputy. No new Deputy ingest is
accepted (the old webhook returns 410). Retained historical evidence is unchanged.

## Implemented reporting

The GET-only `tanda-api` connector retains source bytes through `ConnectorRunner`,
then validates the complete captured window before publishing canonical roster and
worked-shift observations. The labour projector supplies semantic reporting and the
Staff & labour page, including daily Tanda team figures and the next 14 days of rosters.
The page preserves the seven native teams; there is no FOH/BOH regrouping.

Real-account access was validated on 26 November 2026. A representative capture
contained 223 roster observations and 6 unique shifts inside the validation window:
229/229 mapped, including 3 shifts with unknown worked hours. Repeated pay-period
responses were deduplicated by shift ID. A subsequent full-window probe (28 October–10 December) retained 42 pages and
validated 239/239 observations: 223 rosters and 16 worked-shift records. The sanitised regression fixture retains
paid/unpaid breaks, open shifts, zero costs and pagination. These are recorded source
observations, not acceptance of complete payroll or weekly reporting totals.

The [public OpenAPI](https://my.tanda.co/api/v2/openapi) defines a maximum seven-day
difference for schedule date filters. Capture uses non-overlapping seven-calendar-day
windows; the original wide request returned HTTP 400 against the real account.

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
- Manual capture remains available. `TANDA_SYNC_ENABLED=true` adds startup and hourly
  refresh; disabled by default. The runner prevents overlapping runs.

## Rollout and rollback

V125 permits unknown hours and records the observation measure. After importing Tanda,
older binaries with the combined-only labour projector cannot safely rebuild these facts.
Keep the new projector when disabling sync (`TANDA_SYNC_ENABLED=false`); do not roll back
to the previous backend image after canonical publication. Refresh begins only after
startup projection rebuilding completes. A failed source capture leaves published data intact.

## Identity, hours and qualifications

- `tanda:schedule:<id>` and `tanda:shift:<id>` are independent source-qualified
  identities. Repeated pay-period envelopes select the latest shift observation.
- Roster dates use the configured venue zone; worked shifts use Tanda's explicit date.
  Paid breaks remain in worked hours; unpaid breaks are subtracted once. Open or
  missing endpoints yield unknown hours and cost, never zero. Source `cost` is used,
  not `cost_with_oncosts`; super and other oncosts stay excluded.
- Native team names come from Tanda team IDs. Unknown teams and multi-location accounts
  fail validation instead of guessing scope. Renaming a team updates its projections.
- Each fact references its retained raw page. A complete validated capture atomically
  supersedes facts no longer visible in the same bounded source window and reprojects
  old and new dates for corrections. Failed/partial captures preserve the previous
  published window; their retained bytes and failures remain available in Data health.
- The source includes pending shifts and explicit zero costs. These remain qualified
  observations; zero cost does not prove payroll completeness. Missing-day coverage and
  ratio qualifications remain in the semantic layer. FOH/BOH ratios remain unavailable
  when there are no source teams with those exact names.

Still outside this delivery: individual payroll/award advice, leave balances,
availability, qualifications and staffing-adequacy recommendations. Weekly reporting
acceptance needs comparison to an independently supplied completed Tanda payroll/week
export, particularly salaried employees with source-reported zero shift cost.
