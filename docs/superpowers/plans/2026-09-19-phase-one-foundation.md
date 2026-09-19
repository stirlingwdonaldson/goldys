# Phase 1 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore runnable applications and implement the durable ingestion, authorization, and bitemporal foundations required before Phase 1 connector work begins.

**Architecture:** Build one modular Spring Boot application and one Next.js frontend. Establish exact-byte ingestion and OIDC-backed authorization first, then add a shared bitemporal persistence pattern and concrete sale/shift entities. Keep Phase 2 limited to frozen contract artifacts; do not add unused AI runtime code.

**Tech Stack:** Java 25, Spring Boot 3.5.0, Gradle 9.5.0, PostgreSQL 16, Flyway, JUnit 5, Testcontainers, Next.js 15, React 19, TypeScript 5.6, Tailwind 3.4, Bun.

**Spec:** `docs/superpowers/specs/2026-09-19-phase-one-mvp-design.md`

## Global Constraints

- Keep Gradle 9.5.0, Java 25, Spring Boot 3.5.0, and PostgreSQL 16.
- Keep Hibernate `ddl-auto: validate`; preserve `V1__baseline_schema.sql` and evolve with Flyway.
- Use Bun for frontend package management; do not add npm or Yarn lockfiles under `frontend/`.
- Persist source bytes before parsing; bytes plus SHA-256 are the authoritative payload.
- Keep connector vendor types inside their adapter and keep every connector one-way.
- Use one permission service; denied access is explicit and never silently filtered.
- Do not seed guessed permission mappings or matching tolerances.
- Use atomic Conventional Commits after each verified task.
- Never stage existing unrelated untracked `.opencode/`, `data/`, `AGENTS.md`, root `opencode.jsonc`, or root `package-lock.json` files.

## Execution Prerequisites

The planning environment has Java 25 and Gradle 9.5.0. It does not have Bun,
cannot access the Docker socket, and has no local PostgreSQL client.

Before Task 3, install Bun at the version declared by `frontend/package.json`.
Before Task 5, grant Docker access so Testcontainers can start PostgreSQL 16, or
provide a disposable PostgreSQL 16 service through datasource environment
variables. Do not substitute H2: the plan tests JSONB, `BYTEA`, partial indexes,
Flyway, and PostgreSQL triggers.

## Review Focus

1. Arbitrary binary payloads and invalid UTF-8 must round-trip exactly; Task 8 tests this.
2. Retried identical source facts must not create canonical versions; Task 15 tests this.
3. Payloads accepted before connector failure must survive in a `PARTIAL` run; Task 9 tests this.
4. Concurrent correction must leave exactly one current canonical version; Task 15 tests this.
5. Unknown or inactive OIDC identities must receive `NOT_PERMITTED`; Task 12 tests this.

---

## File Structure

```text
backend/src/main/java/com/goldys/platform/
  api/             health and stable error contracts
  auth/            OIDC staff profiles and sole permission service
  canonical/       bitemporal base, sale items, shifts
  config/          Spring Security configuration
  ingestion/       exact payloads, runs, failures, connector runner
  ingestion/port/  vendor-neutral connector interfaces
backend/src/main/resources/db/migration/
  V1__baseline_schema.sql
  V2__ingestion_ledger.sql
  V3__staff_profiles.sql
  V4__canonical_provenance.sql
backend/src/test/java/com/goldys/platform/
  support/         PostgreSQL 16 Testcontainers setup
  api/, auth/, canonical/, ingestion/
frontend/
  app/             minimal App Router shell
  lib/api/         typed backend contracts
docs/contracts/    frozen Phase 2 interface artifacts
docs/testing.md    reproducible verification workflow
```

### Task 1: Align Guidance With the Rebuild Baseline

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`
- Modify: `.claude/rules/architecture-invariants.md`
- Modify: `docs/design-system.md`

**Interfaces:**
- Consumes: approved Phase 1 spec
- Produces: accurate startup context for every implementation task

- [ ] **Step 1: Prove stale implementation claims remain**

```bash
rg -n "Implemented in code|20 tests, 0 failures|frontend/components|see the javadoc" \
  README.md CLAUDE.md .claude/rules/architecture-invariants.md docs/design-system.md
```

Expected: matches naming deleted classes, tests, and frontend components.

- [ ] **Step 2: Replace only implementation-state claims**

Use this statement in status sections while retaining all product rules:

```markdown
The implementation has been cleared for a deliberate Phase 1 rebuild. The
repository retains build configuration, application configuration, the V1
Flyway baseline, and product/design context. Follow the approved Phase 1 spec
and implementation plans; previously documented classes and screens no longer
exist unless a later commit restores them.
```

Link the approved spec and this plan from `README.md` and `CLAUDE.md`.

- [ ] **Step 3: Verify context accuracy**

```bash
! rg -n "Implemented in code|20 tests, 0 failures|see the javadoc on each class" \
  README.md CLAUDE.md .claude/rules/architecture-invariants.md docs/design-system.md
rg -n "phase-one-mvp-design.md|phase-one-foundation.md" README.md CLAUDE.md
git diff --check
```

Expected: no stale-state match, both plan pointers found, clean diff.

- [ ] **Step 4: Commit**

```bash
git add README.md CLAUDE.md .claude/rules/architecture-invariants.md docs/design-system.md
git commit -m "docs: align guidance with rebuild baseline"
```

### Task 2: Restore Backend Entry Point and Health Contract

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/PlatformApplication.java`
- Create: `backend/src/main/java/com/goldys/platform/api/HealthController.java`
- Create: `backend/src/test/java/com/goldys/platform/api/HealthControllerTest.java`

**Interfaces:**
- Consumes: existing Spring Boot web starter
- Produces: `GET /api/health` returning `HealthResponse("UP")`

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {
  @Test
  void reportsTheApplicationAsUp() {
    assertThat(new HealthController().health().status()).isEqualTo("UP");
  }
}
```

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*HealthControllerTest'
```

Expected: compilation fails because `HealthController` is absent.

- [ ] **Step 3: Implement the entry point and controller**

```java
package com.goldys.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlatformApplication {
  public static void main(String[] args) {
    SpringApplication.run(PlatformApplication.class, args);
  }
}
```

```java
package com.goldys.platform.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
  @GetMapping
  public HealthResponse health() {
    return new HealthResponse("UP");
  }

  public record HealthResponse(String status) {}
}
```

- [ ] **Step 4: Verify GREEN**

```bash
cd backend && ./gradlew test --tests '*HealthControllerTest' spotlessCheck compileJava
```

Expected: all requested tasks pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/PlatformApplication.java \
  backend/src/main/java/com/goldys/platform/api/HealthController.java \
  backend/src/test/java/com/goldys/platform/api/HealthControllerTest.java
git commit -m "chore: restore backend application baseline"
```

### Task 3: Restore the Minimal Frontend Application

**Files:**
- Create: `frontend/app/layout.tsx`
- Create: `frontend/app/page.tsx`
- Create: `frontend/app/globals.css`
- Modify: `frontend/package.json`

**Interfaces:**
- Consumes: existing Next.js, TypeScript, Tailwind configuration
- Produces: buildable root route and `bun run typecheck`

- [ ] **Step 1: Run the currently failing build**

```bash
cd frontend && bun run build
```

Expected: failure because no `app` or `pages` directory exists.

- [ ] **Step 2: Add the type-check script**

Add this script without changing dependency versions:

```json
"typecheck": "tsc --noEmit"
```

- [ ] **Step 3: Add the App Router shell**

```tsx
// frontend/app/layout.tsx
import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "Goldy's Data Platform",
  description: "Operational data reconciliation for Goldy's",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
```

```tsx
// frontend/app/page.tsx
export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-5xl items-center px-8">
      <section aria-labelledby="page-title">
        <p className="text-sm font-medium text-slate-500">Phase 1 foundation</p>
        <h1 id="page-title" className="mt-2 text-3xl font-semibold text-slate-950">
          Goldy&apos;s Data Platform
        </h1>
        <p className="mt-3 max-w-xl text-slate-600">
          Reconciliation features will appear as verified vertical slices land.
        </p>
      </section>
    </main>
  );
}
```

```css
/* frontend/app/globals.css */
@tailwind base;
@tailwind components;
@tailwind utilities;

html,
body {
  margin: 0;
  color: #0f172a;
  background: #ffffff;
}
```

- [ ] **Step 4: Verify GREEN**

```bash
cd frontend && bun run typecheck && bun run lint && bun run build
```

Expected: all commands pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/app frontend/package.json frontend/bun.lock
git commit -m "chore: restore frontend application baseline"
```

### Task 4: Freeze Tool and Widget Boundaries

**Files:**
- Create: `docs/contracts/ai-tool-boundary.md`
- Create: `docs/contracts/widget-spec.schema.json`
- Create: `backend/src/test/java/com/goldys/platform/contracts/WidgetSchemaTest.java`

**Interfaces:**
- Consumes: restricted-tool and JSON-widget architecture invariants
- Produces: widget schema ID `https://goldys.local/schemas/widget-spec-v1.json`

- [ ] **Step 1: Write the failing schema test**

```java
package com.goldys.platform.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WidgetSchemaTest {
  @Test
  void schemaHasAClosedVersionedRootContract() throws Exception {
    JsonNode schema = new ObjectMapper().readTree(Files.readString(
        Path.of("..", "docs", "contracts", "widget-spec.schema.json")));

    assertThat(schema.get("$id").asText())
        .isEqualTo("https://goldys.local/schemas/widget-spec-v1.json");
    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required")).extracting(JsonNode::asText)
        .containsExactly("version", "type", "title", "data");
  }
}
```

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*WidgetSchemaTest'
```

Expected: failure because the schema file is absent.

- [ ] **Step 3: Create the closed schema**

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://goldys.local/schemas/widget-spec-v1.json",
  "title": "GoldysWidgetSpec",
  "type": "object",
  "additionalProperties": false,
  "required": ["version", "type", "title", "data"],
  "properties": {
    "version": { "const": 1 },
    "type": { "enum": ["stat", "table", "line-chart", "bar-chart"] },
    "title": { "type": "string", "minLength": 1, "maxLength": 120 },
    "description": { "type": "string", "maxLength": 500 },
    "data": { "type": "array", "items": { "type": "object" } }
  }
}
```

Write `ai-tool-boundary.md` with these enforceable rules:

```markdown
# AI Tool Boundary Contract

- Tools are registered by stable enum identifier.
- Every dimension, metric, aggregation, and filter operator is an enum.
- No parameter accepts SQL, a free-text field name, or free-text filter expression.
- PermissionService authorizes the staff profile before dispatch.
- Tools read resolved views only.
- Results may reference widget schema version 1; they never emit executable UI code.
- Phase 1 defines this boundary but does not invoke models or implement business tools.
```

- [ ] **Step 4: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*WidgetSchemaTest'
cd .. && git add docs/contracts backend/src/test/java/com/goldys/platform/contracts/WidgetSchemaTest.java
git commit -m "docs: freeze ai tool and widget contracts"
```

### Task 5: Add the PostgreSQL Integration Harness

**Files:**
- Modify: `backend/build.gradle`
- Create: `backend/src/test/java/com/goldys/platform/support/PostgresContainerConfiguration.java`
- Create: `backend/src/test/java/com/goldys/platform/DatabaseMigrationTest.java`

**Interfaces:**
- Consumes: Docker and `postgres:16-alpine`
- Produces: reusable `@Import(PostgresContainerConfiguration.class)` setup

- [ ] **Step 1: Verify Docker access**

```bash
docker info --format '{{.ServerVersion}}'
```

Expected: a server version. If access is denied, stop and request Docker access
or a PostgreSQL 16 test service. Do not change this task to H2.

- [ ] **Step 2: Write the migration smoke test**

```java
package com.goldys.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class DatabaseMigrationTest {
  @Autowired JdbcTemplate jdbc;

  @Test
  void flywayAppliesTheBaseline() {
    Integer count = jdbc.queryForObject(
        "select count(*) from information_schema.tables where table_schema = 'public' "
            + "and table_name in ('raw_record','ingestion_failure','permission',"
            + "'canonical_shift','canonical_sale_item')",
        Integer.class);
    assertThat(count).isEqualTo(5);
  }
}
```

- [ ] **Step 3: Run RED**

```bash
cd backend && ./gradlew test --tests '*DatabaseMigrationTest'
```

Expected: test compilation fails because container support is absent.

- [ ] **Step 4: Add dependencies and configuration**

Add to `backend/build.gradle`:

```groovy
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:postgresql'
```

```java
package com.goldys.platform.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfiguration {
  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("goldys")
        .withUsername("goldys")
        .withPassword("goldys_test_only");
  }
}
```

- [ ] **Step 5: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*DatabaseMigrationTest' spotlessCheck
cd .. && git add backend/build.gradle backend/src/test/java/com/goldys/platform/support \
  backend/src/test/java/com/goldys/platform/DatabaseMigrationTest.java
git commit -m "test: add postgres integration harness"
```

### Task 6: Migrate the Exact-Byte Ingestion Ledger

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__ingestion_ledger.sql`
- Create: `backend/src/test/java/com/goldys/platform/ingestion/IngestionLedgerSchemaTest.java`

**Interfaces:**
- Consumes: V1 `raw_record` and `ingestion_failure`
- Produces: run tracking, exact bytes, digest metadata, and mutation guard

- [ ] **Step 1: Write a failing PostgreSQL test**

The test inserts an ingestion run and arbitrary bytes, reads the bytes back, and
asserts UPDATE fails with `raw_record is append-only`:

```java
byte[] payload = {(byte) 0x50, (byte) 0x4b, (byte) 0x03, (byte) 0x04, (byte) 0xff};
jdbc.update("insert into ingestion_run "
        + "(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) "
        + "values (?, 'CTB', 'fixture', 'RUNNING', now(), 0, 0)", runId);
jdbc.update("insert into raw_record "
        + "(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,"
        + "payload_sha256,payload_byte_length,fetcher_identity,fetched_at) "
        + "values (?, ?, 'CTB', 'FILE_EXPORT', 'application/octet-stream', ?, ?, ?, 'fixture', now())",
    recordId, runId, payload, "0".repeat(64), payload.length);
assertThat(jdbc.queryForObject(
    "select payload_bytes from raw_record where id = ?", byte[].class, recordId))
    .containsExactly(payload);
assertThatThrownBy(() -> jdbc.update(
    "update raw_record set content_type='text/plain' where id=?", recordId))
    .hasMessageContaining("raw_record is append-only");
```

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*IngestionLedgerSchemaTest'
```

Expected: failure because `ingestion_run` and byte columns do not exist.

- [ ] **Step 3: Create V2**

```sql
CREATE TABLE ingestion_run (
    id uuid PRIMARY KEY,
    source_system varchar(255) NOT NULL,
    connector_name varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    started_at timestamp(6) with time zone NOT NULL,
    completed_at timestamp(6) with time zone,
    input_watermark text,
    output_watermark text,
    fetched_count bigint NOT NULL DEFAULT 0 CHECK (fetched_count >= 0),
    persisted_count bigint NOT NULL DEFAULT 0 CHECK (persisted_count >= 0),
    failure_summary text,
    CONSTRAINT ingestion_run_status_check
      CHECK (status IN ('RUNNING','SUCCESS','PARTIAL','FAILED','NO_NEW_DATA'))
);

ALTER TABLE raw_record RENAME COLUMN payload TO parsed_payload;
ALTER TABLE raw_record ALTER COLUMN parsed_payload DROP NOT NULL;
ALTER TABLE raw_record
    ADD COLUMN ingestion_run_id uuid NOT NULL REFERENCES ingestion_run(id),
    ADD COLUMN payload_bytes bytea NOT NULL,
    ADD COLUMN payload_sha256 char(64) NOT NULL,
    ADD COLUMN payload_byte_length bigint NOT NULL CHECK (payload_byte_length >= 0),
    ADD COLUMN character_encoding varchar(64),
    ADD CONSTRAINT raw_record_digest_format_check
      CHECK (payload_sha256 ~ '^[0-9a-f]{64}$');

ALTER TABLE ingestion_failure
    ADD COLUMN ingestion_run_id uuid NOT NULL REFERENCES ingestion_run(id);

CREATE INDEX idx_ingestion_run_source_started
    ON ingestion_run (source_system, started_at DESC);
CREATE INDEX idx_raw_record_run ON raw_record (ingestion_run_id);

CREATE FUNCTION reject_raw_record_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'raw_record is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER raw_record_append_only
    BEFORE UPDATE OR DELETE ON raw_record
    FOR EACH ROW EXECUTE FUNCTION reject_raw_record_mutation();
```

This migration assumes the cleared rebuild has no retained V1 raw rows.
Developers with an old disposable database recreate it.

- [ ] **Step 4: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*IngestionLedgerSchemaTest'
cd .. && git add backend/src/main/resources/db/migration/V2__ingestion_ledger.sql \
  backend/src/test/java/com/goldys/platform/ingestion/IngestionLedgerSchemaTest.java
git commit -m "feat: add exact-byte ingestion ledger"
```

### Task 7: Model Ingestion Runs and Exact Payloads

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/ingestion/IngestionModels.java`
- Create: `backend/src/main/java/com/goldys/platform/ingestion/IngestionRun.java`
- Create: `backend/src/main/java/com/goldys/platform/ingestion/RawRecord.java`
- Create: `backend/src/main/java/com/goldys/platform/ingestion/IngestionRepositories.java`
- Create: `backend/src/test/java/com/goldys/platform/ingestion/IngestionModelTest.java`

**Interfaces:**
- Produces: `IngestionStatus`, `FetchMethod`, run lifecycle, exact raw record, repositories

- [ ] **Step 1: Write lifecycle and defensive-copy tests**

```java
@Test
void completedRunCannotCompleteAgain() {
  var run = IngestionRun.start("CTB", "ctb-export", null, Instant.now());
  run.complete(IngestionStatus.NO_NEW_DATA, null, null, Instant.now());
  assertThatThrownBy(() -> run.complete(
      IngestionStatus.SUCCESS, null, null, Instant.now()))
      .isInstanceOf(IllegalStateException.class);
}

@Test
void rawRecordDefensivelyCopiesBytes() {
  byte[] input = {1, 2, 3};
  RawRecord record = RawRecord.create(runId, "CTB", FetchMethod.FILE_EXPORT,
      "application/octet-stream", input, digest, null, "fixture", Instant.now());
  input[0] = 9;
  byte[] output = record.payloadBytes();
  output[1] = 9;
  assertThat(record.payloadBytes()).containsExactly(1, 2, 3);
}
```

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*IngestionModelTest'
```

Expected: compilation fails because model types do not exist.

- [ ] **Step 3: Implement exact lifecycle rules**

`IngestionModels.java` contains package-private enums:

```java
enum IngestionStatus { RUNNING, SUCCESS, PARTIAL, FAILED, NO_NEW_DATA }
enum FetchMethod { API, FILE_EXPORT, SCRAPE, MANUAL }
```

`IngestionRun.start` assigns a UUID and RUNNING state. `recordFetched` and
`recordPersisted` increment counters only while running. `complete` rejects
RUNNING as a final status and rejects a second completion.

`RawRecord` maps V2, marks source fact columns non-updatable, and clones bytes
in factory and accessor. `IngestionRepositories.java` contains package-private
JPA repositories for runs and raw records.

- [ ] **Step 4: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*IngestionModelTest' spotlessCheck
cd .. && git add backend/src/main/java/com/goldys/platform/ingestion \
  backend/src/test/java/com/goldys/platform/ingestion/IngestionModelTest.java
git commit -m "feat: model immutable ingestion records"
```

### Task 8: Persist Payloads Before Parsing and Record Failures

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/ingestion/IngestionFailure.java`
- Create: `backend/src/main/java/com/goldys/platform/ingestion/IngestionFailureRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/ingestion/RawPayloadService.java`
- Create: `backend/src/main/java/com/goldys/platform/ingestion/IngestionRunService.java`
- Create: `backend/src/test/java/com/goldys/platform/ingestion/IngestionServicesTest.java`

**Interfaces:**
- Consumes: Task 7 models/repositories
- Produces: transactional run start, raw persistence, failure recording, completion

- [ ] **Step 1: Write arbitrary-byte and run-outcome tests**

```java
@Test
void arbitraryBytesSurviveAndReceiveSha256() {
  UUID runId = runs.start("CTB", "ctb-export", null, clock.instant());
  byte[] bytes = {(byte) 0x50, (byte) 0x4b, (byte) 0xff};
  UUID id = payloads.persist(runId, "CTB", FetchMethod.FILE_EXPORT,
      "application/octet-stream", bytes, null, "fixture");
  bytes[0] = 0;
  RawRecord saved = rawRecords.findById(id).orElseThrow();
  assertThat(saved.payloadBytes()).containsExactly(0x50, 0x4b, 0xff);
  assertThat(saved.payloadSha256()).hasSize(64);
}

@Test
void zeroPayloadsWithFailureCompletesAsFailed() {
  UUID runId = runs.start("DEPUTY", "deputy-api", null, clock.instant());
  runs.recordFailure(runId, "AUTH_FAILED", "OAuth token rejected", clock.instant());
  runs.complete(runId, null, clock.instant());
  assertThat(runRepository.findById(runId).orElseThrow().status())
      .isEqualTo(IngestionStatus.FAILED);
}
```

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*IngestionServicesTest'
```

- [ ] **Step 3: Implement services**

`RawPayloadService.persist` is transactional, requires a RUNNING run, clones
input, computes lower-case SHA-256 using `MessageDigest` and `HexFormat`, saves
the raw record, increments persisted count, and returns its UUID.

`IngestionRunService.complete` derives status exactly:

```java
if (persistedCount == 0 && failureCount > 0) status = FAILED;
else if (failureCount > 0) status = PARTIAL;
else if (persistedCount == 0) status = NO_NEW_DATA;
else status = SUCCESS;
```

Failure type remains a string so new adapters can report new failure modes
without a migration. Payloads, credentials, and tokens never enter failure detail.

- [ ] **Step 4: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*IngestionServicesTest' \
  --tests '*IngestionLedgerSchemaTest' spotlessCheck
cd .. && git add backend/src/main/java/com/goldys/platform/ingestion \
  backend/src/test/java/com/goldys/platform/ingestion/IngestionServicesTest.java
git commit -m "feat: persist payloads before transformation"
```

### Task 9: Define the Connector Port and Partial-Failure Runner

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/ingestion/port/FetchedPayload.java`
- Create: `backend/src/main/java/com/goldys/platform/ingestion/port/IngestionSink.java`
- Create: `backend/src/main/java/com/goldys/platform/ingestion/port/SourceConnector.java`
- Create: `backend/src/main/java/com/goldys/platform/ingestion/ConnectorRunner.java`
- Create: `backend/src/test/java/com/goldys/platform/ingestion/ConnectorRunnerTest.java`

**Interfaces:**
- Consumes: Task 8 services
- Produces: `SourceConnector.fetch(String watermark, IngestionSink sink)`

- [ ] **Step 1: Write the partial-failure test**

```java
@Test
void acceptedPayloadSurvivesLaterConnectorFailure() {
  SourceConnector connector = new SourceConnector() {
    public String sourceSystem() { return "LIGHTSPEED"; }
    public String connectorName() { return "fixture"; }
    public void fetch(String watermark, IngestionSink sink) {
      sink.accept(new FetchedPayload(FetchMethod.SCRAPE, "text/html",
          "<table>first</table>".getBytes(UTF_8), "UTF-8", "fixture"));
      throw new ConnectorFetchException("SCHEMA_MISMATCH", "second page changed");
    }
  };

  UUID runId = runner.run(connector, null);

  assertThat(rawRecords.findByIngestionRunId(runId)).hasSize(1);
  assertThat(runs.findById(runId).orElseThrow().status())
      .isEqualTo(IngestionStatus.PARTIAL);
}
```

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*ConnectorRunnerTest'
```

- [ ] **Step 3: Implement the streaming boundary**

```java
public record FetchedPayload(
    FetchMethod fetchMethod,
    String contentType,
    byte[] bytes,
    String characterEncoding,
    String fetcherIdentity) {
  public FetchedPayload { bytes = bytes.clone(); }
  @Override public byte[] bytes() { return bytes.clone(); }
}
```

```java
@FunctionalInterface
public interface IngestionSink {
  UUID accept(FetchedPayload payload);
}

public interface SourceConnector {
  String sourceSystem();
  String connectorName();
  void fetch(String watermark, IngestionSink sink);
}
```

`ConnectorRunner` opens a run, persists each sink payload immediately, records
`ConnectorFetchException`, completes the run, and returns the run UUID.
Unexpected exceptions are recorded as `UNEXPECTED` with exception class only,
then rethrown after completion.

- [ ] **Step 4: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*ConnectorRunnerTest' \
  --tests 'com.goldys.platform.ingestion.*' spotlessCheck
cd .. && git add backend/src/main/java/com/goldys/platform/ingestion \
  backend/src/test/java/com/goldys/platform/ingestion/ConnectorRunnerTest.java
git commit -m "feat: define isolated connector ingestion port"
```

## Checkpoint A: Platform and Ingestion

- [ ] `cd backend && ./gradlew test spotlessCheck build`
- [ ] `cd frontend && bun run typecheck && bun run lint && bun run build`
- [ ] `docker compose config`
- [ ] Confirm raw UPDATE and DELETE fail on a fresh PostgreSQL 16 database.
- [ ] Confirm `git status --short` contains only pre-existing unrelated untracked files.
- [ ] Obtain human review before authorization and canonical tasks.

### Task 10: Add Staff-Profile Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__staff_profiles.sql`
- Create: `backend/src/test/java/com/goldys/platform/auth/StaffProfileSchemaTest.java`

**Interfaces:**
- Produces: unique `(oidc_issuer, oidc_subject)` mapping to department/seniority

- [ ] **Step 1: Write the uniqueness test**

```java
String sql = "insert into staff_profile "
    + "(id,oidc_issuer,oidc_subject,display_name,department,seniority,active,created_at,updated_at) "
    + "values (?, ?, ?, ?, ?, ?, true, now(), now())";
jdbc.update(sql, UUID.randomUUID(), "https://id.example", "subject-1", "Alex", "FOH", "MANAGER");
assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(),
    "https://id.example", "subject-1", "Other", "BOH", "STAFF"))
    .hasRootCauseInstanceOf(SQLException.class);
```

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*StaffProfileSchemaTest'
```

- [ ] **Step 3: Create V3 without seed rows**

```sql
CREATE TABLE staff_profile (
    id uuid PRIMARY KEY,
    oidc_issuer varchar(512) NOT NULL,
    oidc_subject varchar(255) NOT NULL,
    display_name varchar(255) NOT NULL,
    department varchar(100) NOT NULL,
    seniority varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT staff_profile_oidc_identity_key UNIQUE (oidc_issuer, oidc_subject)
);
CREATE INDEX idx_staff_profile_role
    ON staff_profile (department, seniority) WHERE active = true;
```

- [ ] **Step 4: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*StaffProfileSchemaTest'
cd .. && git add backend/src/main/resources/db/migration/V3__staff_profiles.sql \
  backend/src/test/java/com/goldys/platform/auth/StaffProfileSchemaTest.java
git commit -m "feat: add oidc staff profile schema"
```

### Task 11: Implement the Sole Permission Service

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/auth/AuthorizationTypes.java`
- Create: `backend/src/main/java/com/goldys/platform/auth/Permission.java`
- Create: `backend/src/main/java/com/goldys/platform/auth/PermissionRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/auth/PermissionService.java`
- Create: `backend/src/test/java/com/goldys/platform/auth/PermissionServiceTest.java`

**Interfaces:**
- Produces: `require(UserRole, ResourceKey, PermissionAction)` and explicit `AccessDeniedException`

- [ ] **Step 1: Write denied and no-Owner-bypass tests**

```java
@Test
void ownerHasNoHardcodedBypass() {
  PermissionLookup lookup = (role, resource, action) -> false;
  PermissionService service = new PermissionService(lookup);

  assertThatThrownBy(() -> service.require(
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER")),
      new ResourceKey("labor.wages"), PermissionAction.READ))
      .isInstanceOf(AccessDeniedException.class)
      .hasMessageContaining("labor.wages");
}
```

Add an integration case saving a BOH/MANAGER READ grant and proving it does not
grant BOH/OWNER unless an explicit Owner row exists.

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*PermissionServiceTest'
```

- [ ] **Step 3: Implement validated codes and persistence**

`DepartmentCode` and `SeniorityCode` trim, uppercase with `Locale.ROOT`, and
accept `[A-Z][A-Z0-9_-]{0,99}`. `ResourceKey` accepts
`[a-z][a-z0-9.-]{0,99}`. `PermissionAction` is READ or WRITE.

Map V1 `Permission` exactly. Repository lookup uses department, seniority, and
resource. Absent row is denied. READ selects `canRead`; WRITE selects `canWrite`.
There is no ordinal comparison and no Owner branch.

- [ ] **Step 4: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*PermissionServiceTest' spotlessCheck
cd .. && git add backend/src/main/java/com/goldys/platform/auth \
  backend/src/test/java/com/goldys/platform/auth/PermissionServiceTest.java
git commit -m "feat: enforce table-driven permissions"
```

### Task 12: Map OIDC Sessions to Active Staff Profiles

**Files:**
- Modify: `backend/build.gradle`
- Create: `backend/src/main/java/com/goldys/platform/auth/StaffProfile.java`
- Create: `backend/src/main/java/com/goldys/platform/auth/StaffProfileRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/config/SecurityConfig.java`
- Create: `backend/src/test/java/com/goldys/platform/auth/OidcAccessIntegrationTest.java`

**Interfaces:**
- Consumes: OIDC issuer/subject and V3 profile
- Produces: secure session and `GET /api/me`

- [ ] **Step 1: Write the unknown-identity test**

```java
mvc.perform(get("/api/me").with(oidcLogin().idToken(token -> token
        .issuer("https://id.example")
        .subject("unknown"))))
    .andExpect(status().isForbidden())
    .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
```

Also add an active-profile case returning display name, department, and seniority.

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*OidcAccessIntegrationTest'
```

Expected: security APIs are absent.

- [ ] **Step 3: Add approved dependencies**

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
testImplementation 'org.springframework.security:spring-security-test'
```

- [ ] **Step 4: Implement session policy**

`StaffProfileRepository` exposes:

```java
Optional<StaffProfile> findByOidcIssuerAndOidcSubjectAndActiveTrue(
    String oidcIssuer, String oidcSubject);
```

`SecurityConfig` permits `/api/health`, requires authentication elsewhere,
enables OAuth2 login, retains CSRF for browser sessions, and requires POST for
logout. `/api/me` resolves issuer plus subject to an active profile. Missing or
inactive profiles throw `AccessDeniedException`.

Return errors through an `ApiErrorResponse` record nested in `SecurityConfig`
for this task:

```java
record ApiErrorResponse(
    String code, String message, String correlationId, Map<String, String> fields) {}
```

Use an `X-Correlation-ID` request value when valid, otherwise generate a UUID.
Never log claims, tokens, payloads, or credentials.

- [ ] **Step 5: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*OidcAccessIntegrationTest' spotlessCheck build
cd .. && git add backend/build.gradle backend/src/main/java/com/goldys/platform/auth \
  backend/src/main/java/com/goldys/platform/config/SecurityConfig.java \
  backend/src/test/java/com/goldys/platform/auth/OidcAccessIntegrationTest.java
git commit -m "feat: map oidc sessions to staff profiles"
```

### Task 13: Add Canonical Provenance Constraints

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__canonical_provenance.sql`
- Create: `backend/src/test/java/com/goldys/platform/canonical/CanonicalSchemaTest.java`

**Interfaces:**
- Consumes: V1 canonical tables and V2 raw records
- Produces: logical IDs, source references, provenance, one-current-version indexes

- [ ] **Step 1: Write the failing constraint test**

Insert one valid raw record, then two current sale rows with the same source fact:

```sql
insert into canonical_sale_item
  (id, logical_entity_id, source_system, source_record_ref, raw_record_id,
   valid_from, recorded_at, item_name, quantity_sold, amount)
values (?, ?, 'LIGHTSPEED', 'sale-line-1', ?, now(), now(), 'Burger', 1, 18.00)
```

Assert the second insert fails and `raw_record_id` cannot be null.

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*CanonicalSchemaTest'
```

- [ ] **Step 3: Create V4**

Add these columns to both canonical tables:

```sql
ADD COLUMN logical_entity_id uuid NOT NULL,
ADD COLUMN source_system varchar(255) NOT NULL,
ADD COLUMN source_record_ref varchar(512) NOT NULL,
ADD COLUMN raw_record_id uuid NOT NULL REFERENCES raw_record(id)
```

Drop V1 current indexes. Add a partial unique index on
`(source_system, source_record_ref) WHERE superseded_at IS NULL` and a lookup
index on `(logical_entity_id, source_system) WHERE superseded_at IS NULL` for
each table. The migration assumes the cleared rebuild has no canonical rows.

- [ ] **Step 4: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*CanonicalSchemaTest' --tests '*DatabaseMigrationTest'
cd .. && git add backend/src/main/resources/db/migration/V4__canonical_provenance.sql \
  backend/src/test/java/com/goldys/platform/canonical/CanonicalSchemaTest.java
git commit -m "feat: add canonical provenance constraints"
```

### Task 14: Implement the Shared Bitemporal Base

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/BitemporalEntity.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/BitemporalRepository.java`
- Create: `backend/src/test/java/com/goldys/platform/canonical/BitemporalEntityTest.java`

**Interfaces:**
- Produces: shared valid/system time fields and `supersede(Instant)`

- [ ] **Step 1: Write lifecycle tests**

```java
@Test
void supersessionClosesSystemTimeOnly() {
  var entity = TestEntity.recordedAt(Instant.parse("2026-09-19T10:00:00Z"));
  entity.supersede(Instant.parse("2026-09-19T11:00:00Z"));
  assertThat(entity.validFrom()).isEqualTo(TestEntity.VALID_FROM);
  assertThat(entity.supersededAt()).isEqualTo(Instant.parse("2026-09-19T11:00:00Z"));
}

@Test
void supersessionCannotMoveBackwardOrRepeat() {
  var entity = TestEntity.recordedAt(Instant.parse("2026-09-19T10:00:00Z"));
  assertThatThrownBy(() -> entity.supersede(Instant.parse("2026-09-19T09:59:59Z")))
      .isInstanceOf(IllegalArgumentException.class);
  entity.supersede(Instant.parse("2026-09-19T11:00:00Z"));
  assertThatThrownBy(() -> entity.supersede(Instant.parse("2026-09-19T12:00:00Z")))
      .isInstanceOf(IllegalStateException.class);
}
```

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*BitemporalEntityTest'
```

- [ ] **Step 3: Implement the mapped superclass**

Map version ID, logical ID, source, source reference, raw record ID, valid time,
and system time. Fact/provenance fields are immutable. Only `supersededAt`
changes when closing a version. `supersede` rejects timestamps before
`recordedAt` and repeated closure.

Create `@NoRepositoryBean BitemporalRepository<T extends BitemporalEntity>`
extending `JpaRepository<T, UUID>`. Concrete repositories define their own typed
current and as-of queries; do not add speculative generic JPQL.

- [ ] **Step 4: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests '*BitemporalEntityTest' spotlessCheck
cd .. && git add backend/src/main/java/com/goldys/platform/canonical \
  backend/src/test/java/com/goldys/platform/canonical/BitemporalEntityTest.java
git commit -m "feat: define shared bitemporal entity pattern"
```

### Task 15: Persist Sale and Shift Versions Safely

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleItem.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleItemRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleItemService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalShift.java`
- Create: `backend/src/test/java/com/goldys/platform/canonical/CanonicalVersionIntegrationTest.java`

**Interfaces:**
- Consumes: Task 14 base and V4 constraints
- Produces: idempotent source-fact recording, correction, current and as-of reads

- [ ] **Step 1: Write idempotency and as-of tests**

```java
@Test
void unchangedRetryDoesNotCreateAnotherVersion() {
  var first = service.record(input("sale-line-1", 1, money("18.00"), raw1));
  var retry = service.record(input("sale-line-1", 1, money("18.00"), raw2));
  assertThat(retry.id()).isEqualTo(first.id());
  assertThat(repository.findAllBySourceSystemAndSourceRecordRefOrderByRecordedAt(
      "LIGHTSPEED", "sale-line-1")).hasSize(1);
}

@Test
void correctionPreservesSystemTimeHistory() {
  var first = service.recordAt(input("sale-line-1", 1, money("18.00"), raw1), t1);
  var second = service.recordAt(input("sale-line-1", 2, money("36.00"), raw2), t2);
  assertThat(repository.findKnownAt(first.logicalEntityId(), "LIGHTSPEED", t1.plusSeconds(1)))
      .get().extracting(CanonicalSaleItem::quantitySold).isEqualTo(1);
  assertThat(repository.findCurrent(first.logicalEntityId(), "LIGHTSPEED"))
      .get().extracting(CanonicalSaleItem::quantitySold).isEqualTo(2);
  assertThat(second.logicalEntityId()).isEqualTo(first.logicalEntityId());
}
```

Add a two-thread barrier test. Both threads correct the same current source fact.
Assert one commits, the other retries or receives a concurrency exception, and
one row remains current.

- [ ] **Step 2: Run RED**

```bash
cd backend && ./gradlew test --tests '*CanonicalVersionIntegrationTest'
```

- [ ] **Step 3: Implement locked, idempotent sale recording**

Repository contract:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from CanonicalSaleItem s where s.sourceSystem = :source "
    + "and s.sourceRecordRef = :sourceRef and s.supersededAt is null")
Optional<CanonicalSaleItem> lockCurrentSourceFact(String source, String sourceRef);

@Query("select s from CanonicalSaleItem s where s.logicalEntityId = :logicalId "
    + "and s.sourceSystem = :source and s.recordedAt <= :asOf "
    + "and (s.supersededAt is null or s.supersededAt > :asOf)")
Optional<CanonicalSaleItem> findKnownAt(UUID logicalId, String source, Instant asOf);
```

The service locks the current source fact. Equal normalized facts return the
current row unchanged. A correction closes it and inserts a successor with the
same logical ID. A new source fact gets a new provisional logical ID; the later
matching plan may link it before reconciliation begins.

- [ ] **Step 4: Add shift entity on the same base**

Map immutable `staffMemberRef`, `shiftStart`, and `shiftEnd`. Put a
package-private `CanonicalShiftRepository` in the same file with equivalent
current and as-of queries. Do not add matching tolerances.

- [ ] **Step 5: Run GREEN and commit**

```bash
cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.*' \
  --tests '*DatabaseMigrationTest' spotlessCheck
cd .. && git add backend/src/main/java/com/goldys/platform/canonical \
  backend/src/test/java/com/goldys/platform/canonical/CanonicalVersionIntegrationTest.java
git commit -m "feat: persist canonical sale and shift versions"
```

### Task 16: Publish and Run the Foundation Verification Workflow

**Files:**
- Create: `docs/testing.md`
- Modify: `README.md`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: all prior task commands
- Produces: reproducible verification and environment-only OIDC configuration

- [ ] **Step 1: Document exact verification commands**

```bash
docker info
cd backend && ./gradlew test spotlessCheck build
cd frontend && bun install --frozen-lockfile
cd frontend && bun run typecheck && bun run lint && bun run build
docker compose config
```

State that integration tests use PostgreSQL 16 and never H2.

- [ ] **Step 2: Add environment-only OIDC configuration**

Merge this into the existing `spring` tree without defaults for secrets:

```yaml
security:
  oauth2:
    client:
      registration:
        goldys:
          client-id: ${OIDC_CLIENT_ID:}
          client-secret: ${OIDC_CLIENT_SECRET:}
          scope: openid,profile,email
      provider:
        goldys:
          issuer-uri: ${OIDC_ISSUER_URI:}
```

- [ ] **Step 3: Update verified-state documentation honestly**

Link `docs/testing.md`; record commands that actually ran. Do not claim Docker
Compose or live OIDC verification unless those checks succeeded.

- [ ] **Step 4: Run the complete checkpoint**

```bash
cd backend && ./gradlew test spotlessCheck build
cd ../frontend && bun install --frozen-lockfile && bun run typecheck && bun run lint && bun run build
cd .. && docker compose config
git diff --check
```

Expected: all commands pass.

- [ ] **Step 5: Commit**

```bash
git add docs/testing.md README.md backend/src/main/resources/application.yml
git commit -m "docs: publish foundation verification workflow"
```

## Checkpoint B: Foundation Complete

- [ ] V1–V4 migrate from an empty PostgreSQL 16 database.
- [ ] Arbitrary bytes round-trip exactly and raw mutation is rejected.
- [ ] Partial connector failure leaves evidence and a `PARTIAL` run.
- [ ] Unknown/inactive OIDC identities receive `NOT_PERMITTED`.
- [ ] Permission checks are table-driven with no Owner bypass or seed guesses.
- [ ] Canonical retry is idempotent; correction preserves as-of history.
- [ ] Concurrent correction leaves one current source version.
- [ ] Backend tests, build, and Spotless pass.
- [ ] Frontend type check, lint, and build pass with Bun.
- [ ] Tool/widget contracts pass without Phase 2 runtime code.
- [ ] Every task is represented by one verified Conventional Commit.
- [ ] Request whole-plan review before writing the sales vertical-slice plan.

## Follow-On Plans

After this plan lands and is reviewed, create separate detailed plans in order:

1. `phase-one-sales-reconciliation`: paired Lightspeed/CTB samples, approved sale
   matching, both adapters, conflicts, overrides, resolved sales, and UI.
2. `phase-one-remaining-connectors`: Deputy and OpenTable through the proven
   connector/canonical patterns, with approved matching rules.
3. `phase-one-operations-and-validation`: connector status UI, indicators,
   permission-matrix completion, and one-trading-week acceptance.
