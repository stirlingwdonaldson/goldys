---
name: new-connector
description: Add or extend a source integration (Lightspeed, Cooking the Books, OpenTable, Deputy, or a new source) following this repo's connector-isolation and raw-ledger pattern. Use when asked to build, add, or change a connector, webhook ingest, CSV/PDF drop, or SFTP pull.
---

Goldy's ingests from four Phase 1 sources: **Lightspeed** (POS), **Cooking the
Books / CTB** (inventory, purchasing, and a second sales feed), **OpenTable**
(reservations), and **Deputy** (labour). MarketMan is history-only (one-time
pull, see `docs/decisions/marketman-history-only.md`). Tenzo is out of scope.

Paths below are relative to `backend/src/main/java/com/goldys/platform/`.

## Before writing code

1. Read the source's row in `docs/system-context.md` ("Per-Source Ingestion
   Reality") and its section in `docs/connectors/source-access.md`. Don't assume
   a clean REST API exists.
2. Read `docs/connectors/matching-and-identity.md` for the canonical entity the
   source feeds. If it's a new entity, follow `docs/architecture/adding-a-domain.md`
   and add a section to the matching doc.
3. Look at what already exists for the source in `connectors/<source>/` and
   `api/*IngestController.java`. Most sources already have an ingestion path.

| Source | Current path | Code |
|---|---|---|
| Lightspeed | Insights webhook (sales, products, Z-report) | `connectors/lightspeed/`, `api/LightspeedIngestController` |
| CTB | Scheduled AJAX pull (pull connector), invoice CSV/PDF drop, SFTP poll | `connectors/ctb/`, `ingestion/CtbScheduledPull`, `ingestion/CtbSftpPull` |
| OpenTable | Manual GuestCenter CSV drop | `connectors/opentable/`, `api/OpenTableCsvIngestController` |
| Deputy | Webhook, raw-ledger only until the payload schema is confirmed | `api/DeputyIngestController` |

## Pick the pattern

**Pull** (the platform fetches on a schedule or "run now"): implement
`ingestion/port/SourceConnector`.

```java
public interface SourceConnector {
  String sourceSystem();     // e.g. "CTB" — recorded on every run, payload, failure
  String connectorName();    // which adapter, e.g. "ctb-revenue"
  void fetch(String watermark, IngestionSink sink);
}
```

- Hand each payload to `sink.accept(new FetchedPayload(fetchMethod,
  contentType, bytes, charset, fetcherIdentity))` as soon as you have it. The
  sink stores it and returns the raw record id. Streaming means pages already
  fetched survive a later failure and the run ends `PARTIAL`.
- Register the connector as a Spring `@Bean` (see `connectors/ctb/CtbConfig`);
  `IngestionService` collects every `SourceConnector` bean, and `ConnectorRunner`
  creates the `IngestionRun`, records failures, and sets the final status.
- For expected source failures (bad credentials, changed page, rejected
  request) throw `ConnectorFetchException(failureType, message)`. The message is
  shown to operators: never include payload contents, credentials, or tokens.

**Push** (the source or an operator sends us data: webhook, CSV/PDF upload,
SFTP drop): call `IngestionService.ingestPush(sourceSystem, connectorName,
fetchMethod, contentType, bytes, charset, fetcherIdentity)` first, then parse
the stored bytes. `connectors/opentable/OpenTableCsvIngestService` is the
smallest example.

- Public ingest endpoints are gated by a shared token header and fail closed
  when the token is unset (see `api/LightspeedIngestController`). Add the token
  to `.env.example`.

## Rules

- **Raw first.** Bytes go to the raw ledger byte-faithful before any parsing,
  using the right `FetchMethod` (`API`, `FILE_EXPORT`, `SCRAPE`, `MANUAL`). No
  source-specific staging tables.
- **Use a distinct `fetcherIdentity`** per endpoint or export type, so the
  ledger can tell them apart (CTB uses one per AJAX endpoint).
- **Canonicalize through the public ingest facade** (`Canonical*Ingest`),
  passing the raw record id. Never write canonical repositories directly from a
  connector. Raw-only ingestion (no canonical mapping yet) is fine and common;
  say so in the class Javadoc.
- **Don't widen the port.** If the connector needs a change to
  `SourceConnector`, `IngestionSink`, or `FetchedPayload`, stop and flag it.
- **One-way only.** Never write back to a source system.
- **Never commit credentials.** Read them from environment variables and
  document the names in `.env.example`.

## Done means

- Tests: parser unit tests from a real (sanitised) sample in
  `src/test/resources/fixtures/`, plus an integration test that the payload
  lands in `raw_record` and that a failure produces a `FAILED`/`PARTIAL` run with
  an `IngestionFailure`, not an empty success.
- `./gradlew test spotlessCheck` passes.
- `docs/connectors/source-access.md` describes the access path, and
  `docs/connectors/matching-and-identity.md` describes identity for any new
  canonical entity.
