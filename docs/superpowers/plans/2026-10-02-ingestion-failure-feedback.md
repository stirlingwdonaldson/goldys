# Ingestion Failure Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface the failure type + message + timestamp already recorded in the ingestion ledger on the connector API and render it in a "Logs" drawer with a copy affordance, so an operator can see and copy what went wrong instead of a bare failure count.

**Architecture:** The backend already writes `failure_type`/`detail`/`occurred_at` into the `ingestion_failure` ledger; this plan exposes the latest run's failure as a new `failure` field on `IngestionRunSummary` and the connector DTO (no new endpoint), then renders it in a global "Logs" drawer. No localStorage — the frontend fetches live data.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA, PostgreSQL 16 (Testcontainers), JUnit 5 + Mockito + AssertJ, Gradle + Spotless; Next.js 15 + React 19 + TypeScript, Tailwind, shadcn/ui (Sheet), Bun, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-02-ingestion-failure-feedback-design.md`

## Global Constraints

- Java 25; run with the committed Gradle wrapper (`./gradlew`). Frontend uses Bun (`bun run`).
- No new endpoint — reuse `GET /api/connectors` and `POST /api/connectors/{source}/run`.
- The failure `detail` is operator-facing and must never carry payload contents/credentials (already guaranteed by `IngestionFailure`'s contract).
- No localStorage — the frontend fetches live data.
- The failure belongs to the **latest run** (a source whose latest run succeeded shows no failure).
- Verification gates: `cd backend && ./gradlew test spotlessCheck build`; `cd frontend && bun run typecheck && bun run lint && bun run test`.
- Atomic Conventional Commits on branch `feature/ingestion-failure-feedback`.

## Review Focus

1. **A source whose latest run succeeded** — shows no failure detail and no crash (failure is null). → Task 1 + Task 3.
2. **A source that has never run** — `failure` is null and the UI shows "Never run", no crash. → Task 3.
3. **A failure with a null `detail` message** — the DTO still carries `type` + `at`; the frontend renders without a blank line. → Task 2 + Task 3.
4. **"Copy all" with an empty list** — disabled, no crash. → Task 3.
5. **The synchronous `Unknown source: LIGHTSPEED` error** — already surfaces via the existing `runError` path (a 400, not a ledger failure); the `failure` field stays null. → Task 4.

---

## Task 1: Backend — `FailureDetail` value type + resolve it into the summary

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/ingestion/FailureDetail.java`
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/IngestionRunSummary.java`
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/IngestionService.java`
- Test: `backend/src/test/java/com/goldys/platform/ingestion/IngestionFailureDetailTest.java`

**Interfaces:**
- Consumes: `IngestionFailure` (existing), `IngestionFailureRepository.findByIngestionRunIdOrderByOccurredAtAsc(UUID)` (existing), `IngestionRun` (existing).
- Produces: `FailureDetail(String type, String message, Instant at)`; `IngestionRunSummary(..., FailureDetail failure)` (6th field, nullable); `IngestionService.latestRunPerSource()` returns summaries carrying `failure`. Used by Task 2.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/ingestion/IngestionFailureDetailTest.java`:

```java
package com.goldys.platform.ingestion;

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
class IngestionFailureDetailTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired IngestionRunService runs;
  @Autowired IngestionService ingestion;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table ingestion_run, ingestion_failure, raw_record cascade");
  }

  @Test
  void latestRunPerSourceCarriesTheLatestFailureDetail() {
    UUID runId = runs.start("CTB", "ctb-revenue", null, Instant.now());
    runs.recordFailure(runId, "AUTH_FAILED", "OAuth token rejected", Instant.now());
    runs.complete(runId, null, Instant.now());

    IngestionRunSummary summary = ingestion.latestRunPerSource().get(0);

    assertThat(summary.failure()).isNotNull();
    assertThat(summary.failure().type()).isEqualTo("AUTH_FAILED");
    assertThat(summary.failure().message()).isEqualTo("OAuth token rejected");
    assertThat(summary.failure().at()).isNotNull();
  }

  @Test
  void latestRunPerSourceHasNullFailureForASuccessfulRun() {
    UUID runId = runs.start("CTB", "ctb-revenue", null, Instant.now());
    runs.recordFetched(runId);
    runs.complete(runId, null, Instant.now());

    IngestionRunSummary summary = ingestion.latestRunPerSource().get(0);

    assertThat(summary.failure()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*IngestionFailureDetailTest'`
Expected: FAIL — `FailureDetail` does not exist / `IngestionRunSummary` has no `failure()` / `summary.failure()` is not a method.

- [ ] **Step 3: Write the value type**

`backend/src/main/java/com/goldys/platform/ingestion/FailureDetail.java`:

```java
package com.goldys.platform.ingestion;

import java.time.Instant;
import java.util.Objects;

/** The latest failure of one ingestion run, exposed to the connector API. */
public record FailureDetail(String type, String message, Instant at) {
  public FailureDetail {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(at, "at");
  }
}
```

- [ ] **Step 4: Extend `IngestionRunSummary`**

Replace `backend/src/main/java/com/goldys/platform/ingestion/IngestionRunSummary.java` with:

```java
package com.goldys.platform.ingestion;

import java.time.Instant;

/** A read-only view of one ingestion run, for the connector-status API. */
public record IngestionRunSummary(
    String sourceSystem,
    String connectorName,
    String status,
    Instant startedAt,
    String failureSummary,
    FailureDetail failure) {}
```

- [ ] **Step 5: Resolve the failure in `IngestionService`**

In `backend/src/main/java/com/goldys/platform/ingestion/IngestionService.java`:

Add the import and the field + constructor parameter:

```java
  private final IngestionFailureRepository failures;
```

Add `IngestionFailureRepository failures` as the last constructor parameter and assign `this.failures = failures;`.

Replace `runSummary` and `latestRunPerSource` with:

```java
  private IngestionRunSummary runSummary(UUID runId) {
    IngestionRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
    return toSummary(run);
  }

  /** The latest run for each source, newest first by start time. */
  public List<IngestionRunSummary> latestRunPerSource() {
    Map<String, IngestionRun> latest = new LinkedHashMap<>();
    for (IngestionRun run : runRepository.findAllByOrderByStartedAtDesc()) {
      latest.putIfAbsent(run.sourceSystem(), run);
    }
    return latest.values().stream().map(this::toSummary).toList();
  }

  private IngestionRunSummary toSummary(IngestionRun run) {
    return new IngestionRunSummary(
        run.sourceSystem(),
        run.connectorName(),
        run.status().name(),
        run.startedAt(),
        run.failureSummary(),
        latestFailure(run.id()));
  }

  private FailureDetail latestFailure(UUID runId) {
    List<IngestionFailure> runFailures =
        failures.findByIngestionRunIdOrderByOccurredAtAsc(runId);
    if (runFailures.isEmpty()) {
      return null;
    }
    IngestionFailure latest = runFailures.get(runFailures.size() - 1);
    return new FailureDetail(latest.failureType(), latest.detail(), latest.occurredAt());
  }
```

Note: the existing `runSummary` method (used by `runConnector`) and `latestRunPerSource` both now return the 6-field `IngestionRunSummary`. Remove the old inline summary construction in `latestRunPerSource`.

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*IngestionFailureDetailTest'`
Expected: PASS.

- [ ] **Step 7: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/main/java/com/goldys/platform/ingestion/FailureDetail.java backend/src/main/java/com/goldys/platform/ingestion/IngestionRunSummary.java backend/src/main/java/com/goldys/platform/ingestion/IngestionService.java backend/src/test/java/com/goldys/platform/ingestion/IngestionFailureDetailTest.java
git commit -m "feat: expose the latest ingestion failure detail"
```

---

## Task 2: Backend — expose `failure` on the connector DTO

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ConnectorStatusControllerTest.java`

**Interfaces:**
- Consumes: `IngestionRunSummary.failure()` (Task 1), `IngestionService` (existing).
- Produces: `ConnectorStatusDto` gains `FailureDetailDto failure` (nullable); `FailureDetailDto(String type, String message, String at)`.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/api/ConnectorStatusControllerTest.java`:

```java
package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.connectors.opentable.OpenTableCsvIngestService;
import com.goldys.platform.ingestion.FailureDetail;
import com.goldys.platform.ingestion.IngestionRunSummary;
import com.goldys.platform.ingestion.IngestionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ConnectorStatusController.class)
@Import(SecurityConfig.class)
class ConnectorStatusControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean IngestionService ingestion;
  @MockitoBean OpenTableCsvIngestService openTableCsvIngest;
  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void connectorsCarryTheFailureDetail() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(ingestion.latestRunPerSource())
        .thenReturn(
            List.of(
                new IngestionRunSummary(
                    "CTB",
                    "ctb-revenue",
                    "FAILED",
                    Instant.parse("2026-10-02T12:00:00Z"),
                    "1 failure(s) recorded",
                    new FailureDetail("AUTH_FAILED", "OAuth token rejected", Instant.parse("2026-10-02T12:00:05Z")))));

    mvc.perform(get("/api/connectors").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.source=='CTB')].failure.type").value("AUTH_FAILED"))
        .andExpect(jsonPath("$[?(@.source=='CTB')].failure.message").value("OAuth token rejected"));
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(UUID.randomUUID(), "o@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
```

Add the missing import `import static org.mockito.ArgumentMatchers.any;` at the top of the test file.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*ConnectorStatusControllerTest'`
Expected: FAIL — `ConnectorStatusDto` has no `failure` field / `toDto` does not map it.

- [ ] **Step 3: Extend the DTO**

In `backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java`:

Add the import `com.goldys.platform.ingestion.FailureDetail;`.

Update `KNOWN_SOURCES` to pass `null` for the new field:

```java
  private static final List<ConnectorStatusDto> KNOWN_SOURCES =
      List.of(
          new ConnectorStatusDto("LIGHTSPEED", "lightspeed-insights", null, "never_run", 0, null),
          new ConnectorStatusDto("CTB", "ctb-revenue", null, "never_run", 0, null),
          new ConnectorStatusDto("OPENTABLE", "opentable-csv-drop", null, "never_run", 0, null),
          new ConnectorStatusDto("DEPUTY", "deputy-api", null, "never_run", 0, null));
```

Update `toDto` and add the mapping helper + nested record:

```java
  private ConnectorStatusDto toDto(IngestionRunSummary run) {
    return new ConnectorStatusDto(
        run.sourceSystem(),
        run.connectorName(),
        run.startedAt().toString(),
        status(run.status()),
        failureCount(run.failureSummary()),
        failure(run.failure()));
  }

  private static FailureDetailDto failure(FailureDetail f) {
    return f == null ? null : new FailureDetailDto(f.type(), f.message(), f.at().toString());
  }

  record ConnectorStatusDto(
      String source,
      String connectorName,
      String lastRunAt,
      String status,
      int failureCount,
      FailureDetailDto failure) {}

  record FailureDetailDto(String type, String message, String at) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ConnectorStatusControllerTest'`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java backend/src/test/java/com/goldys/platform/api/ConnectorStatusControllerTest.java
git commit -m "feat: surface ingestion failure detail on the connector API"
```

---

## Task 3: Frontend — types + `logLine` + Logs drawer + header button

**Files:**
- Modify: `frontend/lib/api/types.ts`
- Create: `frontend/components/logs/log-line.ts`
- Create: `frontend/components/logs/logs-drawer.tsx`
- Modify: `frontend/components/app-shell/app-header.tsx`
- Test: `frontend/components/logs/log-line.test.ts`
- Test: `frontend/components/logs/logs-drawer.test.tsx`

**Interfaces:**
- Consumes: `ConnectorStatus` (types.ts), `useApiData` (existing), `Sheet`/`Button` (shadcn), `ConnectorStatusBadge` (existing).
- Produces: `logLine(connector): string`; `LogsDrawer` (header entry point). Used by Task 4 (nothing) and the header.

- [ ] **Step 1: Write the failing tests**

`frontend/components/logs/log-line.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import { logLine } from "./log-line";
import type { ConnectorStatus } from "@/lib/api";

const base: ConnectorStatus = {
  source: "CTB",
  connectorName: "ctb-revenue",
  lastRunAt: null,
  status: "failed",
  failureCount: 1,
};

describe("logLine", () => {
  it("formats a failed connector with its failure detail", () => {
    const line = logLine({
      ...base,
      failure: { type: "AUTH_FAILED", message: "OAuth token rejected", at: "2026-10-02T12:00:05Z" },
    });
    expect(line).toContain("CTB");
    expect(line).toContain("AUTH_FAILED");
    expect(line).toContain("OAuth token rejected");
  });

  it("formats a connector with no failure", () => {
    const line = logLine({ ...base, status: "success", failure: null });
    expect(line).toContain("CTB");
    expect(line).not.toContain("failure");
  });

  it("formats a failure with a null message", () => {
    const line = logLine({
      ...base,
      failure: { type: "UNEXPECTED", message: null, at: "2026-10-02T12:00:05Z" },
    });
    expect(line).toContain("UNEXPECTED");
  });
});
```

`frontend/components/logs/logs-drawer.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { LogsDrawer } from "./logs-drawer";
import type { ConnectorStatus } from "@/lib/api";

const failed: ConnectorStatus = {
  source: "CTB",
  connectorName: "ctb-revenue",
  lastRunAt: "2026-10-02T12:00:00Z",
  status: "failed",
  failureCount: 1,
  failure: { type: "AUTH_FAILED", message: "OAuth token rejected", at: "2026-10-02T12:00:05Z" },
};

vi.mock("@/lib/use-api-data", () => ({
  useApiData: () => ({ data: [failed], loading: false, error: null, reload: async () => {} }),
}));

describe("LogsDrawer", () => {
  beforeEach(() => {
    vi.stubGlobal("navigator", { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
  });

  it("opens and shows a connector's failure detail", () => {
    render(<LogsDrawer />);
    fireEvent.click(screen.getByRole("button", { name: /logs/i }));
    expect(screen.getByText("CTB")).toBeInTheDocument();
    expect(screen.getByText(/OAuth token rejected/)).toBeInTheDocument();
  });

  it("shows the trigger even when closed", () => {
    render(<LogsDrawer />);
    expect(screen.getByRole("button", { name: /logs/i })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && bun run test -- log-line.test.ts logs-drawer.test.tsx`
Expected: FAIL — `log-line` / `logs-drawer` do not exist.

- [ ] **Step 3: Extend the type**

In `frontend/lib/api/types.ts`, extend `ConnectorStatus`:

```ts
export interface ConnectorStatus {
  source: string;
  connectorName: string;
  lastRunAt: string | null;
  status: ConnectorRunStatus;
  failureCount: number;
  /** Latest run's failure; null when the latest run had no failure. `message` is nullable. */
  failure?: { type: string; message: string | null; at: string } | null;
}
```

- [ ] **Step 4: Write `logLine`**

`frontend/components/logs/log-line.ts`:

```ts
import type { ConnectorStatus } from "@/lib/api";

/** One connector's latest run, formatted as a copyable log line. */
export function logLine(c: ConnectorStatus): string {
  const head = `${c.source} (${c.connectorName}) — ${c.status}`;
  if (c.failure) {
    return `${head}\n  ${c.failure.at} ${c.failure.type}: ${c.failure.message ?? ""}`;
  }
  return head;
}
```

- [ ] **Step 5: Write the drawer**

`frontend/components/logs/logs-drawer.tsx`:

```tsx
"use client";

import { useState } from "react";
import { Copy, ScrollText } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { ConnectorStatusBadge } from "@/components/connectors/connector-status-badge";
import { LoadingState } from "@/components/states/loading-state";
import { useApiData } from "@/lib/use-api-data";
import { logLine } from "./log-line";
import type { ConnectorStatus } from "@/lib/api";

async function copy(text: string) {
  await navigator.clipboard.writeText(text);
}

/** A global drawer listing each source's latest run + failure, with copy affordances. */
export function LogsDrawer() {
  const [open, setOpen] = useState(false);
  const connectors = useApiData((api) => api.listConnectorStatuses());

  const allText = connectors.data?.map(logLine).join("\n\n") ?? "";

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm" className="gap-1.5">
          <ScrollText className="h-4 w-4" aria-hidden="true" />
          Logs
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="flex w-full flex-col gap-4 sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Logs</SheetTitle>
        </SheetHeader>

        <div className="flex items-center justify-end">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => copy(allText)}
            disabled={!allText}
            aria-label="Copy all logs"
          >
            <Copy className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Copy all
          </Button>
        </div>

        <div className="flex-1 space-y-2 overflow-y-auto">
          {connectors.loading ? <LoadingState rows={4} /> : null}
          {connectors.error ? (
            <p className="text-sm text-destructive">{connectors.error.message}</p>
          ) : null}
          {connectors.data?.map((c) => (
            <LogRow key={c.source} connector={c} />
          ))}
        </div>
      </SheetContent>
    </Sheet>
  );
}

function LogRow({ connector: c }: { connector: ConnectorStatus }) {
  return (
    <div className="rounded-md border p-3">
      <div className="flex items-center justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-medium">{c.source}</p>
          <p className="text-xs text-muted-foreground">{c.connectorName}</p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <ConnectorStatusBadge status={c.status} />
          <Button
            variant="ghost"
            size="sm"
            aria-label={`Copy ${c.source} log`}
            onClick={() => copy(logLine(c))}
          >
            <Copy className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </div>
      {c.failure ? (
        <p className="mt-2 text-xs text-destructive">
          {c.failure.at} · {c.failure.type}: {c.failure.message}
        </p>
      ) : (
        <p className="mt-2 text-xs text-muted-foreground">
          {c.lastRunAt ? `Last run ${new Date(c.lastRunAt).toLocaleString()}` : "Never run"}
        </p>
      )}
    </div>
  );
}
```

- [ ] **Step 6: Wire the button into the header**

In `frontend/components/app-shell/app-header.tsx`, add the `LogsDrawer` next to `AskGoldysDrawer`:

```tsx
import { AskGoldysDrawer } from "@/components/ask-goldys/ask-goldys-drawer";
import { LogsDrawer } from "@/components/logs/logs-drawer";
```

and in the header's right cluster:

```tsx
      <div className="ml-auto flex items-center gap-2">
        <LogsDrawer />
        <AskGoldysDrawer seniority={user?.seniority} />
      </div>
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd frontend && bun run test -- log-line.test.ts logs-drawer.test.tsx`
Expected: PASS.

- [ ] **Step 8: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`

```bash
git add frontend/lib/api/types.ts frontend/components/logs/ frontend/components/app-shell/app-header.tsx
git commit -m "feat: add a Logs drawer with copy for connector failures"
```

---

## Task 4: Frontend — show failure detail on the data-health page

**Files:**
- Modify: `frontend/app/(app)/data-health/page.tsx`
- Test: (covered by Task 3's `LogsDrawer`; this page change is a small inline render)

**Interfaces:**
- Consumes: `ConnectorStatus.failure` (Task 3).
- Produces: the data-health connector list shows the failure detail inline.

- [ ] **Step 1: Render the failure detail inline**

In `frontend/app/(app)/data-health/page.tsx`, inside the connector row (`connectors.data.map(...)`), after the `connectorName` line, add a failure line when present. Replace the subtitle block:

```tsx
              <p className="text-xs text-muted-foreground">
                {c.connectorName}
                {c.lastRunAt
                  ? ` · last run ${new Date(c.lastRunAt).toLocaleString()}`
                  : " · never run"}
              </p>
```

with:

```tsx
              <p className="text-xs text-muted-foreground">
                {c.connectorName}
                {c.lastRunAt
                  ? ` · last run ${new Date(c.lastRunAt).toLocaleString()}`
                  : " · never run"}
              </p>
              {c.failure ? (
                <p className="text-xs text-destructive">
                  {c.failure.type}: {c.failure.message}
                </p>
              ) : null}
```

- [ ] **Step 2: Verify the run error path is intact**

The existing `run()` already sets `runError` from a rejected `runConnector` (e.g. `Unknown source: LIGHTSPEED`) and renders it above the list — no change needed; confirm it compiles.

- [ ] **Step 3: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`

```bash
git add frontend/app/\(app\)/data-health/page.tsx
git commit -m "feat: show connector failure detail on the data-health page"
```

---

## Task 5: Full verification pass

**Files:**
- None (verification only).

- [ ] **Step 1: Run the full backend gate**

Run: `cd backend && ./gradlew test spotlessCheck build`
Expected: all pass.

- [ ] **Step 2: Run the full frontend gate**

Run: `cd frontend && bun run typecheck && bun run lint && bun run test`
Expected: all pass.

- [ ] **Step 3: Commit any fixes**

If a check surfaced a fix, commit it atomically with a `fix:` message; otherwise there is nothing to commit.
