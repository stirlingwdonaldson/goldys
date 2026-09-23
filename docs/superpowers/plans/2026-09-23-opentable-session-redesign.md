# OpenTable Connector Session Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the OpenTable connector so it reuses a persistent authenticated GuestCenter session, logs in with human-like pacing over a Fortress stealth engine (CDP sidecar), and surfaces a clear "manual re-auth" failure instead of fighting the hCaptcha every run.

**Architecture:** Replace `PlaywrightOpenTableClient` with `FortressOpenTableClient`, which connects to a Fortress sidecar over CDP, loads/saves a Playwright `storage_state` file, validates the session on `authenticate()`, and runs the corrected three-stage GuestCenter→Okta→hCaptcha login with paced input only when the session is missing/expired. The `OpenTableClient` interface changes `login(email, password)` → `authenticate()`, with credentials moved into the client's config.

**Tech Stack:** Java 25, Spring Boot 3.5, Playwright (Java) 1.49.0 (CDP only, no local browser), Fortress stealth Chromium sidecar, docker-compose, JUnit 5 + Mockito + AssertJ, spotless.

**Spec:** `docs/superpowers/specs/2026-09-23-opentable-session-redesign.md`

## Global Constraints

- Backend in `backend/`; run Gradle from there (`./gradlew ...`); Java 25; spotless/google-java-format clean (`./gradlew spotlessCheck`).
- Connector is one-way; `sourceSystem()` `"OPENTABLE"`, `connectorName()` `"opentable-guestcenter"`, raw payloads use `FetchMethod.SCRAPE`.
- Credentials and the session file are sensitive: never commit them; the session file holds live cookies.
- Failures are `ConnectorFetchException(failureType, message)`; messages are operator-facing and must never leak credentials/tokens/payload.
- `OpenTableClient` must stay an interface so `OpenTableConnectorTest` can stub the browser (which cannot run in CI).
- Conventional Commits, atomic; work is on a feature branch (`feature/opentable-session-redesign`), never `main`.
- Playwright Java API names below are from 1.49.0; if a name differs at compile time, fix it minimally (as the `AriaRole`/`getByRole` correction was in the prior work) and note it in the report.

## Review Focus

Five failure modes the spec implies, each pinned by a test in the task noted:

1. **`authenticate()` must run before `exportReservationsCsv()`** — an export on a dead session would fail confusingly. → Task 2 (`InOrder`).
2. **`CONNECTOR_AUTH_FAILED` from `authenticate()` must propagate** — never look like "no new data". → Task 2.
3. **`CONNECTOR_BROWSER_FAILED` from `authenticate()` must propagate** — unreachable Fortress must be a classified failure, not an NPE/hang. → Task 2.
4. **Pacing values stay within their documented bounds** — off-by-one in the random ranges would break the "human-like" premise. → Task 1.
5. **Credentials must not live in the connector** — they move to the client's config; the connector constructor no longer accepts `email`/`password`. → Task 2 (compile-level, exercised by the updated test).

---

## File Structure

New (under `backend/`):

- `src/main/java/com/goldys/platform/connectors/opentable/HumanPacing.java` — randomized pacing values.
- `src/main/java/com/goldys/platform/connectors/opentable/FortressOpenTableClient.java` — CDP client with session lifecycle + paced login.
- `src/test/java/com/goldys/platform/connectors/opentable/HumanPacingTest.java`
- `src/test/java/com/goldys/platform/connectors/opentable/FortressSmokeTest.java`

Modified:

- `src/main/java/com/goldys/platform/connectors/opentable/OpenTableClient.java` — `login` → `authenticate`.
- `src/main/java/com/goldys/platform/connectors/opentable/OpenTableConfig.java` — wire CDP URL, session path, credentials.
- `src/main/java/com/goldys/platform/connectors/opentable/OpenTableConnector.java` — `authenticate()`, drop credentials.
- `src/test/java/com/goldys/platform/connectors/opentable/OpenTableConnectorTest.java`
- `docker-compose.prod.yml` — Fortress sidecar + backend env/volume.
- `.env.example` — new vars.
- `backend/Dockerfile` — remove the now-unneeded Chromium OS deps.

Deleted:

- `src/main/java/com/goldys/platform/connectors/opentable/PlaywrightOpenTableClient.java`
- `src/test/java/com/goldys/platform/connectors/opentable/PlaywrightSmokeTest.java`

---

## Task 1: `HumanPacing` utility + test

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/HumanPacing.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/opentable/HumanPacingTest.java`

**Interfaces:**
- Produces: `HumanPacing.charDelayMs()` (int, [40,90]), `HumanPacing.pauseMs()` (int, [300,1500]), `HumanPacing.mouseSteps()` (int, [10,20]), `HumanPacing.sleep(int ms)` — consumed by Task 2.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/goldys/platform/connectors/opentable/HumanPacingTest.java`:

```java
package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HumanPacingTest {

  @Test
  void charDelayStaysWithinBounds() {
    for (int i = 0; i < 200; i++) {
      assertThat(HumanPacing.charDelayMs()).isBetween(40, 90);
    }
  }

  @Test
  void pauseStaysWithinBounds() {
    for (int i = 0; i < 200; i++) {
      assertThat(HumanPacing.pauseMs()).isBetween(300, 1500);
    }
  }

  @Test
  void mouseStepsStayWithinBounds() {
    for (int i = 0; i < 200; i++) {
      assertThat(HumanPacing.mouseSteps()).isBetween(10, 20);
    }
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./gradlew test --tests 'HumanPacingTest'`
Expected: compilation FAIL (`HumanPacing` absent).

- [ ] **Step 3: Write `HumanPacing`**

Create `backend/src/main/java/com/goldys/platform/connectors/opentable/HumanPacing.java`:

```java
package com.goldys.platform.connectors.opentable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Randomized human-like pacing for browser interaction. Instant fill/click is a bot signal; the
 * client uses these bounds to type, move, and pause like a person. Timing only — no browser code.
 */
final class HumanPacing {
  private HumanPacing() {}

  static int charDelayMs() {
    return ThreadLocalRandom.current().nextInt(40, 91); // [40, 90]
  }

  static int pauseMs() {
    return ThreadLocalRandom.current().nextInt(300, 1501); // [300, 1500]
  }

  static int mouseSteps() {
    return ThreadLocalRandom.current().nextInt(10, 21); // [10, 20]
  }

  static void sleep(int ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd backend && ./gradlew test --tests 'HumanPacingTest' spotlessCheck`
Expected: PASS (3 tests), spotless clean.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/opentable/HumanPacing.java \
        backend/src/test/java/com/goldys/platform/connectors/opentable/HumanPacingTest.java
git commit -m "feat: add human-like pacing helper for OpenTable client"
```

---

## Task 2: Client swap — `FortressOpenTableClient` + interface + config + connector

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/FortressOpenTableClient.java`
- Create: `backend/src/test/java/com/goldys/platform/connectors/opentable/FortressSmokeTest.java`
- Modify: `OpenTableClient.java`, `OpenTableConfig.java`, `OpenTableConnector.java`, `OpenTableConnectorTest.java`
- Delete: `PlaywrightOpenTableClient.java`, `PlaywrightSmokeTest.java`

**Interfaces:**
- Consumes: `HumanPacing` (Task 1), `OpenTableCsvParser`/`OpenTableReservation`/`CanonicalReservationIngest` (existing).
- Produces: `OpenTableClient` with `void authenticate()`, `byte[] exportReservationsCsv(LocalDate, LocalDate)`, `void close()`.

This task touches several files at once because the interface change ripples through the connector, its config, and its test. Do all steps before committing.

- [ ] **Step 1: Update the `OpenTableClient` interface**

Replace the whole `OpenTableClient.java` body:

```java
package com.goldys.platform.connectors.opentable;

import java.time.LocalDate;

/**
 * Browser-automation port for OpenTable GuestCenter. An interface so tests can stub the browser,
 * which cannot run in CI.
 */
public interface OpenTableClient {
  /** Ensure an authenticated GuestCenter session, auto-logging in only if the stored session is missing or expired. */
  void authenticate();

  byte[] exportReservationsCsv(LocalDate from, LocalDate to);

  /** Releases the browser connection and Playwright resources. Safe to call multiple times. */
  void close();
}
```

- [ ] **Step 2: Write `FortressOpenTableClient`**

Create `backend/src/main/java/com/goldys/platform/connectors/opentable/FortressOpenTableClient.java`:

```java
package com.goldys.platform.connectors.opentable;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Fortress-backed GuestCenter client. Connects to a Fortress stealth-Chromium sidecar over CDP,
 * reuses a persisted storage_state session, and logs in with human-like pacing only when needed.
 */
public class FortressOpenTableClient implements OpenTableClient {
  private static final String BASE = "https://guestcenter.opentable.com";

  private final String cdpUrl;
  private final String email;
  private final String password;
  private final Path sessionPath;

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;

  public FortressOpenTableClient(String cdpUrl, String email, String password, Path sessionPath) {
    this.cdpUrl = cdpUrl;
    this.email = email;
    this.password = password;
    this.sessionPath = sessionPath;
  }

  @Override
  public void authenticate() {
    ensureConnected();
    if (tryReuseSession()) {
      return;
    }
    login();
  }

  private boolean tryReuseSession() {
    if (!Files.exists(sessionPath)) {
      return false;
    }
    try {
      context =
          browser.newContext(
              new Browser.NewContextOptions()
                  .setAcceptDownloads(true)
                  .setStorageStatePath(sessionPath));
      Page page = context.newPage();
      page.navigate(BASE + "/reports/reservations");
      // A valid session stays on the report; an expired one redirects to login or Okta.
      return !page.url().contains("/login") && !page.url().contains("restauth");
    } catch (RuntimeException e) {
      if (context != null) {
        context.close();
        context = null;
      }
      return false;
    }
  }

  private void login() {
    try {
      if (context != null) {
        context.close();
      }
      context = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(true));
      Page page = context.newPage();
      page.navigate(BASE + "/login");

      // Stage 1: GuestCenter email -> Continue -> Okta.
      Locator emailField = page.locator("input[name=email]");
      moveMouse(page, emailField);
      typeHuman(page, emailField, email);
      pause();
      page.locator("button[type=submit]").click();
      page.waitForURL("**restauth.opentable.com/**");

      // Stage 2: Okta identifier -> Next.
      Locator identifier = page.locator("input[name=identifier]");
      if (identifier.inputValue() == null || identifier.inputValue().isBlank()) {
        moveMouse(page, identifier);
        typeHuman(page, identifier, email);
      }
      pause();
      page.locator("input[type=submit]").click();
      page.waitForSelector("input[type=password]");

      // Stage 3: Okta password -> Sign in (invisible hCaptcha may auto-pass or challenge).
      Locator passwordField = page.locator("input[type=password]");
      moveMouse(page, passwordField);
      typeHuman(page, passwordField, password);
      pause();
      page.locator("input[type=submit]").click();
      page.waitForURL("**guestcenter.opentable.com/**");

      // Persist the session for the next run.
      if (sessionPath.getParent() != null) {
        Files.createDirectories(sessionPath.getParent());
      }
      context.storageState(new BrowserContext.StorageStateOptions().setPath(sessionPath));
    } catch (IOException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_AUTH_FAILED", "Failed to persist the OpenTable session at " + sessionPath, e);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_AUTH_FAILED",
          "OpenTable login failed (hCaptcha challenge or bad credentials); a manual re-auth may be "
              + "required by placing a valid session at "
              + sessionPath,
          e);
    }
  }

  private void typeHuman(Page page, Locator field, String text) {
    field.click();
    page.keyboard().type(text, new Keyboard.TypeOptions().setDelay(HumanPacing.charDelayMs()));
  }

  private void moveMouse(Page page, Locator locator) {
    Locator.BoundingBox box = locator.boundingBox();
    if (box != null) {
      page.mouse()
          .move(
              box.x + box.width / 2,
              box.y + box.height / 2,
              new Mouse.MoveOptions().setSteps(HumanPacing.mouseSteps()));
    }
  }

  private void pause() {
    HumanPacing.sleep(HumanPacing.pauseMs());
  }

  private void ensureConnected() {
    if (browser == null) {
      try {
        playwright = Playwright.create();
        browser = playwright.chromium().connectOverCDP(cdpUrl);
      } catch (RuntimeException e) {
        throw new ConnectorFetchException(
            "CONNECTOR_BROWSER_FAILED", "Fortress CDP unreachable at " + cdpUrl, e);
      }
    }
  }

  @Override
  public byte[] exportReservationsCsv(LocalDate from, LocalDate to) {
    if (context == null) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "authenticate() must be called before exporting reservations");
    }
    try (Page page = context.newPage()) {
      page.navigate(BASE + "/reports/reservations");
      page.locator("input[name=from]").fill(from.format(DateTimeFormatter.ISO_LOCAL_DATE));
      page.locator("input[name=to]").fill(to.format(DateTimeFormatter.ISO_LOCAL_DATE));
      Download download =
          page.waitForDownload(
              () ->
                  page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export"))
                      .click());
      try {
        return Files.readAllBytes(download.path());
      } catch (IOException e) {
        throw new ConnectorFetchException(
            "CONNECTOR_FETCH_FAILED", "OpenTable CSV download failed", e);
      }
    } catch (ConnectorFetchException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "OpenTable reservation export failed", e);
    }
  }

  @Override
  public void close() {
    if (context != null) {
      context.close();
      context = null;
    }
    if (browser != null) {
      browser.close();
      browser = null;
    }
    if (playwright != null) {
      playwright.close();
      playwright = null;
    }
  }
}
```

- [ ] **Step 3: Rewrite `OpenTableConfig`**

Replace `OpenTableConfig.java`:

```java
package com.goldys.platform.connectors.opentable;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the OpenTable connector, reading credentials and the Fortress CDP endpoint from environment variables. */
@Configuration
public class OpenTableConfig {
  @Bean(destroyMethod = "close")
  OpenTableClient opentableClient(
      @Value("${opentable.fortress-cdp-url:http://localhost:9222}") String cdpUrl,
      @Value("${opentable.email:}") String email,
      @Value("${opentable.password:}") String password,
      @Value("${opentable.session-path:/tmp/opentable-session.json}") String sessionPath) {
    return new FortressOpenTableClient(cdpUrl, email, password, Path.of(sessionPath));
  }

  @Bean
  OpenTableConnector opentableConnector(
      OpenTableClient client,
      OpenTableCsvParser parser,
      CanonicalReservationIngest canonical,
      @Value("${opentable.window-before-days:30}") int windowBeforeDays,
      @Value("${opentable.window-after-days:14}") int windowAfterDays) {
    return new OpenTableConnector(client, parser, canonical, windowBeforeDays, windowAfterDays);
  }
}
```

- [ ] **Step 4: Update `OpenTableConnector`**

Replace the field/constructor/`fetch` so credentials are gone and it calls `authenticate()`:

```java
public class OpenTableConnector implements SourceConnector {
  private final OpenTableClient client;
  private final OpenTableCsvParser parser;
  private final CanonicalReservationIngest canonical;
  private final int windowBeforeDays;
  private final int windowAfterDays;

  public OpenTableConnector(
      OpenTableClient client,
      OpenTableCsvParser parser,
      CanonicalReservationIngest canonical,
      int windowBeforeDays,
      int windowAfterDays) {
    this.client = client;
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
    // `watermark` is intentionally unused: this connector pulls a rolling window anchored
    // on LocalDate.now() rather than resuming from a stored high-water mark.
    client.authenticate();

    LocalDate today = LocalDate.now();
    byte[] csv =
        client.exportReservationsCsv(
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

(Keep the existing imports; remove the now-unused `email`/`password` fields and any unused imports.)

- [ ] **Step 5: Update `OpenTableConnectorTest`**

Replace it so it constructs the connector without credentials, stubs `authenticate()`, and verifies ordering:

```java
package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import org.mockito.InOrder;

class OpenTableConnectorTest {

  private final byte[] csv = fixtureCsv();
  private final OpenTableClient client = mock(OpenTableClient.class);
  private final OpenTableCsvParser parser = new OpenTableCsvParser("Australia/Sydney");
  private final CanonicalReservationIngest canonical = mock(CanonicalReservationIngest.class);

  private OpenTableConnector connector() {
    return new OpenTableConnector(client, parser, canonical, 30, 14);
  }

  @Test
  void authenticatesBeforeExportingAndCanonicalizesEachRow() {
    when(client.exportReservationsCsv(any(), any())).thenReturn(csv);
    AtomicReference<FetchedPayload> captured = new AtomicReference<>();
    IngestionSink sink =
        payload -> {
          captured.set(payload);
          return UUID.randomUUID();
        };

    connector().fetch(null, sink);

    InOrder order = inOrder(client);
    order.verify(client).authenticate();
    order.verify(client).exportReservationsCsv(any(), any());

    assertThat(captured.get().fetchMethod()).isEqualTo(FetchMethod.SCRAPE);
    assertThat(captured.get().contentType()).isEqualTo("text/csv");
    assertThat(captured.get().fetcherIdentity()).isEqualTo("opentable-guestcenter");

    ArgumentCaptor<ReservationInput> input = ArgumentCaptor.forClass(ReservationInput.class);
    verify(canonical, times(2)).record(input.capture());
    assertThat(input.getAllValues().get(0).reservationId()).isEqualTo("1000000001");
    assertThat(input.getAllValues().get(0).sourceSystem()).isEqualTo("OPENTABLE");
  }

  @Test
  void authFailurePropagates() {
    doThrow(new ConnectorFetchException("CONNECTOR_AUTH_FAILED", "bad login"))
        .when(client)
        .authenticate();

    assertThatThrownBy(() -> connector().fetch(null, p -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("bad login");
  }

  @Test
  void browserFailurePropagates() {
    doThrow(new ConnectorFetchException("CONNECTOR_BROWSER_FAILED", "no fortress"))
        .when(client)
        .authenticate();

    assertThatThrownBy(() -> connector().fetch(null, p -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("no fortress");
  }

  private static byte[] fixtureCsv() {
    return ("Reservation ID,Date,Time,Party Size,Status,Table,Source,Guest Name\n"
            + "1000000001,2026-09-23,19:30,4,Booked,12,OpenTable,Smith\n"
            + "1000000002,2026-09-23,18:00,2,Seated,7,OpenTable,Jones\n")
        .getBytes(StandardCharsets.UTF_8);
  }
}
```

- [ ] **Step 6: Delete the obsolete files and add the smoke test**

Delete `PlaywrightOpenTableClient.java` and `PlaywrightSmokeTest.java`:

```bash
git rm backend/src/main/java/com/goldys/platform/connectors/opentable/PlaywrightOpenTableClient.java \
        backend/src/test/java/com/goldys/platform/connectors/opentable/PlaywrightSmokeTest.java
```

Create `backend/src/test/java/com/goldys/platform/connectors/opentable/FortressSmokeTest.java`:

```java
package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Proves the Fortress CDP endpoint is reachable in this environment. Opt-in. */
@EnabledIfEnvironmentVariable(named = "OPENTABLE_SMOKE", matches = "true")
class FortressSmokeTest {
  @Test
  void connectsOverCdpAndLoadsAPage() {
    String cdpUrl = System.getenv().getOrDefault("OPENTABLE_FORTRESS_CDP_URL", "http://localhost:9222");
    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().connectOverCDP(cdpUrl);
      Page page = browser.newPage();
      page.setContent("<title>ok</title>");
      assertThat(page.title()).isEqualTo("ok");
      browser.close();
    }
  }
}
```

- [ ] **Step 7: Compile and run the tests**

Run: `cd backend && ./gradlew compileJava compileTestJava spotlessCheck && ./gradlew test --tests 'OpenTableConnectorTest' --tests 'HumanPacingTest'`
Expected: compiles + spotless clean; 3 + 3 tests PASS. (The `FortressSmokeTest` is skipped — env-gated.) If a Playwright API name differs in 1.49.0, fix it minimally and note it.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/opentable/ backend/src/test/java/com/goldys/platform/connectors/opentable/
git commit -m "feat: rework OpenTable client for persistent session and Fortress CDP"
```

---

## Task 3: Deployment — Fortress sidecar, env, session volume, Dockerfile cleanup

**Files:**
- Modify: `docker-compose.prod.yml`
- Modify: `.env.example`
- Modify: `backend/Dockerfile`

**Interfaces:**
- Consumes: `FortressOpenTableClient` (Task 2) — needs `opentable.fortress-cdp-url`, `opentable.email`, `opentable.password`, `opentable.session-path`.

- [ ] **Step 1: Add the Fortress sidecar and backend env/volume to `docker-compose.prod.yml`**

Add a `fortress` service and wire the backend. Under `services:`, add:

```yaml
  fortress:
    image: tilion/fortress:latest
    command: ["--headless=new", "--no-sandbox", "--remote-debugging-port=9222"]
    restart: unless-stopped
    # Internal only — no host port; the backend reaches it over the compose network.
```

Then in `backend`, add to `environment:`:

```yaml
      OPENTABLE_FORTRESS_CDP_URL: http://fortress:9222
      OPENTABLE_SESSION_PATH: /app/data/opentable-session.json
      OPENTABLE_EMAIL: ${OPENTABLE_EMAIL:-}
      OPENTABLE_PASSWORD: ${OPENTABLE_PASSWORD:-}
```

Add a volume mount and the fortress dependency to `backend`:

```yaml
    volumes:
      - goldys_opentable_data:/app/data
    depends_on:
      postgres:
        condition: service_healthy
      fortress:
        condition: service_started
```

Finally add the new volume next to `goldys_pg_data`:

```yaml
volumes:
  goldys_pg_data:
  goldys_opentable_data:
```

(Verify the exact Fortress image tag and default CMD against Docker Hub — `tilion/fortress:latest`; if the image needs different flags, adjust the `command:` accordingly and note it.)

- [ ] **Step 2: Document the new env vars in `.env.example`**

Append:

```bash
# ---- OpenTable connector (Fortress browser sidecar) ----
# OPENTABLE_EMAIL=reservations@example.com
# OPENTABLE_PASSWORD=
# Fortress sidecar CDP endpoint (set by docker-compose; default http://localhost:9222).
# OPENTABLE_FORTRESS_CDP_URL=http://fortress:9222
# Where the persisted session file lives (holds live cookies — keep private).
# OPENTABLE_SESSION_PATH=/app/data/opentable-session.json
```

- [ ] **Step 3: Remove the now-unneeded Chromium OS deps from `backend/Dockerfile`**

The backend no longer launches a browser (it connects to Fortress over CDP), so the `apt-get install` of Chromium libraries is dead weight. Delete the `RUN apt-get update && apt-get install ... libasound2t64 ... && rm -rf /var/lib/apt/lists/*` block (and its preceding comment) from the `runtime` stage. Keep the rest of the stage (`USER app`, `COPY`, `EXPOSE`, `ENTRYPOINT`) intact.

- [ ] **Step 4: Validate compose config**

Run: `docker compose -f docker-compose.prod.yml config`
Expected: valid YAML, no errors (no Docker daemon needed for `config`). If `docker` is unavailable, `docker compose config` may still fail — in that case, note it and verify by eye.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.prod.yml .env.example backend/Dockerfile
git commit -m "chore: add Fortress sidecar and OpenTable session volume to prod deployment"
```

---

## Self-Review

**Spec coverage:**
- §5.1 interface `authenticate()` → Task 2 Step 1. ✓
- §5.2 session lifecycle (load/validate/reuse/login/save; manual fallback = session file) → Task 2 `FortressOpenTableClient`. ✓
- §5.3 three-stage paced login → Task 2 `login()`. ✓
- §5.4 behavioral pacing → Task 1 + Task 2 helpers. ✓
- §5.5 failure classification (`AUTH_FAILED`, `BROWSER_FAILED`, `FETCH_FAILED`) → Task 2. ✓
- §6 deployment (sidecar, CDP URL, session volume) → Task 3. ✓
- §7 testing (connector test, pacing test, env-gated smoke) → Tasks 1–2. ✓
- §8 risk 5 (provisional report/export selectors) — retained as provisional in `FortressOpenTableClient` (the `/reports/reservations` URL and `Export` button remain the unconfirmed placeholders; flagged in the code + spec, not a task-blocker). ✓

**Placeholder scan:** no TBD/TODO; the only provisional values are the GuestCenter report selectors, which are explicitly out of scope (spec §4) and carried as-is from the prior connector.

**Type consistency:** `OpenTableClient.authenticate()/exportReservationsCsv(from,to)/close()` used identically in `FortressOpenTableClient`, `OpenTableConnector`, and `OpenTableConnectorTest`. `OpenTableConnector(client, parser, canonical, windowBeforeDays, windowAfterDays)` matches `OpenTableConfig.opentableConnector(...)`. `HumanPacing` method names match Task 2's usage.

**Review Focus:** ordering (Task 2 `InOrder`), `AUTH_FAILED` propagation (Task 2), `BROWSER_FAILED` propagation (Task 2), pacing bounds (Task 1), credentials out of connector (Task 2 compile + updated constructor). ✓
