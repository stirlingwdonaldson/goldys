# CTB recipe non-JSON response — empty POST body causes HTTP 411

**Date:** 2026-10-12
**Status:** implemented and verified live
**Findings addressed:** production-data audit F02 (`docs/audits/production-data/`)
**Branch:** `fix/ctb-recipe-non-json-isolation`

## Problem

The CTB web pull (connector `ctb-revenue`) runs every dataset inside one `CtbConnector.fetch()`
call, in order: daily revenue → sale items → invoices → sale-recipe links → recipes → stocks →
suppliers → stocktakes → wastage → stock orders → statements → reference data. At the recipe step
it calls `POST /RecipeBook/GetAllRecipes`, which returned a **non-JSON (HTML) body** on every
production run since 2026-10-08.

Because `CtbConnector.fetch()` had no per-dataset isolation, that one exception aborted the whole
connector. The datasets after recipes — stocks, suppliers, stocktakes, wastage, stock orders,
statements, reference data — were therefore **never** ingested: the raw ledger contained only
`ctb-revenue`, `ctb-invoices-ajax` and `ctb-sale-recipe-links`.

## Root cause

`CtbClient.getAllRecipes()` (and `getAllSuppliers()`, and the `GetAll*` reference-data calls) sent
an **empty POST body** (`post(path, "")`). Java's `HttpClient` does not send a `Content-Length`
header for an empty body, and **IIS/ASP.NET answers such a POST with `HTTP 411 Length Required`
and an HTML error page**. Jackson then failed to parse the HTML as JSON and reported the generic
"Non-JSON CTB response".

Every `Search*`/form-bodied call sends a non-empty body and worked; only the empty-body `GetAll*`
calls failed. That is why the failure always landed on recipes — it is simply the **first**
empty-body call in the sequence.

### Evidence

Production ledger (read-only, via `docs/audits/production-data/checks/probe.py`), all eight
`CONNECTOR_SCHEMA_MISMATCH` failures:

```
ConnectorFetchException: Non-JSON CTB response
  at ...CtbClient.parse(CtbClient.java:304)
  at ...CtbClient.page(CtbClient.java:232)
  at ...CtbClient.getAllRecipes(CtbClient.java:136)
Caused by: com.fasterxml.jackson.core.JsonParseException:
  Unexpected character ('<' (code 60)): expected a valid value ... line: 1, column: 1
```

Failures at 04:46, 05:12, 06:31, 10:54 (manual runs, 2026-10-08) and 17:21–17:22 (the daily run,
2026-10-08→10-11): not time-of-day specific.

A faithful replay of the connector's whole sequence through the **real** `CtbConnector` +
`CtbClient` (Java) failed on **exactly** the empty-body endpoints — `ctb-recipes`,
`ctb-suppliers`, `ctb-reference-data` — while every form-bodied endpoint (including stocks,
stocktakes, wastage, stock orders, statements) succeeded. A fresh-session probe isolated it:

```
EMPTY  /RecipeBook/GetAllRecipes     -> HTTP 411 text/html ... <TITLE>Length Required</TITLE>
EMPTY  /Supplier/GetAllSuppliers     -> HTTP 411 text/html ...
keyword=/RecipeBook/GetAllRecipes    -> HTTP 200 application/json
x=1    /RecipeBook/GetAllRecipes     -> HTTP 200 application/json
```

(The Python research client sent `Content-Length: 0` explicitly, so it never hit this.)

## Fix

### 1. Always send a non-empty form body (root cause)

`CtbClient.requestBody(form)` substitutes `keyword=` when the form is empty. `POST
/RecipeBook/GetAllRecipes` with `keyword=` returns the full recipe list (the `GetAll*` actions
ignore the parameter). This is applied centrally in `CtbClient.post(...)`, covering recipes,
suppliers and all reference-data endpoints.

### 2. Preserve the response evidence (diagnosability)

`CtbClient` now carries the HTTP status and content-type alongside the body and, on a JSON parse
failure, reports them and a bounded body prefix:

```
Non-JSON CTB response from RecipeBook/GetAllRecipes (HTTP 411, text/html; charset=us-ascii, 445 chars): <!DOCTYPE HTML …
```

This mirrors the existing `IsSuccess:false` path. It would have identified the 411 page directly
from the ledger rather than a bare "Non-JSON CTB response".

### 3. Isolate dataset attempts (stop the bleed)

`CtbConnector.fetch()` wraps each dataset in a `pull(dataset, failures, attempt)` helper: a
classified `ConnectorFetchException` is logged and collected instead of aborting, and the remaining
datasets still run. The collected failures are rethrown at the end (a single failure verbatim, so
its type/stack is preserved; several as `CTB datasets failed: <dataset>: <type>: <detail> …`), so
the run still closes `PARTIAL`/`FAILED` and a missing dataset is never mistaken for "no new data".

This is retained even though the root cause is fixed, because it makes one bad endpoint
non-fatal and preserves the audit's "attempt each dataset independently" recommendation.

## Verification

- Live, fresh session through the fixed `CtbClient`:
  - `GetAllRecipes` → 663 recipes (JSON)
  - `GetAllSuppliers` → 117, `GetAllDepartments` → 2, `GetAllUOMs` → 8
  - `SearchStocks` → 1201
- `CtbClientTest.requestBody…` (empty/null → `keyword=`), `nonJsonDetail…` (status/type/prefix,
  bounded); `CtbConnectorTest.aFailingDatasetDoesNotAbortTheRest`,
  `multipleDatasetFailuresAreSummarised`; existing `permissionDeniedFromClientPropagates` still holds.
- Full backend suite: `./gradlew test` (843 tests, 0 failures) and `spotlessCheck`.

## Non-goals / deferred

- Per-dataset **failure rows** (rather than one aggregated failure) belong to the dataset-stage
  accounting plan (`docs/superpowers/plans/2026-10-09-ingestion-stage-accounting.md`, F06/F09).
- An explicit request timeout on `CtbClient` (audit noted its absence and no measured hang): not
  needed for this defect; still worth a separate change.
