# OpenTable Reviews, Menu Items & Rich Reservations — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ingest three one-time OpenTable GuestCenter CSVs (rich reservations, reviews, menu items) into the raw ledger byte-faithfully and recurrence-ready, and canonicalize the reservations+guest slice (extend `canonical_reservation`, add the `canonical_guest` schema).

**Architecture:** Follow the existing OpenTable manual CSV-drop pattern — one typed, header-validated, fail-loud parser per dataset; each ingest service stores byte-faithful via `IngestionService.ingestPush` then parses. Only reservations canonicalize this slice; reviews and menu items are raw-ledger-only (parsers validated, output parked for slices 2–3). Reservations reuse the bitemporal close-and-reinsert supersession; the thin `opentable-csv-drop` path loses its canonicalization to prevent sparse-upload supersession.

**Tech Stack:** Java 25, Spring Boot 3.5.0, Spring Data JPA, PostgreSQL 16 (Flyway), `commons-csv`, JUnit 5 + Testcontainers, Gradle 9.5.0 wrapper.

**Spec:** `docs/superpowers/specs/2026-10-13-opentable-reviews-menu-reservations-design.md` — the plan argues from the spec; executors read both.

## Global Constraints

- Build/run from `backend/` with the committed wrapper `./gradlew` (pinned Gradle 9.5.0 — do not downgrade).
- `hibernate.ddl-auto: validate` — every schema change is a Flyway migration (`backend/src/main/resources/db/migration`); never edit an applied migration. Next free numbers: `V51`, `V52`.
- Raw ledger is append-only and byte-faithful; canonical rows are closed-and-reinserted, never edited in place.
- No write-back to OpenTable or any source, ever.
- Architecture rules in `ArchitectureBoundariesTest`: `api` never reads canonical/raw; connectors depend only on `canonical`/`ingestion`. Keep the new code inside `connectors/opentable`, `canonical`, `api`, `application` per their existing roles.
- Format with `./gradlew spotlessApply` (google-java-format 1.28.0).
- Fast inner loop: `./gradlew fastTest` (unit, no Docker) or `--tests` targeting. Full gate before finishing: `./gradlew test spotlessCheck` (needs Docker for Testcontainers).
- **Git:** never commit to `main`. Do the work in a git worktree on a task branch (`superpowers:using-git-worktrees`); the production checkout stays on `main` untouched.
- Money is `BigDecimal`, counts are `Integer`, booleans are `Boolean`, dates are `LocalDate`, timestamps are `Instant`.

## Review Focus

These are the inputs/failure modes the spec implies but no single task's tests would otherwise pin; each is mapped to the owning task below.

1. **UTF-8 BOM** preceding the first header cell (`Visit Date` / `Review ID` / `Items`) — header matching must not miss the first column. → Task 3, 5, 6.
2. **Multi-line quoted comments** (review `Comment` contains embedded newlines) — must stay one field, not split rows. → Task 5.
3. **Blank optional cells** (empty `Table`, `Wait staff`, empty sub-ratings, blank `Visit Notes`) — must parse to `null`, never `""` or `0`. → Tasks 3, 5, 6.
4. **Re-upload with unchanged facts** — must be a no-op (no duplicate canonical rows). → Task 4.
5. **Re-upload with a changed fact** (e.g. `Wait staff` edited) — must close the prior row and insert a successor, preserving history. → Task 4.
6. **Masked PII** (`**********` phone/email) — must store `null`, not the literal mask. → Task 3.
7. **Mixed Reservation ID formats** (`CSV_140126_RES0000095` legacy vs native UUIDs) — both key correctly with no collision. → Tasks 3, 4.
8. **Thin CSV after rich CSV** — must not supersede rich facts with nulls (the sparse-supersession guard). → Task 7.

---

### Task 1: Extend `canonical_reservation` schema + entity + `ReservationInput`

**Files:**
- Create: `backend/src/main/resources/db/migration/V51__reservation_detail.sql`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/ReservationInput.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservation.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationService.java`
- Modify: `backend/src/test/java/com/goldys/platform/canonical/CanonicalReservationIntegrationTest.java`

**Interfaces:**
- Produces: `ReservationInput` record with the 9 existing fields **plus** the nullable fields listed below (exact names + Java types). Because the record grows to ~79 components, it exposes a **builder** (`ReservationInput.builder()` → `.sourceSystem(...)`/`.reservationId(...)`/…/`.build()`; one method per field) as the construction path, and a **9-arg convenience constructor** (the original signature) that defaults every new field to `null` so existing call sites compile unchanged.
- Produces: `CanonicalReservation.create(UUID logicalEntityId, String sourceSystem, String sourceRecordRef, UUID rawRecordId, Instant validFrom, Instant recordedAt, ReservationInput input)` — the entity copies every field from `input` rather than 70 positional params. `sameFact(ReservationInput input)` compares every field (`Objects.equals`, `==` for `int partySize`).
- Consumes: `BitemporalEntity` (unchanged).

New `ReservationInput` fields (nullable unless noted), names are the spec §4 column names camelCased:

- Visit lifecycle — `createdAt`/`cancelledAt`/`noShowAt`/`seatedAt`/`finishedAt`/`lastUpdatedAt` `Instant`; `totalActualDurationMinutes` `Integer`.
- Operational — `tableType`, `diningAreaAssigned`, `diningAreaBooked`, `diningAreaIdAssigned`, `diningAreaIdBooked`, `waitStaff`, `createdBy`, `turnTimeSetting`, `reservationType`, `occasion`, `reservationStatus` `String`; `onlineWaitlist`, `isFlexReso` `Boolean`; `quotedWaitTime`, `totalWaitTime` `Integer`.
- Source/marketing — `onlineChannel`, `partner`, `campaignType`, `campaignDetails`, `referrerName`, `referrerBusiness`, `restrefSource`, `restrefCampaignName`, `restrefId` `String`.
- Guest — `guestFirstName`, `guestLastName`, `guestNickname`, `guestEmail`, `guestPhone`, `company` `String`; `marketingOptIn` `Boolean`; `birthday`, `anniversary`, `firstVisitDate`, `recentVisitDate` `LocalDate`; `upcomingVisits`, `completedVisits`, `cancelledCount`, `noShowCount` `Integer`; `lifetimeSpend`, `lifetimePerCoverSpend` `BigDecimal`; `guestId` `UUID`.
- Experience/prepayment — `experienceTitle`, `experienceType`, `experienceTypeEnum`, `experiencePriceType`, `prepaymentMethod`, `prepaymentStatus`, `vendorPayoutStatus`, `currency` `String`; `experiencePrice`, `experienceSubtotal`, `addonsSubtotal`, `serviceCharges`, `prepaidExperienceTip`, `prepaidExperienceVat`, `prepaidExperienceTotalPaid`, `experienceTotalSales`, `experienceTotalSalesWithTip`, `prepaymentRequiredTotal`, `prepaymentPaidTotal` `BigDecimal`.
- Payment/revenue — `posCheckIds` `String`; `posSubtotal`, `posTax`, `posServiceCharges`, `posTip`, `posPaid`, `posDue`, `totalTips`, `totalVat`, `totalIncome`, `totalIncomeWithTip`, `totalRevenue`, `totalRevenueWithTip`, `totalRevenueWithoutTip`, `posSpend`, `posRefund`, `posRevenue`, `refundAmount`, `vendorPayoutAmount` `BigDecimal`.
- Notes/tags — `guestRequests`, `visitNotes`, `reservationTags`, `guestTags` `String`.

- [ ] **Step 1: Write the failing migration + entity test**

Add to `CanonicalReservationIntegrationTest` a test `persistsRichReservationFields` that records a `ReservationInput` with a non-null value in every new field, then asserts a current `CanonicalReservation` row has those values and the new columns are non-null.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew fastTest --tests 'com.goldys.platform.canonical.CanonicalReservationIntegrationTest'`
Expected: FAIL — `ReservationInput` has no such fields / columns do not exist (test won't compile or JPA `validate` fails).

- [ ] **Step 3: Write `V51__reservation_detail.sql`**

`ALTER TABLE canonical_reservation ADD COLUMN <name> <type>;` for each field above, all nullable, no defaults. Map types: `Instant` → `timestamp(6) with time zone`, `Integer` → `integer`, `Boolean` → `boolean`, `String` → `varchar(1024)` (except `currency` `varchar(16)`, `pos_check_ids` `varchar(1024)`), `BigDecimal` → `numeric(19,4)`, `LocalDate` → `date`, `guestId` → `uuid` (no FK constraint this slice; the target table arrives in Task 2).

- [ ] **Step 4: Extend `ReservationInput` (builder + convenience ctor) and `CanonicalReservation` (columns + `create` + `sameFact`)**

Add the fields to the record (all-new fields nullable), a fluent builder, and the 9-arg convenience constructor defaulting new fields to `null`. Add the `@Column`-mapped fields to the entity; change `CanonicalReservation.create(...)` to accept the `ReservationInput` and copy every field from it; extend `sameFact` to `Objects.equals` every field (`==` for `int partySize`). Update `CanonicalReservationService.recordAt` to call the new `create(...)` signature and pass the full `input` (so the build stays green).

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew fastTest --tests 'com.goldys.platform.canonical.CanonicalReservationIntegrationTest'`
Expected: PASS. (If Docker is unavailable, run the non-integration compile + `spotlessApply`; the integration test runs in CI.)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V51__reservation_detail.sql \
        backend/src/main/java/com/goldys/platform/canonical/ReservationInput.java \
        backend/src/main/java/com/goldys/platform/canonical/CanonicalReservation.java \
        backend/src/test/java/com/goldys/platform/canonical/CanonicalReservationIntegrationTest.java
git commit -m "feat(reservations): extend canonical_reservation with rich GuestCenter fields"
```

---

### Task 2: Add `canonical_guest` (schema + entity + repository)

**Files:**
- Create: `backend/src/main/resources/db/migration/V52__canonical_guest.sql`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalGuest.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalGuestRepository.java`
- Create: `backend/src/test/java/com/goldys/platform/canonical/CanonicalGuestMigrationTest.java`

**Interfaces:**
- Produces: `CanonicalGuest extends BitemporalEntity` (package-private, like `CanonicalReservation`), fields: `guestName`, `firstName`, `lastName`, `nickname`, `email`, `phone`, `company` `String`; `marketingOptIn` `Boolean`; `firstVisitDate`, `recentVisitDate` `LocalDate`; `upcomingVisits`, `completedVisits`, `cancelledCount`, `noShowCount` `Integer`; `lifetimeSpend`, `lifetimePerCoverSpend` `BigDecimal`. `CanonicalGuestRepository extends BitemporalRepository<CanonicalGuest>` (empty — no queries needed this slice).
- Consumes: `BitemporalEntity`.

- [ ] **Step 1: Write the failing migration test**

`CanonicalGuestMigrationTest` (a plain Flyway/schema test, or a Testcontainers context): assert the `canonical_guest` table exists with the columns in the schema below and the `uq_guest_current_source_fact` unique index.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew fastTest --tests 'com.goldys.platform.canonical.CanonicalGuestMigrationTest'`
Expected: FAIL — table does not exist.

- [ ] **Step 3: Write `V52__canonical_guest.sql`**

Columns: `id uuid PRIMARY KEY`, `logical_entity_id uuid NOT NULL`, `source_system varchar(255) NOT NULL`, `source_record_ref varchar(512) NOT NULL`, `raw_record_id uuid NOT NULL REFERENCES raw_record(id)`, the guest fields above (nullable), and the bitemporal columns `valid_from`/`valid_to`/`recorded_at`/`superseded_at` (same types as `V11`). Add `CREATE UNIQUE INDEX uq_guest_current_source_fact ON canonical_guest (source_system, source_record_ref) WHERE superseded_at IS NULL;` and `CREATE INDEX ix_guest_logical_current ON canonical_guest (logical_entity_id) WHERE superseded_at IS NULL;`.

- [ ] **Step 4: Add `CanonicalGuest` entity + `CanonicalGuestRepository`**

Mirror `CanonicalReservation`'s `@Entity`/`@Table(name="canonical_guest")` + `@Column(..., updatable=false)` shape; a package-private constructor and a static `create(...)`. No `sameFact`/service/query yet (not populated this slice).

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew fastTest --tests 'com.goldys.platform.canonical.CanonicalGuestMigrationTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V52__canonical_guest.sql \
        backend/src/main/java/com/goldys/platform/canonical/CanonicalGuest.java \
        backend/src/main/java/com/goldys/platform/canonical/CanonicalGuestRepository.java \
        backend/src/test/java/com/goldys/platform/canonical/CanonicalGuestMigrationTest.java
git commit -m "feat(guests): add canonical_guest schema (unpopulated until reviews slice)"
```

---

### Task 3: Rich reservation parser

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservationDetail.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservationsCsvParser.java`
- Create: `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableReservationsCsvParserTest.java`
- Create: `backend/src/test/resources/fixtures/opentable/reservations_rich_sample.csv`

**Interfaces:**
- Produces: `OpenTableReservationsCsvParser.parse(byte[] csv) -> List<OpenTableReservationDetail>`; `OpenTableReservationDetail` is a record holding the parsed reservation row — one field per `ReservationInput` field above plus the raw `reservationId` `String` (identity) and `status` `String` (normalized), with every money/count/date already coerced (blank → `null`).
- Consumes: `ConnectorFetchException` (`ingestion.port`).

Required columns (parser fails with `CONNECTOR_SCHEMA_MISMATCH` if absent): `Reservation ID`, `Visit Date`, `Size`, `Status`. Strip a leading UTF-8 BOM before header matching. `reservation_at` comes from the single `Visit Date` timestamp (format `yyyy-MM-dd HH:mm:ss`, venue zone `Australia/Melbourne`). `party_size` from `Size` (int). `status` normalization: `Finished`→`COMPLETED`, `Seated`→`SEATED`, `Cancelled`→`CANCELLED`, `Booked`→`BOOKED`, `No Show`→`NO_SHOW`, `Walk-in`/`Walk in`→`WALK_IN`, else uppercase passthrough. `Reservation Status` (system `Done`/`AssumedSeated`/`AssumedDone`/`Cancelled`) → `reservationStatus` verbatim. Masked `**********` phone/email → `null`. Blank optional cells → `null`.

- [ ] **Step 1: Write the failing parser tests**

Fixture `reservations_rich_sample.csv`: a trimmed copy of the real CSV (header row + ~4 data rows exercising both `CSV_140126_RES…` and UUID `Reservation ID`s, a masked phone, a blank `Wait staff`, and a `Finished` status). Tests in `OpenTableReservationsCsvParserTest`:
  - `stripsBomAndFindsColumns` — a fixture with a leading BOM parses; `Reservation ID`/`Visit Date`/`Size`/`Status` resolve.
  - `mapsRichFields` — first row's `reservationAt`, `partySize`, `status` (`Finished`→`COMPLETED`), `waitStaff`, and one money field (`posPaid`) match the fixture.
  - `masksPiiToNull` — the row with `**********` phone parses with `guestPhone == null`.
  - `blankOptionalToNull` — the blank `Wait staff` parses to `null`.
  - `missingColumnThrows` — a header without `Visit Date` throws `ConnectorFetchException` with type `CONNECTOR_SCHEMA_MISMATCH`.
  - `handlesBothIdFormats` — a legacy `CSV_140126_RES…` and a UUID `Reservation ID` both parse; `reservationId()` returns each verbatim.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew fastTest --tests 'com.goldys.platform.connectors.opentable.OpenTableReservationsCsvParserTest'`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `OpenTableReservationDetail` + `OpenTableReservationsCsvParser`**

Reuse `OpenTableCsvParser`'s `readAll`/`columnIndex`/`require` helpers (copy or share); the parser is a `@Component` taking `@Value("${opentable.timezone:Australia/Melbourne}")`. Map columns → the detail record via a `blankToNull` + typed coercion helper (`parseInstant`, `parseInt`, `parseBigDecimal`, `parseLocalDate`, `parseBool`). No reporting arithmetic.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew fastTest --tests 'com.goldys.platform.connectors.opentable.OpenTableReservationsCsvParserTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservationDetail.java \
        backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservationsCsvParser.java \
        backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableReservationsCsvParserTest.java \
        backend/src/test/resources/fixtures/opentable/reservations_rich_sample.csv
git commit -m "feat(opentable): parse the rich GuestCenter reservations export"
```

---

### Task 4: Rich reservation ingest (service + endpoint + supersession)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservationsCsvIngestService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/OpenTableReservationsCsvIngestController.java`
- Create: `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableReservationsCsvIngestServiceTest.java`

**Interfaces:**
- Consumes: `OpenTableReservationsCsvParser.parse(byte[])`, `IngestionService.ingestPush(String sourceSystem, String connectorName, FetchMethod fetchMethod, String contentType, byte[] bytes, String characterEncoding, String fetcherIdentity)`, `CanonicalReservationIngest.record(ReservationInput)`, `ReservationInput.builder()`.
- Produces: `OpenTableReservationsCsvIngestService.ingest(byte[] csv)` — `ingestPush("OPENTABLE", "opentable-reservations", FetchMethod.FILE_EXPORT, "text/csv", csv, UTF-8, "opentable-reservations")` then `parser.parse(csv)` → one `ReservationInput` per row (built via the builder from `OpenTableReservationDetail`) → `canonical.record(...)`. Endpoint `POST /api/ingest/opentable-reservations` token-gated like `OpenTableCsvIngestController` (same `opentable.drop-token`).

- [ ] **Step 1: Write the failing ingest-service tests**

`OpenTableReservationsCsvIngestServiceTest` (mock `IngestionService` + `CanonicalReservationIngest`, real parser on a fixture):
  - `storesRawAndCanonicalizesEachRow` — `ingestPush` called once with fetcher identity `opentable-reservations`; `canonical.record` called once per row with the parsed values.
  - `supersessionUsesCanonicalService` — call `ingest` twice with a fixture whose `Wait staff` changed between the two; assert `canonical.record` is called again for the changed row (supersession is the canonical service's responsibility — the ingest service just records).

Also add a `CanonicalReservationIntegrationTest` case `unchangedReuploadIsNoOpAndChangeSupersedes`: record the same `ReservationInput` twice → one current row; record a changed `waitStaff` → prior row `superseded_at` non-null, one new current row.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew fastTest --tests 'com.goldys.platform.connectors.opentable.OpenTableReservationsCsvIngestServiceTest'`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement the service and endpoint**

The service mirrors `OpenTableCsvIngestService` but uses the rich parser and maps `OpenTableReservationDetail` → the extended `ReservationInput` via the builder (identity = `reservationId`, `sourceSystem = "OPENTABLE"`). The controller copies `OpenTableCsvIngestController` with the new path.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew fastTest --tests 'com.goldys.platform.connectors.opentable.OpenTableReservationsCsvIngestServiceTest' --tests 'com.goldys.platform.canonical.CanonicalReservationIntegrationTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservationsCsvIngestService.java \
        backend/src/main/java/com/goldys/platform/api/OpenTableReservationsCsvIngestController.java \
        backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationService.java \
        backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableReservationsCsvIngestServiceTest.java \
        backend/src/test/java/com/goldys/platform/canonical/CanonicalReservationIntegrationTest.java
git commit -m "feat(opentable): ingest rich reservations into canonical_reservation"
```

---

### Task 5: Reviews parser + raw-only ingest

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReview.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvParser.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvIngestService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/OpenTableReviewsCsvIngestController.java`
- Create: `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvParserTest.java`
- Create: `backend/src/test/resources/fixtures/opentable/reviews_sample.csv`

**Interfaces:**
- Produces: `OpenTableReviewsCsvParser.parse(byte[]) -> List<OpenTableReview>`; `OpenTableReview` record: `reviewId` (identity), `source` (`OpenTable`/`Google`), `reviewDate` `Instant`, `overallRating` `Integer`, `comment` `String` (nullable). `OpenTableReviewsCsvIngestService.ingest(byte[])` — `ingestPush("OPENTABLE", "opentable-reviews", FILE_EXPORT, "text/csv", …, "opentable-reviews")` then `parser.parse(...)` for validation only (output not canonicalized). Endpoint `POST /api/ingest/opentable-reviews`.
- Consumes: `IngestionService`, `ConnectorFetchException`.

Required columns: `Review ID`, `Source`, `Review Date`, `Overall Rating`. Strip BOM; `Comment` may span multiple quoted lines (commons-csv handles it); blank `Overall Rating`/`Comment` → `null`.

- [ ] **Step 1: Write the failing parser + service tests**

`OpenTableReviewsCsvParserTest`: `handlesMultilineComment` (a comment with an embedded newline parses as one field), `missingColumnThrows`, `mapsReviewIdentity` (`reviewId`/`source`/`overallRating`). `OpenTableReviewsCsvIngestServiceTest`: `storesRawOnlyAndValidates` — `ingestPush` called with `opentable-reviews`; a bad header throws and no raw record is written (validation happens before/with ingest; on failure the run is failed, matching `OpenTableCsvIngestService`).

- [ ] **Step 2: Run to verify they fail** — `./gradlew fastTest --tests 'com.goldys.platform.connectors.opentable.OpenTableReviewsCsvParserTest'` → FAIL (class not found).

- [ ] **Step 3: Implement the record, parser, service, endpoint** — mirror Tasks 3–4 but raw-only (no canonical write).

- [ ] **Step 4: Run to verify they pass** — same `--tests` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(opentable): ingest reviews raw (parser + endpoint)"`.

---

### Task 6: Menu items parser + raw-only ingest

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableMenuItem.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableMenuItemsCsvParser.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableMenuItemsCsvIngestService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/OpenTableMenuItemsCsvIngestController.java`
- Create: `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableMenuItemsCsvParserTest.java`
- Create: `backend/src/test/resources/fixtures/opentable/menu_items_sample.csv`

**Interfaces:**
- Produces: `OpenTableMenuItemsCsvParser.parse(byte[]) -> List<OpenTableMenuItem>`; `OpenTableMenuItem` record: `invariantId` (identity), `itemId`, `name` (`Items` column), `price` `BigDecimal` (from `Price`, stripping the `A$` prefix and parsing). `OpenTableMenuItemsCsvIngestService.ingest(byte[])` — `ingestPush("OPENTABLE", "opentable-menu-items", FILE_EXPORT, "text/csv", …, "opentable-menu-items")` then validate. Endpoint `POST /api/ingest/opentable-menu-items`.
- Consumes: `IngestionService`, `ConnectorFetchException`.

Required columns: `Item ID`, `Invariant ID`, `Items`, `Price`. Strip BOM; blank `Price` → `null`; `A$72.00` → `BigDecimal("72.00")`.

- [ ] **Step 1: Write the failing tests** — parser: `mapsIdentityAndPrice` (assert `invariantId`, `itemId`, `name`, `price` for a `A$72.00` row), `stripsBom`, `missingColumnThrows`. Service: `storesRawOnlyWithFetcherIdentity`.

- [ ] **Step 2: Run to verify they fail** — `./gradlew fastTest --tests 'com.goldys.platform.connectors.opentable.OpenTableMenuItemsCsvParserTest'` → FAIL.

- [ ] **Step 3: Implement** the record, parser (`A$`-prefix price parse), service, endpoint.

- [ ] **Step 4: Run to verify they pass** — same `--tests` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(opentable): ingest menu items raw (parser + endpoint)"`.

---

### Task 7: Register sources, in-app uploads, thin-path deprecation, docs

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/application/ConnectorApplicationService.java` (add `openTableReservationsIngest`/`openTableReviewsIngest`/`openTableMenuItemsIngest` deps + `uploadOpenTableReservationsCsv`/`uploadOpenTableReviewsCsv`/`uploadOpenTableMenuItemsCsv`; add the three to `KNOWN_SOURCES` with connector names `opentable-reservations`/`opentable-reviews`/`opentable-menu-items`)
- Modify: `backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java` (add three multipart upload endpoints `POST /api/connectors/opentable-reservations/upload`, `.../opentable-reviews/upload`, `.../opentable-menu-items/upload`)
- Modify: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableCsvIngestService.java` (thin path — remove canonicalization, raw-only)
- Modify: `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableCsvIngestServiceTest.java` (assert raw-only)
- Modify: `docs/connectors/opentable.md`, `docs/connectors/matching-and-identity.md`, `docs/connectors/source-access.md`

**Interfaces:**
- Consumes: the three ingest services; `ConnectorApplicationService.uploadOpenTableCsv` pattern (permission `connectors` WRITE).
- Produces: three new connector names in the connectors screen; three new token-gated ingest endpoints; the thin `opentable-csv-drop` path no longer canonicalizes.

- [ ] **Step 1: Write the failing tests**

`ConnectorApplicationServiceTest`: `connectorsListsNewSources` — `KNOWN_SOURCES`/result includes `opentable-reservations`, `opentable-reviews`, `opentable-menu-items`. `uploadOpenTableReservationsCsvDelegates` — calls the right ingest service under `connectors` WRITE. `OpenTableCsvIngestServiceTest` update: `storesRawButDoesNotCanonicalize` — `ingest` stores raw and never calls `canonical.record`.

- [ ] **Step 2: Run to verify they fail** — `./gradlew fastTest --tests 'com.goldys.platform.application.ConnectorApplicationServiceTest' --tests 'com.goldys.platform.connectors.opentable.OpenTableCsvIngestServiceTest'` → FAIL.

- [ ] **Step 3: Implement**

Add the three services to `ConnectorApplicationService` (constructor + upload methods mirroring `uploadOpenTableCsv`) and the three upload endpoints to `ConnectorStatusController`. Remove the `CanonicalReservationIngest`/parser dependency from the thin `OpenTableCsvIngestService` so it only calls `ingestion.ingestPush(...)` (the sparse-supersession guard: the thin path can no longer downgrade a rich row). Delete the now-unused `OpenTableCsvParser`/`OpenTableReservation`/`OpenTableCsvParserTest` if nothing else references them.

- [ ] **Step 4: Run to verify they pass** — same `--tests` → PASS.

- [ ] **Step 5: Update docs** — `opentable.md`: document the three real export shapes + fetcher identities, mark the thin `opentable-csv-drop` deprecated. `matching-and-identity.md`: add a `Guest` section (identity, no-guest-id-in-reservations ruling, deferred name-matching) and note future review/menu identities. `source-access.md`: OpenTable row names the three reports.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/application/ConnectorApplicationService.java \
        backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java \
        backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableCsvIngestService.java \
        backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableCsvIngestServiceTest.java \
        backend/src/test/java/com/goldys/platform/application/ConnectorApplicationServiceTest.java \
        docs/connectors/opentable.md docs/connectors/matching-and-identity.md docs/connectors/source-access.md
git commit -m "feat(opentable): register 3 new sources; deprecate thin CSV canonicalization"
```

---

### Final gate (after Task 7)

Run the full backend gate and format:

```bash
./gradlew spotlessApply test spotlessCheck
```

Expected: green. If Docker is unavailable locally, run `./gradlew fastTest spotlessCheck` and let CI run the Testcontainers integration tests.
