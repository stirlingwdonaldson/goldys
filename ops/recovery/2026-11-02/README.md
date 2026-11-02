# CTB failed daily pull — 2 November 2026

Prepared on `codex/ctb-failed-run`. Production investigation was read-only.
No source credentials or payloads are included. Publishing, merging, deployment
and the production recovery pull require explicit approval under AGENTS.md.

## Cause and scope

The 2 November 04:00 Melbourne run (`188a8c6d-f8c8-48b0-af8c-ab0e0a9a743f`)
finished PARTIAL at 04:34:36 after retaining 281 payloads. Seven code-less free
modifiers caused six entire sale-item days to be refused. The same identity
failure appears in 17 runs beginning 17 October. A separate temporary request
failure affected 11 September in this run; another run already retained its
180 source facts totalling 27723.2087.

All 12,795 sale rows retained by the latest run were inspected with read-only
SQL. Exactly seven have blank product codes; all seven satisfy the narrow
free-modifier predicate: report row ID, parent reference, description, quantity,
and explicit zeros for amount, cost/unit, food cost, total food cost, discount,
tax and unit price. Their bytes remain in the immutable raw ledger. They do not
create invented product identities or product financial facts. Pagination and
repeat checks include them, so excluding them cannot conceal a truncated report.
Coded rows still publish normally. Null descriptions remain null in the parser.

Transient I/O failures now retry at most three times, waiting
one then two seconds. Exhaustion still records a connector failure; permissions,
authentication, HTTP error responses and malformed source data are not retried.
HTTP errors cannot become empty successful reports; recipe error responses still
reach the raw ledger before validation. At a 45-second timeout,
a single request can now take up to about 138 seconds before failing.

## Recovery baseline

Comparison of every nonempty dated sale report captured by this run with current
`source_product_sale_fact` counts and source amount sums found only these gaps:

| Date | Retained rows | Raw-only free modifiers | Missing coded facts | Source amount |
| --- | ---: | ---: | ---: | ---: |
| 2026-08-07 | 154 | 1 | 153 | 23183.4710 |
| 2026-08-09 | 151 | 1 | 150 | 20232.4090 |
| 2026-08-15 | 197 | 1 | 196 | 52329.6400 |
| 2026-09-05 | 206 | 1 | 205 | 49115.2799 |
| 2026-09-22 | 110 | 1 | 109 | 9834.3800 |
| 2026-10-24 | 199 | 2 | 197 | 54533.9141 |
| Total | 1017 | 7 | 1010 | 209229.0940 |

These are CTB source report amounts, not additional governed revenue or a claim
of external completeness. The separate revenue and invoice pipelines continued;
the affected product observations are not evidence that headline sales were lost.
Five dates already have legacy, name-keyed product aggregates with matching source
amounts (7/9/15 August, 5/22 September), but no qualified site/code totals or
source financial facts. **24 October has neither legacy nor qualified product
totals.** Recovery supplies detailed facts and qualified totals, while the existing
projection compatibility rule prevents legacy totals being counted again.
Amounts/counts can change if CTB revises a report before recovery.

## Approved recovery sequence to execute

1. Publish the reviewed branch, open a PR and wait for CI; merge and deploy the
   approved backend image through the normal release procedure. No migration or
   production environment-file edit is required. Keep legacy replay flags off.
2. Use the existing authenticated **Run now** action for CTB
   (`POST /api/connectors/CTB/run`) once. Follow the returned run ID until terminal.
   Do not run a second Spring instance or perform manual canonical SQL inserts.
3. Require SUCCESS (or inspect every new PARTIAL failure). Verify the new raw
   report pages and request manifests, then compare current source facts against
   each report's coded row count and amount sum, including both September pages.
   Check the seven modifiers remain raw and create no empty-code mappings.
4. Verify canonical product totals and resolved projections after the normal
   `ProductSalesRecorded` event handling; compare by source site/code first.
   Name collisions may deliberately remain qualified/unavailable in resolved data.
5. Verify the next 04:00 Melbourne scheduled run succeeds. Preserve the old failure
   rows and PARTIAL status as history. Record actual run IDs and validation counts.

The normal pull covers today minus 90 days through today. It includes all six
blocked dates on 2 November. Recover by **5 November Melbourne**, inclusive, to
keep 7 August in that window. If recovery happens later, retain and use the saved
raw pages for a separately reviewed dated recovery; the existing source-fact-only
backfill skips multi-page reports and does not rebuild product totals, so it is
not a complete substitute. No source samples must be exported into this repo.

## Validation and limits

Regression coverage uses the already-committed real September two-page fixture,
plus field mutations and a local HTTP server. No new production payload was exported.
The production-source access path itself was not exercised with new credentials;
existing retained responses establish the real response shape. Transient failures
can still outlast the bounded retries and will remain observable.

The host root filesystem was 99% used with about 2.2 GiB available; /tmp writes
also hit a quota. This independently broke the shell sandbox and Mockito agent
creation. Tests use a worktree-local Java temporary directory. No infrastructure
or unrelated files were deleted. Review storage headroom before deployment.

### Final local checks

- `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=<worktree>/backend/build/ctb-test-tmp ./gradlew --no-configuration-cache test spotlessCheck`: **passed**, 1783 tests, 0 failures, 0 errors, 5 skipped. PostgreSQL integration tests used disposable Testcontainers databases.
- Focused CTB modifier, parser, connector, retry and recipe raw-byte regressions: passed.
- `git diff --check`: passed. Ripwire's contract check returned exit 0; its quality delta returned exit 2 with two findings (first: batch verbosity 73→97), and its test gate returned exit 4. These static gates are not claimed green; the local guard remains together rather than adding an abstraction solely to split its measured size.
- No frontend changes/checks, PR/CI, deployment, new source fetch or production recovery.
