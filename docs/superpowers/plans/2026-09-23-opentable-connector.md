# OpenTable Connector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the OpenTable source connector — a scripted Playwright pull of GuestCenter reservation CSV, byte-faithfully through the ingestion envelope, mapped into a new bitemporal `CanonicalReservation` entity.

**Architecture:** A self-contained `connectors/opentable/` package mirrors `connectors/ctb/`: an `OpenTableClient` interface (Playwright-backed impl) fetches a CSV export, `OpenTableCsvParser` turns it into typed records, and `OpenTableConnector implements SourceConnector` streams the raw bytes to the sink then canonicalizes each row through a new `CanonicalReservationIngest`. The canonical layer follows the existing bitemporal pattern (`BitemporalEntity` + lock + `sameFact` + supersede).

**Tech Stack:** Java 25, Spring Boot 3.5, JPA/Hibernate (`ddl-auto: validate`), Flyway, PostgreSQL 16 (Testcontainers), commons-csv 1.11, Playwright (Java) + headless Chromium, JUnit 5 + Mockito, spotless/google-java-format.

**Spec:** `docs/superpowers/specs/2026-09-23-opentable-connector-design.md`

## Global Constraints

- Java 25 toolchain; backend lives in `backend/` and builds with `./gradlew` (run from `backend/`).
- Schema is owned by Flyway migrations in `src/main/resources/db/migration`; `ddl-auto: validate` means every entity must have a matching migration or startup fails. Next migration number is `V11`.
- Connectors are one-way (read-only); never write back to a source.
- Every raw payload goes through the shared envelope: `sourceSystem`, `fetchMethod`, `contentType`, `characterEncoding`, `fetcherIdentity` all non-null. `FetchMethod.SCRAPE` for this connector.
- `sourceSystem()` returns `"OPENTABLE"`; `connectorName()` returns `"opentable-guestcenter"`.
- Credentials come from env vars via `@Value` relaxed binding: `OPENTABLE_EMAIL` → `opentable.email`, `OPENTABLE_PASSWORD` → `opentable.password`. Never commit real credentials.
- Failures are `ConnectorFetchException(failureType, message)`; the message must never contain payload contents, credentials, or tokens.
- Code is google-java-format clean (spotless). Run `./gradlew spotlessCheck`; `./gradlew spotlessApply` fixes formatting.
- Conventional Commits, atomic, one logical increment per commit. Work happens on a feature branch, never `main`.
- The OpenTable reservation ID is stored as `source_record_ref` (inherited from `BitemporalEntity`) — no separate `reservation_ref` column, avoiding a duplicate of the identity. The `table` fact column is named `table_name` in DDL/entity because `table` is a reserved word.

## Review Focus

These are the inputs/failure modes the spec implies but that are easy to miss. Each is pinned by a test in the task noted.

1. **Empty export (zero reservation rows)** must surface as `CONNECTOR_SCHEMA_MISMATCH`, never a silent "no new data". → Task 2.
2. **A renamed or missing CSV column** must fail loudly naming the column, not produce wrong rows. → Task 2.
3. **Re-importing an unchanged reservation** must be idempotent — no duplicate version appended. → Task 4.
4. **A changed reservation (status/party size/table)** must supersede and leave exactly one current row. → Task 4.
5. **Auth failure or missing browser** must propagate as a classified failure (`CONNECTOR_AUTH_FAILED` / `CONNECTOR_BROWSER_FAILED`), never look like a clean run. → Task 6.

---

## File Structure

New files (all under `backend/`):

- `src/main/java/com/goldys/platform/connectors/opentable/OpenTableClient.java` — browser-automation port (interface).
- `src/main/java/com/goldys/platform/connectors/opentable/PlaywrightOpenTableClient.java` — Playwright implementation.
- `src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservation.java` — parsed reservation record.
- `src/main/java/com/goldys/platform/connectors/opentable/OpenTableCsvParser.java` — CSV → records (commons-csv).
- `src/main/java/com/goldys/platform/connectors/opentable/OpenTableConnector.java` — `SourceConnector` orchestration.
- `src/main/java/com/goldys/platform/connectors/opentable/OpenTableConfig.java` — Spring wiring.
- `src/main/java/com/goldys/platform/canonical/CanonicalReservation.java` — bitemporal entity.
- `src/main/java/com/goldys/platform/canonical/ReservationInput.java` — public input record.
- `src/main/java/com/goldys/platform/canonical/CanonicalReservationService.java` — upsert service.
- `src/main/java/com/goldys/platform/canonical/CanonicalReservationIngest.java` — public facade.
- `src/main/java/com/goldys/platform/canonical/CanonicalReservationRepository.java` — repository.
- `src/main/resources/db/migration/V11__reservations.sql` — schema.
- `src/test/java/com/goldys/platform/connectors/opentable/OpenTableCsvParserTest.java`
- `src/test/java/com/goldys/platform/connectors/opentable/OpenTableConnectorTest.java`
- `src/test/java/com/goldys/platform/canonical/CanonicalReservationIntegrationTest.java`
- `src/test/resources/fixtures/opentable/reservations_sample.csv`
- `docs/connectors/opentable.md` — discovery reference (login/export mechanics, columns).

Modified files:

- `build.gradle` — add Playwright dependency.
- `Dockerfile` — add headless-Chromium OS deps + browser download.
- `.env.example` — document `OPENTABLE_EMAIL` / `OPENTABLE_PASSWORD` / `OPENTABLE_TIMEZONE`.

---

## Task 1: Capture and pin the GuestCenter reservation CSV

**Files:**
- Create: `backend/src/test/resources/fixtures/opentable/reservations_sample.csv`
- Create: `docs/connectors/opentable.md`

**Interfaces:**
- Produces: the fixture CSV whose header names are the parser's required columns (Task 2): `Reservation ID`, `Date`, `Time`, `Party Size`, `Status`, `Table`, `Source`, `Guest Name`.
- Produces: `docs/connectors/opentable.md` documenting login URL, report URL, export trigger, and the column list (Task 5 reads this for the Playwright selectors).

The GuestCenter column set is provisionally defined below (the repo rule is "confirm the actual working path" before coding against a schema). This task captures the real shape; if the real export differs, fix the fixture **and** Task 2's required-column list in the same commit.

- [ ] **Step 1: Write the provisional fixture CSV**

Create `backend/src/test/resources/fixtures/opentable/reservations_sample.csv`:

```csv
Reservation ID,Date,Time,Party Size,Status,Table,Source,Guest Name
1000000001,2026-09-23,19:30,4,Booked,12,OpenTable,Smith
1000000002,2026-09-23,18:00,2,Seated,7,OpenTable,Jones
1000000003,2026-09-23,20:15,6,Cancelled,,Phone,Patel
1000000004,2026-09-22,19:00,3,Completed,5,Walk-in,Brown
1000000005,2026-09-23,19:45,2,No Show,,OpenTable,Lee
```

- [ ] **Step 2: Write the discovery reference doc**

Create `docs/connectors/opentable.md`:

```markdown
# OpenTable (GuestCenter) Access

How the OpenTable connector reaches reservation data. Records login and export
mechanics and the CSV columns, never credentials.

## Login

- URL: `https://guestcenter.opentable.com/login`
- Email + password form; success lands on the GuestCenter dashboard.

## Reservations export

- Navigate to the Reservations report and choose the date range, then trigger the
  CSV export. The browser download is captured by Playwright (`page.waitForDownload`).
- Provisionally: report at `https://guestcenter.opentable.com/reports/reservations`,
  export button labelled "Export".

## CSV columns

`Reservation ID`, `Date`, `Time`, `Party Size`, `Status`, `Table`, `Source`, `Guest Name`.

- `Status` values observed: `Booked`, `Seated`, `Completed`, `Cancelled`,
  `No Show`, `Walk-in` (normalized by `OpenTableCsvParser`).
- `Table` and `Source` may be blank.
```

- [ ] **Step 3: (If a real export is obtainable) replace the synthetic fixture**

Run the Task 5 Playwright client against real credentials, save the downloaded CSV,
sanitize real names to placeholders, and overwrite `reservations_sample.csv`. Update
this task's Step 1 header list if the real columns differ.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/resources/fixtures/opentable/reservations_sample.csv docs/connectors/opentable.md
git commit -m "docs: record OpenTable GuestCenter access and reservation CSV fixture"
```

---

## Task 2: `OpenTableReservation` + `OpenTableCsvParser` (TDD)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservation.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableCsvParser.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableCsvParserTest.java`

**Interfaces:**
- Produces: `OpenTableReservation(String reservationId, Instant reservationAt, int partySize, String status, String table, String sourceChannel, String partyName)` — consumed by Task 6.
- Produces: `OpenTableCsvParser` (Spring `@Component`), constructed with `@Value("${opentable.timezone:Australia/Sydney}") String zone`; method `List<OpenTableReservation> parse(byte[] csv)`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableCsvParserTest.java`:

```java
package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenTableCsvParserTest {

  private final OpenTableCsvParser parser = new OpenTableCsvParser("Australia/Sydney");

  private byte[] fixture() throws Exception {
    return Files.readAllBytes(Path.of("src/test/resources/fixtures/opentable/reservations_sample.csv"));
  }

  @Test
  void parsesRowsNormalizingStatusAndCombiningDateTime() throws Exception {
    List<OpenTableReservation> rows = parser.parse(fixture());

    assertThat(rows).hasSize(5);
    OpenTableReservation first = rows.get(0);
    assertThat(first.reservationId()).isEqualTo("1000000001");
    assertThat(first.partySize()).isEqualTo(4);
    assertThat(first.status()).isEqualTo("BOOKED");
    assertThat(first.table()).isEqualTo("12");
    assertThat(first.sourceChannel()).isEqualTo("OpenTable");
    assertThat(first.partyName()).isEqualTo("Smith");
    // 2026-09-23T19:30 Australia/Sydney == 09:30 UTC
    assertThat(first.reservationAt()).isEqualTo(Instant.parse("2026-09-23T09:30:00Z"));
  }

  @Test
  void normalizesNoShowStatus() throws Exception {
    OpenTableReservation row = parser.parse(fixture()).get(4);
    assertThat(row.status()).isEqualTo("NO_SHOW");
  }

  @Test
  void blanksBecomeNull() throws Exception {
    OpenTableReservation row = parser.parse(fixture()).get(2);
    assertThat(row.table()).isNull();
    assertThat(row.sourceChannel()).isEqualTo("Phone");
  }

  @Test
  void emptyCsvIsSchemaMismatch() {
    assertThatThrownBy(() -> parser.parse("Reservation ID,Date,Time,Party Size,Status,Table,Source,Guest Name\n".getBytes()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void missingColumnIsSchemaMismatch() {
    String csv = "Reservation ID,Date,Time,Party Size,Status,Table,Source\n1,2026-09-23,19:30,4,Booked,12,OpenTable\n";
    assertThatThrownBy(() -> parser.parse(csv.getBytes()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Guest Name");
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests 'OpenTableCsvParserTest'`
Expected: compilation FAIL (types absent).

- [ ] **Step 3: Write `OpenTableReservation`**

Create `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservation.java`:

```java
package com.goldys.platform.connectors.opentable;

import java.time.Instant;

/** One reservation row parsed from a GuestCenter CSV export. */
public record OpenTableReservation(
    String reservationId,
    Instant reservationAt,
    int partySize,
    String status,
    String table,
    String sourceChannel,
    String partyName) {}
```

- [ ] **Step 4: Write `OpenTableCsvParser`**

Create `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableCsvParser.java`:

```java
package com.goldys.platform.connectors.opentable;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parses the GuestCenter reservation CSV. The header is read from the CSV and the required columns
 * are validated, so a report-shape change fails loudly rather than silently producing wrong rows.
 */
@Component
public class OpenTableCsvParser {

  private final ZoneId zone;

  public OpenTableCsvParser(@Value("${opentable.timezone:Australia/Sydney}") String zone) {
    this.zone = ZoneId.of(zone);
  }

  public List<OpenTableReservation> parse(byte[] csv) {
    List<CSVRecord> records = readAll(csv);
    if (records.size() < 2) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "OpenTable CSV is empty");
    }

    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "Reservation ID", "Date", "Time", "Party Size", "Status", "Table", "Source", "Guest Name");

    List<OpenTableReservation> out = new ArrayList<>();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      out.add(
          new OpenTableReservation(
              r.get(columns.get("Reservation ID")),
              at(r, columns),
              partySize(r, columns),
              normalizeStatus(get(columns, r, "Status")),
              blankToNull(get(columns, r, "Table")),
              blankToNull(get(columns, r, "Source")),
              blankToNull(get(columns, r, "Guest Name"))));
    }
    return out;
  }

  private Instant at(CSVRecord r, Map<String, Integer> columns) {
    String date = get(columns, r, "Date");
    String time = get(columns, r, "Time");
    try {
      LocalDateTime dt = LocalDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim()));
      return dt.atZone(zone).toInstant();
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad date/time '" + date + " " + time + "' in OpenTable CSV", e);
    }
  }

  private static int partySize(CSVRecord r, Map<String, Integer> columns) {
    String value = get(columns, r, "Party Size");
    try {
      return Integer.parseInt(value.trim());
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad party size '" + value + "' in OpenTable CSV", e);
    }
  }

  private static String normalizeStatus(String raw) {
    if (raw == null) {
      return null;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "booked" -> "BOOKED";
      case "seated" -> "SEATED";
      case "completed" -> "COMPLETED";
      case "cancelled", "canceled" -> "CANCELLED";
      case "no show", "no-show" -> "NO_SHOW";
      case "walk-in", "walk in" -> "WALK_IN";
      default -> raw.trim().toUpperCase(Locale.ROOT);
    };
  }

  private static List<CSVRecord> readAll(byte[] csv) {
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      return CSVFormat.DEFAULT.parse(in).getRecords();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, Integer> columnIndex(CSVRecord header) {
    Map<String, Integer> out = new HashMap<>();
    for (int i = 0; i < header.size(); i++) {
      out.put(header.get(i).trim(), i);
    }
    return out;
  }

  private static void require(Map<String, Integer> columns, String... names) {
    for (String name : names) {
      if (!columns.containsKey(name)) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "Missing column '" + name + "' in OpenTable CSV");
      }
    }
  }

  private static String get(Map<String, Integer> columns, CSVRecord r, String name) {
    Integer i = columns.get(name);
    return i == null ? null : r.get(i);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests 'OpenTableCsvParserTest' spotlessCheck`
Expected: PASS (5 tests), spotless clean.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservation.java \
        backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableCsvParser.java \
        backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableCsvParserTest.java
git commit -m "feat: add OpenTable reservation CSV parser"
```

---

## Task 3: `CanonicalReservation` entity + migration + repository

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservation.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ReservationInput.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationRepository.java`
- Create: `backend/src/main/resources/db/migration/V11__reservations.sql`

**Interfaces:**
- Produces: `ReservationInput(String sourceSystem, String reservationId, Instant reservationAt, int partySize, String status, String tableName, String sourceChannel, String partyName, UUID rawRecordId)` — produced here, consumed by Tasks 4 and 6.
- Produces: `CanonicalReservationRepository` with `Optional<CanonicalReservation> lockCurrentReservation(String ref, String source)`, `List<CanonicalReservation> findCurrentByRef(String ref)`, `List<CanonicalReservation> findAllCurrent()`.

The reservation ID is stored as `source_record_ref` (inherited); there is no separate `reservation_ref` column. This task's entity only declares the six fact columns plus the inherited provenance columns.

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V11__reservations.sql`:

```sql
CREATE TABLE canonical_reservation (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    reservation_at timestamp(6) with time zone NOT NULL,
    party_size integer NOT NULL,
    status varchar(64) NOT NULL,
    table_name varchar(255),
    source_channel varchar(255),
    party_name varchar(255),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX uq_reservation_current_source_fact
    ON canonical_reservation (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX ix_reservation_logical_current
    ON canonical_reservation (logical_entity_id) WHERE superseded_at IS NULL;
```

- [ ] **Step 2: Write `ReservationInput`**

Create `backend/src/main/java/com/goldys/platform/canonical/ReservationInput.java`:

```java
package com.goldys.platform.canonical;

import java.time.Instant;
import java.util.UUID;

/** A normalized reservation fact from one source, to be recorded as a canonical version. */
public record ReservationInput(
    String sourceSystem,
    String reservationId,
    Instant reservationAt,
    int partySize,
    String status,
    String tableName,
    String sourceChannel,
    String partyName,
    UUID rawRecordId) {}
```

- [ ] **Step 3: Write `CanonicalReservation`**

Create `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservation.java`:

```java
package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a source's reservation fact. Fact fields are immutable; a correction closes this
 * row and inserts a successor sharing the same logical identity (the OpenTable reservation id).
 */
@Entity
@Table(name = "canonical_reservation")
class CanonicalReservation extends BitemporalEntity {
  @Column(name = "reservation_at", nullable = false, updatable = false)
  private Instant reservationAt;

  @Column(name = "party_size", nullable = false, updatable = false)
  private int partySize;

  @Column(name = "status", nullable = false, updatable = false)
  private String status;

  @Column(name = "table_name", updatable = false)
  private String tableName;

  @Column(name = "source_channel", updatable = false)
  private String sourceChannel;

  @Column(name = "party_name", updatable = false)
  private String partyName;

  protected CanonicalReservation() {}

  private CanonicalReservation(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      Instant reservationAt,
      int partySize,
      String status,
      String tableName,
      String sourceChannel,
      String partyName) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.reservationAt = reservationAt;
    this.partySize = partySize;
    this.status = status;
    this.tableName = tableName;
    this.sourceChannel = sourceChannel;
    this.partyName = partyName;
  }

  static CanonicalReservation create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      Instant reservationAt,
      int partySize,
      String status,
      String tableName,
      String sourceChannel,
      String partyName) {
    return new CanonicalReservation(
        logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt,
        reservationAt, partySize, status, tableName, sourceChannel, partyName);
  }

  boolean sameFact(ReservationInput input) {
    return Objects.equals(reservationAt, input.reservationAt())
        && partySize == input.partySize()
        && Objects.equals(status, input.status())
        && Objects.equals(tableName, input.tableName())
        && Objects.equals(sourceChannel, input.sourceChannel())
        && Objects.equals(partyName, input.partyName());
  }

  Instant reservationAt() {
    return reservationAt;
  }

  int partySize() {
    return partySize;
  }

  String status() {
    return status;
  }
}
```

- [ ] **Step 4: Write `CanonicalReservationRepository`**

Create `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationRepository.java`:

```java
package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalReservationRepository extends BitemporalRepository<CanonicalReservation> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from CanonicalReservation r where r.sourceRecordRef = :ref "
          + "and r.sourceSystem = :source and r.supersededAt is null")
  Optional<CanonicalReservation> lockCurrentReservation(String ref, String source);

  @Query("select r from CanonicalReservation r where r.sourceRecordRef = :ref and r.supersededAt is null")
  List<CanonicalReservation> findCurrentByRef(String ref);

  @Query("select r from CanonicalReservation r where r.supersededAt is null")
  List<CanonicalReservation> findAllCurrent();
}
```

- [ ] **Step 5: Verify entity↔migration alignment at startup**

Run: `cd backend && ./gradlew compileJava`
Expected: compiles. (The entity↔DDL alignment is enforced by `ddl-auto: validate` in Task 4's integration test, which boots the full context and runs `V11`.)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalReservation.java \
        backend/src/main/java/com/goldys/platform/canonical/ReservationInput.java \
        backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationRepository.java \
        backend/src/main/resources/db/migration/V11__reservations.sql
git commit -m "feat: add canonical reservation entity, migration, and repository"
```

---

## Task 4: `CanonicalReservationService` + `CanonicalReservationIngest` (TDD)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationIngest.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalReservationIntegrationTest.java`

**Interfaces:**
- Consumes: `CanonicalReservationRepository` (Task 3), `ReservationInput` (Task 3).
- Produces: `CanonicalReservationIngest.record(ReservationInput input)` — consumed by Task 6.

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/com/goldys/platform/canonical/CanonicalReservationIntegrationTest.java`:

```java
package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalReservationIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalReservationService service;
  @Autowired CanonicalReservationRepository repository;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_reservation");
  }

  @Test
  void unchangedRetryDoesNotAppendAVersion() {
    ReservationInput input = input("OPENTABLE", "1000000001", "2026-09-23T09:30:00Z");

    CanonicalReservation first = service.record(input);
    CanonicalReservation retry = service.record(input);

    assertThat(retry.id()).isEqualTo(first.id());
    assertThat(repository.findCurrentByRef("1000000001")).hasSize(1);
  }

  @Test
  void changedFactSupersedesThePriorVersion() {
    ReservationInput before = input("OPENTABLE", "1000000002", "2026-09-23T09:30:00Z");
    CanonicalReservation first = service.record(before);
    ReservationInput after =
        new ReservationInput("OPENTABLE", "1000000002", Instant.parse("2026-09-23T09:30:00Z"),
            5, "CANCELLED", null, "OpenTable", "Jones", before.rawRecordId());

    CanonicalReservation corrected = service.record(after);

    assertThat(corrected.id()).isNotEqualTo(first.id());
    assertThat(corrected.logicalEntityId()).isEqualTo(first.logicalEntityId());
    assertThat(repository.findCurrentByRef("1000000002")).hasSize(1);
    assertThat(repository.findCurrentByRef("1000000002").get(0).status()).isEqualTo("CANCELLED");
  }

  @Test
  void differentSourcesKeepSeparateCurrentRows() {
    service.record(input("OPENTABLE", "1000000003", "2026-09-23T09:30:00Z"));
    service.record(input("MANUAL", "1000000003", "2026-09-23T09:30:00Z"));

    assertThat(repository.findCurrentByRef("1000000003")).hasSize(2);
  }

  private ReservationInput input(String source, String id, String at) {
    return new ReservationInput(
        source, id, Instant.parse(at), 4, "BOOKED", "12", "OpenTable", "Smith", rawRecord(source));
  }

  private UUID rawRecord(String source) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, ?, 'test', 'SUCCESS', now(), 1, 1)",
        runId, source);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, ?, 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId, runId, source, new byte[] {1}, "0".repeat(64), 1);
    return recordId;
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests 'CanonicalReservationIntegrationTest'`
Expected: compilation FAIL (`CanonicalReservationService` absent).

- [ ] **Step 3: Write `CanonicalReservationService`**

Create `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationService.java`:

```java
package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical reservation facts, locking the current source fact so concurrent corrections
 * cannot leave two current versions. The logical identity is a deterministic UUID of the
 * reservation id, so the same reservation from two sources shares an id without a matching step.
 */
@Service
class CanonicalReservationService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalReservationRepository repository;

  CanonicalReservationService(CanonicalReservationRepository repository) {
    this.repository = repository;
  }

  @Transactional
  CanonicalReservation record(ReservationInput input) {
    return recordAt(input, CLOCK.instant());
  }

  @Transactional
  CanonicalReservation recordAt(ReservationInput input, Instant recordedAt) {
    UUID logicalId = logicalIdFor(input.reservationId());
    String ref = input.reservationId();
    Optional<CanonicalReservation> current =
        repository.lockCurrentReservation(ref, input.sourceSystem());

    if (current.isPresent()) {
      CanonicalReservation existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalReservation.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.reservationAt(),
            input.partySize(),
            input.status(),
            input.tableName(),
            input.sourceChannel(),
            input.partyName()));
  }

  private static UUID logicalIdFor(String reservationId) {
    return UUID.nameUUIDFromBytes(("reservation:" + reservationId).getBytes(StandardCharsets.UTF_8));
  }
}
```

- [ ] **Step 4: Write `CanonicalReservationIngest`**

Create `backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationIngest.java`:

```java
package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical reservations, so connectors can record parsed facts. */
@Service
public class CanonicalReservationIngest {
  private final CanonicalReservationService service;

  public CanonicalReservationIngest(CanonicalReservationService service) {
    this.service = service;
  }

  public void record(ReservationInput input) {
    service.record(input);
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests 'CanonicalReservationIntegrationTest' spotlessCheck`
Expected: PASS (3 tests). If startup fails with a schema-validation error, the entity and `V11` disagree — reconcile them.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationService.java \
        backend/src/main/java/com/goldys/platform/canonical/CanonicalReservationIngest.java \
        backend/src/test/java/com/goldys/platform/canonical/CanonicalReservationIntegrationTest.java
git commit -m "feat: add canonical reservation service with bitemporal upsert"
```

---

## Task 5: `OpenTableClient` interface + Playwright implementation + config

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableClient.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/PlaywrightOpenTableClient.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableConfig.java`
- Modify: `backend/build.gradle` — add Playwright dependency.

**Interfaces:**
- Consumes: `OpenTableReservation`/`OpenTableCsvParser` (Task 2), `CanonicalReservationIngest` (Task 4).
- Produces: `OpenTableClient` with `void login(String email, String password)` and `byte[] exportReservationsCsv(LocalDate from, LocalDate to)` — consumed by Task 6.

The Playwright selectors are provisional and must match `docs/connectors/opentable.md` (Task 1); adjust if the real GuestCenter flow differs.

- [ ] **Step 1: Add the Playwright dependency**

In `backend/build.gradle`, add after the commons-csv line (`implementation 'org.apache.commons:commons-csv:1.11.0'`):

```gradle
	implementation 'com.microsoft.playwright:playwright:1.49.0'
```

- [ ] **Step 2: Write `OpenTableClient`**

Create `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableClient.java`:

```java
package com.goldys.platform.connectors.opentable;

import java.time.LocalDate;

/**
 * Browser-automation port for OpenTable GuestCenter. An interface so tests can stub the browser,
 * which cannot run in CI.
 */
public interface OpenTableClient {
  void login(String email, String password);

  byte[] exportReservationsCsv(LocalDate from, LocalDate to);
}
```

- [ ] **Step 3: Write `PlaywrightOpenTableClient`**

Create `backend/src/main/java/com/goldys/platform/connectors/opentable/PlaywrightOpenTableClient.java`:

```java
package com.goldys.platform.connectors.opentable;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Playwright-backed GuestCenter pull: login, open the reservations report, capture the CSV export. */
public class PlaywrightOpenTableClient implements OpenTableClient {
  private static final String BASE = "https://guestcenter.opentable.com";

  private final Playwright playwright;
  private final Browser browser;
  private BrowserContext context;

  public PlaywrightOpenTableClient() {
    try {
      this.playwright = Playwright.create();
      this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    } catch (RuntimeException e) {
      throw new ConnectorFetchException("CONNECTOR_BROWSER_FAILED", "Chromium launch failed", e);
    }
  }

  @Override
  public void login(String email, String password) {
    context = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(true));
    Page page = context.newPage();
    page.navigate(BASE + "/login");
    page.locator("input[name=email]").fill(email);
    page.locator("input[name=password]").fill(password);
    page.locator("button[type=submit]").click();
    page.waitForURL("**/guestcenter/**");
  }

  @Override
  public byte[] exportReservationsCsv(LocalDate from, LocalDate to) {
    Page page = context.newPage();
    page.navigate(BASE + "/reports/reservations");
    page.locator("input[name=from]").fill(from.format(DateTimeFormatter.ISO_LOCAL_DATE));
    page.locator("input[name=to]").fill(to.format(DateTimeFormatter.ISO_LOCAL_DATE));
    Download download =
        page.waitForDownload(() -> page.getByRole("button", new Page.GetByRoleOptions().setName("Export")).click());
    try {
      return Files.readAllBytes(download.path());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
```

- [ ] **Step 4: Write `OpenTableConfig`**

Create `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableConfig.java`:

```java
package com.goldys.platform.connectors.opentable;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the OpenTable connector, reading credentials from environment variables. */
@Configuration
public class OpenTableConfig {
  @Bean
  OpenTableClient opentableClient() {
    return new PlaywrightOpenTableClient();
  }

  @Bean
  OpenTableConnector opentableConnector(
      @Value("${opentable.email:}") String email,
      @Value("${opentable.password:}") String password,
      OpenTableClient client,
      OpenTableCsvParser parser,
      CanonicalReservationIngest canonical,
      @Value("${opentable.window-before-days:30}") int windowBeforeDays,
      @Value("${opentable.window-after-days:14}") int windowAfterDays) {
    return new OpenTableConnector(
        client, email, password, parser, canonical, windowBeforeDays, windowAfterDays);
  }
}
```

Note: `OpenTableConnector` does not exist yet — this task and Task 6 are split so each has a test cycle. `./gradlew compileJava` will fail until Task 6's connector is added; do not commit until Task 6 Step 1 is in place, or comment the `opentableConnector` bean out temporarily and add it in Task 6.

- [ ] **Step 5: Verify it compiles and formats**

Run: `cd backend && ./gradlew compileJava spotlessCheck`
Expected: PASS (after Task 6's connector exists, or with the bean temporarily commented).

- [ ] **Step 6: Commit**

```bash
git add backend/build.gradle \
        backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableClient.java \
        backend/src/main/java/com/goldys/platform/connectors/opentable/PlaywrightOpenTableClient.java \
        backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableConfig.java
git commit -m "feat: add OpenTable Playwright client and wiring"
```

---

## Task 6: `OpenTableConnector` (TDD with stubbed client)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableConnector.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableConnectorTest.java`

**Interfaces:**
- Consumes: `OpenTableClient` (Task 5), `OpenTableCsvParser`/`OpenTableReservation` (Task 2), `CanonicalReservationIngest`/`ReservationInput` (Task 4).
- Produces: `OpenTableConnector implements SourceConnector` — `sourceSystem()` `"OPENTABLE"`, `connectorName()` `"opentable-guestcenter"`, `fetch(String watermark, IngestionSink sink)`. Auto-registered via `List<SourceConnector>` in `IngestionService`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableConnectorTest.java`:

```java
package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenTableConnectorTest {

  private final byte[] csv = fixtureCsv();
  private final OpenTableClient client = mock(OpenTableClient.class);
  private final OpenTableCsvParser parser = new OpenTableCsvParser("Australia/Sydney");
  private final CanonicalReservationIngest canonical = mock(CanonicalReservationIngest.class);

  private OpenTableConnector connector() {
    return new OpenTableConnector(client, "e@example.com", "pw", parser, canonical, 30, 14);
  }

  @Test
  void fetchesScrapePayloadAndCanonicalizesEachRow() {
    when(client.exportReservationsCsv(any(), any())).thenReturn(csv);
    AtomicReference<FetchedPayload> captured = new AtomicReference<>();
    IngestionSink sink = payload -> {
      captured.set(payload);
      return UUID.randomUUID();
    };

    connector().fetch(null, sink);

    assertThat(captured.get().fetchMethod()).isEqualTo(FetchMethod.SCRAPE);
    assertThat(captured.get().contentType()).isEqualTo("text/csv");
    assertThat(captured.get().fetcherIdentity()).isEqualTo("opentable-guestcenter");

    ArgumentCaptor<ReservationInput> input = ArgumentCaptor.forClass(ReservationInput.class);
    verify(canonical, org.mockito.Mockito.times(2)).record(input.capture());
    assertThat(input.getAllValues().get(0).reservationId()).isEqualTo("1000000001");
    assertThat(input.getAllValues().get(0).status()).isEqualTo("BOOKED");
    assertThat(input.getAllValues().get(0).sourceSystem()).isEqualTo("OPENTABLE");
  }

  @Test
  void authFailurePropagates() {
    doThrow(new ConnectorFetchException("CONNECTOR_AUTH_FAILED", "bad login"))
        .when(client).login(any(), any());

    assertThatThrownBy(() -> connector().fetch(null, p -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("bad login");
  }

  @Test
  void browserFailurePropagates() {
    when(client.exportReservationsCsv(any(), any()))
        .thenThrow(new ConnectorFetchException("CONNECTOR_BROWSER_FAILED", "no chromium"));

    assertThatThrownBy(() -> {
          OpenTableConnector c = connector();
          c.fetch(null, p -> UUID.randomUUID());
        })
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("no chromium");
  }

  private static byte[] fixtureCsv() {
    return ("Reservation ID,Date,Time,Party Size,Status,Table,Source,Guest Name\n"
            + "1000000001,2026-09-23,19:30,4,Booked,12,OpenTable,Smith\n"
            + "1000000002,2026-09-23,18:00,2,Seated,7,OpenTable,Jones\n")
        .getBytes(StandardCharsets.UTF_8);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests 'OpenTableConnectorTest'`
Expected: compilation FAIL (`OpenTableConnector` absent).

- [ ] **Step 3: Write `OpenTableConnector`**

Create `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableConnector.java`:

```java
package com.goldys.platform.connectors.opentable;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/** Pulls OpenTable reservations from GuestCenter, streaming the CSV to the sink and canonicalizing each row. */
public class OpenTableConnector implements SourceConnector {
  private final OpenTableClient client;
  private final String email;
  private final String password;
  private final OpenTableCsvParser parser;
  private final CanonicalReservationIngest canonical;
  private final int windowBeforeDays;
  private final int windowAfterDays;

  public OpenTableConnector(
      OpenTableClient client,
      String email,
      String password,
      OpenTableCsvParser parser,
      CanonicalReservationIngest canonical,
      int windowBeforeDays,
      int windowAfterDays) {
    this.client = client;
    this.email = email;
    this.password = password;
    this.parser = parser;
    this.canonical = canonical;
    this.windowBeforeDays = windowBeforeDays;
    this.windowAfterDays = windowAfterDays;
  }

  @Override
  public String sourceSystem() {
    return "OPENTABLE";
  }

  @Override
  public String connectorName() {
    return "opentable-guestcenter";
  }

  @Override
  public void fetch(String watermark, IngestionSink sink) {
    client.login(email, password);

    LocalDate today = LocalDate.now();
    byte[] csv = client.exportReservationsCsv(
        today.minusDays(windowBeforeDays), today.plusDays(windowAfterDays));

    UUID rawId =
        sink.accept(
            new FetchedPayload(
                FetchMethod.SCRAPE,
                "text/csv",
                csv,
                StandardCharsets.UTF_8.name(),
                "opentable-guestcenter"));

    for (OpenTableReservation r : parser.parse(csv)) {
      canonical.record(
          new ReservationInput(
              "OPENTABLE",
              r.reservationId(),
              r.reservationAt(),
              r.partySize(),
              r.status(),
              r.table(),
              r.sourceChannel(),
              r.partyName(),
              rawId));
    }
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests 'OpenTableConnectorTest' spotlessCheck`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableConnector.java \
        backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableConnectorTest.java
git commit -m "feat: add OpenTable connector"
```

---

## Task 7: Headless Chromium provisioning + docs + full-suite verification

**Files:**
- Modify: `backend/Dockerfile` — install Chromium OS deps and pre-download the browser.
- Modify: `.env.example` — document `OPENTABLE_EMAIL`, `OPENTABLE_PASSWORD`, `OPENTABLE_TIMEZONE`.
- Create: `backend/src/test/java/com/goldys/platform/connectors/opentable/PlaywrightSmokeTest.java` — opt-in smoke (env-gated).

**Interfaces:**
- Consumes: everything from Tasks 2–6.

- [ ] **Step 1: Add Chromium deps to the runtime image**

In `backend/Dockerfile`, replace the `# ---- runtime ----` stage so the JRE image gains the libraries Playwright needs and the browser is downloaded into the image:

```dockerfile
# ---- runtime ----
FROM eclipse-temurin:25-jre AS runtime
RUN useradd --system --uid 1001 app
WORKDIR /app

# Headless Chromium for the OpenTable browser pull. The library list is Playwright's
# canonical set; install --with-deps also pulls the browser itself into the image layer.
RUN apt-get update && apt-get install -y --no-install-recommends \
      libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 \
      libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 \
      libasound2 libpango-1.0-0 libcairo2 fonts-liberation wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Note: `Playwright.create()` downloads the Chromium binary to `~/.cache/ms-playwright` on first use at runtime. If the deployment has no outbound download at runtime, bake the browser in by running `./gradlew playwrightInstallChromium` (or `java -cp <playwright-driver> com.microsoft.playwright.CLI install chromium`) in a `RUN` step as the `app` user and set `PLAYWRIGHT_BROWSERS_PATH`. Verify the exact install command against the Playwright Java version pinned in Task 5; adjust this step to match.

- [ ] **Step 2: Add an opt-in Playwright smoke test**

Create `backend/src/test/java/com/goldys/platform/connectors/opentable/PlaywrightSmokeTest.java`:

```java
package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Proves headless Chromium launches in this environment. Opt-in; needs Chromium installed. */
@EnabledIfEnvironmentVariable(named = "PLAYWRIGHT_SMOKE", matches = "true")
class PlaywrightSmokeTest {
  @Test
  void launchesHeadlessChromium() {
    try (Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
      Page page = browser.newPage();
      page.setContent("<title>ok</title>");
      assertThat(page.title()).isEqualTo("ok");
    }
  }
}
```

- [ ] **Step 3: Verify the smoke test passes where Chromium is present**

Run: `cd backend && PLAYWRIGHT_SMOKE=true ./gradlew test --tests 'PlaywrightSmokeTest'`
Expected: PASS once Chromium is installed locally (`Playwright.create()` auto-downloads it on first run).

- [ ] **Step 4: Document credentials in `.env.example`**

Append to `.env.example`:

```bash
# ---- OpenTable connector (browser pull of GuestCenter) ----
# OPENTABLE_EMAIL=reservations@example.com
# OPENTABLE_PASSWORD=
# Optional: venue timezone used to interpret GuestCenter date/time columns.
# OPENTABLE_TIMEZONE=Australia/Sydney
```

- [ ] **Step 5: Run the full suite and format check**

Run: `cd backend && ./gradlew test spotlessCheck`
Expected: all tests PASS. The env-gated `PlaywrightSmokeTest` is skipped unless `PLAYWRIGHT_SMOKE=true`.

- [ ] **Step 6: Commit**

```bash
git add backend/Dockerfile .env.example \
        backend/src/test/java/com/goldys/platform/connectors/opentable/PlaywrightSmokeTest.java
git commit -m "chore: provision headless Chromium and document OpenTable env vars"
```

---

## Self-Review

**Spec coverage:**
- §5.1 components → Tasks 2, 3, 4, 5, 6 cover parser/record, entity+repo, service+ingest, client+config, connector. ✓
- §5.2 data flow → Task 6 `fetch()`. ✓
- §5.3 canonical model → Tasks 3–4. ✓ (reservation id realized as `source_record_ref`; `table` → `table_name`, noted in Global Constraints.)
- §5.4 error handling → `CONNECTOR_SCHEMA_MISMATCH` (Task 2), `CONNECTOR_AUTH_FAILED`/`CONNECTOR_BROWSER_FAILED` (Task 6 + Task 5). ✓
- §6 testing → parser test (2), connector test (6), canonical integration test (4), smoke test (7). ✓
- §7 risks → Chromium provisioning (7), discovery-first (1), scheduler out of scope (none needed — deferred per spec). ✓
- §8 success criteria → idempotency/supersede (4), classified failures (2/6), byte-faithful SCRAPE payload (6). ✓

**Placeholder scan:** none — every task has concrete file contents, commands, and commit messages.

**Type consistency:** `ReservationInput(sourceSystem, reservationId, reservationAt, partySize, status, tableName, sourceChannel, partyName, rawRecordId)` is used identically in Tasks 3 (definition), 4 (tests), 6 (connector + test). `OpenTableReservation` field accessors (`reservationId`, `reservationAt`, `partySize`, `status`, `table`, `sourceChannel`, `partyName`) match between Tasks 2 and 6. `CanonicalReservationIngest.record` matches Tasks 4 and 6.

**Review Focus:** each of the five failure modes maps to a test — empty export (Task 2), missing column (Task 2), idempotent re-import (Task 4), supersede on change (Task 4), classified auth/browser failure (Task 6). ✓
