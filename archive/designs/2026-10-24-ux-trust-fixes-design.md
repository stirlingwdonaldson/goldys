# UX trust corrections

Date: 24 October 2026. Baseline: `bc96d7aa` (main, PR #216).

The UX audit found that existing screens can conceal result limitations, confuse
collection activity with evidence freshness, replace unknown subcounts with zero,
and imply that bounded/failed work discovery establishes an all-clear. Home also
labels an attendance proxy as covers and invoice purchases as COGS.

Correct those existing presentations before adding decision workflows. Rostering
is excluded. The implementation uses current frontend API fields and shared
components; it adds no endpoint, metric, permission, schema, connector or source
write. Architecture remains governed by `docs/system-context.md` and `docs/prd.md`.

## User correction — 24 October 2026

The user confirmed that the Settings boolean for hiding verbose incomplete-data
output is intentional and requested its restoration. This supersedes the first
behaviour item and its always-visible acceptance criterion below. Restore the
original switch, default-off browser preference and notice-card matching from
PR #212; preserve the audit's other trust, error, pagination and label fixes.

## Behaviour (original audit implementation)

- Render every returned qualification beside its result. Remove the preference
  that hides incomplete notices, including its provider and Settings control.
  The legacy browser key can remain inert; it no longer affects rendering.
- Label the header as collection activity. Count only recorded SUCCESS and
  NO_NEW_DATA runs as completed without errors; disclose individual statuses.
  Never infer result coverage, effective date or fitness from the latest run.
- Use existing null-aware formatters for unknown booking/no-show subcounts.
  Label the Home reservation measure as an attendance proxy.
- Show invoiced purchases and recorded waste on Home using the same meanings as
  Kitchen. Do not present purchases as consumed cost or compute food-cost %.
  Align the existing synthetic demo bootstrap with the Kitchen fixture.
- Retain bounded queue reads. Expose unavailable checks and server totals beyond
  the returned first pages in both My work and Home. Counts describe shown items;
  empty/zero states never imply that all sources were checked or figures verified.

## Scope and limits

The queue remains a signal browser, not assigned tasks or verified follow-up.
Its first-page limit remains explicit; fetching every page and fixing the
Reconciliation screen's separate first-page assumption are follow-up work.
Connector states do not establish evidence freshness. No role/context redesign,
new financial meaning, staffing/roster feature or source discovery is included.

## Verification

Update existing qualification, Home, KPI and zero-decision tests; add mixed-state
collection and failed/truncated queue cases. Update the existing browser regression
so the old hide preference, reload and navigation cannot conceal qualifications.
Run the full frontend gate and controlled desktop/phone checks before finishing.
