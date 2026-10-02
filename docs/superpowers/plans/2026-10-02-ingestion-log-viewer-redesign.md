# Ingestion Log Viewer Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store the full stack trace of each ingestion failure and surface it in a sidebar "Logs" page — a table whose rows expand to reveal the stack trace, with copy copying the stack trace.

**Architecture:** The backend captures `Throwable.printStackTrace` output into a new nullable `ingestion_failure.stack_trace` column and exposes it on the existing `failure` detail. The frontend replaces the header "Logs" drawer with a sidebar `/logs` page that renders a table and expands/copies the stack trace.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA, PostgreSQL 16 (Testcontainers), JUnit 5 + Mockito + AssertJ, Gradle + Spotless; Next.js 15 + React 19 + TypeScript, Tailwind, shadcn/ui, Bun, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-02-ingestion-log-viewer-redesign-design.md`

## Global Constraints

- Java 25; run with the committed Gradle wrapper (`./gradlew`). Frontend uses Bun (`bun run`).
- No new endpoint — the stack trace rides on the existing `failure` detail.
- The stack trace may contain tokens/payload fragments (accepted tradeoff — internal operator tool, append-only trusted ledger).
- Verification gates: `cd backend && ./gradlew test spotlessCheck build`; `cd frontend && bun run typecheck && bun run lint && bun run test`.
- Atomic Conventional Commits on branch `feature/ingestion-log-viewer`.

## Review Focus

1. **A failure with a very long stack trace** — stored verbatim, exposed, and rendered without truncation/crash. → Task 1 + Task 3.
2. **A run with no failure** — `failure`/`stackTrace` are null and the row shows "—", no crash. → Task 2 + Task 3.
3. **An unclassified `RuntimeException`** — the stack trace is captured (not just the class name). → Task 1.
4. **Copy with an empty stack trace** — disabled/no-op, no crash. → Task 3.
5. **A stack trace containing `#`/`$`/newlines** — survives the DB round-trip and renders in a `<pre>` exactly. → Task 1 + Task 3.

---

## Task 1: Backend — capture and store the stack trace

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__ingestion_failure_stack_trace.sql`
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/IngestionFailure.java`
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/IngestionRunService.java`
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/ConnectorRunner.java`
- Test: `backend/src/test/java/com/goldys/platform/ingestion/IngestionFailureStackTraceTest.java`

**Interfaces:**
- Consumes: `IngestionFailure`, `IngestionRunService.recordFailure` (existing callers), `ConnectorRunner`.
- Produces: `IngestionFailure.stackTrace()`; `IngestionRunService.recordFailure(runId, type, detail, stackTrace, occurredAt)`. Used by Task 2.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/ingestion/IngestionFailureStackTraceTest.java`:

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
class IngestionFailureStackTraceTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired IngestionRunService runs;
  @Autowired IngestionFailureRepository failures;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table ingestion_run, ingestion_failure, raw_record cascade");
  }

  @Test
  void recordsTheFullStackTrace() {
    UUID runId = runs.start("CTB", "ctb-revenue", null, Instant.now());
    String trace = "java.lang.RuntimeException: boom\n\tat com.example.Foo.bar(Foo.java:1)\n";
    runs.recordFailure(runId, "UNEXPECTED", "RuntimeException", trace, Instant.now());

    IngestionFailure saved =
        failures.findByIngestionRunIdOrderByOccurredAtAsc(runId).get(0);

    assertThat(saved.stackTrace()).isEqualTo(trace);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*IngestionFailureStackTraceTest'`
Expected: FAIL — `recordFailure` has no 5-arg overload / `stackTrace()` does not exist.

- [ ] **Step 3: Migration**

`backend/src/main/resources/db/migration/V14__ingestion_failure_stack_trace.sql`:

```sql
-- Capture the full stack trace of each failure so operators can diagnose a run
-- beyond the short operator-facing detail. Nullable: not every failure carries one.
ALTER TABLE ingestion_failure ADD COLUMN stack_trace text;
```

- [ ] **Step 4: Entity**

In `IngestionFailure.java`: add the field, constructor param, and accessor.

```java
  @Column(name = "stack_trace", updatable = false)
  private String stackTrace;
```

Add to the private constructor a `String stackTrace` param (assign `this.stackTrace = stackTrace;`), extend the `record(...)` factory to accept it, and add:

```java
  String stackTrace() {
    return stackTrace;
  }
```

- [ ] **Step 5: Service**

In `IngestionRunService.java`, extend `recordFailure`:

```java
  @Transactional
  void recordFailure(
      UUID ingestionRunId, String failureType, String detail, String stackTrace, Instant occurredAt) {
    IngestionRun run = require(ingestionRunId);
    failures.save(
        IngestionFailure.record(
            ingestionRunId, run.sourceSystem(), failureType, detail, stackTrace, occurredAt));
  }
```

- [ ] **Step 6: Runner captures the trace**

In `ConnectorRunner.java`, add the helper and pass it to both catch branches:

```java
import java.io.PrintWriter;
import java.io.StringWriter;

  try {
    connector.fetch(watermark, sink);
  } catch (ConnectorFetchException e) {
    runs.recordFailure(runId, e.failureType(), e.getMessage(), stackTraceOf(e), CLOCK.instant());
  } catch (RuntimeException e) {
    runs.recordFailure(runId, "UNEXPECTED", e.getClass().getName(), stackTraceOf(e), CLOCK.instant());
    runs.complete(runId, watermark, CLOCK.instant());
    throw e;
  }

  private static String stackTraceOf(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*IngestionFailureStackTraceTest'`
Expected: PASS.

- [ ] **Step 8: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/main/resources/db/migration/V14__ingestion_failure_stack_trace.sql backend/src/main/java/com/goldys/platform/ingestion/IngestionFailure.java backend/src/main/java/com/goldys/platform/ingestion/IngestionRunService.java backend/src/main/java/com/goldys/platform/ingestion/ConnectorRunner.java backend/src/test/java/com/goldys/platform/ingestion/IngestionFailureStackTraceTest.java
git commit -m "feat: store the full stack trace of ingestion failures"
```

---

## Task 2: Backend — expose the stack trace on the failure detail

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/FailureDetail.java`
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/IngestionService.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ConnectorStatusControllerTest.java`

**Interfaces:**
- Consumes: `IngestionFailure.stackTrace()` (Task 1).
- Produces: `FailureDetail(..., String stackTrace)`; `FailureDetailDto(..., String stackTrace)`.

- [ ] **Step 1: Write the failing test**

In `ConnectorStatusControllerTest.java`, add:

```java
  @Test
  void connectorsCarryTheStackTrace() throws Exception {
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
                    new FailureDetail(
                        "AUTH_FAILED",
                        "OAuth token rejected",
                        Instant.parse("2026-10-02T12:00:05Z"),
                        "java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.java:1)"))));

    mvc.perform(get("/api/connectors").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[1].failure.stackTrace").value("java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.java:1)"));
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*ConnectorStatusControllerTest'`
Expected: FAIL — `FailureDetail` has no 4th field / `FailureDetailDto` has no `stackTrace`.

- [ ] **Step 3: `FailureDetail`**

Change `FailureDetail.java` to:

```java
public record FailureDetail(String type, String message, Instant at, String stackTrace) {
  public FailureDetail {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(at, "at");
  }
}
```

- [ ] **Step 4: `IngestionService.latestFailure`**

```java
    IngestionFailure latest = runFailures.get(runFailures.size() - 1);
    return new FailureDetail(
        latest.failureType(), latest.detail(), latest.occurredAt(), latest.stackTrace());
```

- [ ] **Step 5: DTO**

In `ConnectorStatusController.java`, update `failure(...)` and `FailureDetailDto`:

```java
  private static FailureDetailDto failure(FailureDetail f) {
    return f == null
        ? null
        : new FailureDetailDto(f.type(), f.message(), f.at().toString(), f.stackTrace());
  }

  record FailureDetailDto(String type, String message, String at, String stackTrace) {}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ConnectorStatusControllerTest'`
Expected: PASS.

- [ ] **Step 7: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/main/java/com/goldys/platform/ingestion/FailureDetail.java backend/src/main/java/com/goldys/platform/ingestion/IngestionService.java backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java backend/src/test/java/com/goldys/platform/api/ConnectorStatusControllerTest.java
git commit -m "feat: expose the failure stack trace on the connector API"
```

---

## Task 3: Frontend — sidebar Logs page (table + drill-down + copy)

**Files:**
- Modify: `frontend/lib/api/types.ts`
- Create: `frontend/app/(app)/logs/page.tsx`
- Modify: `frontend/components/app-shell/app-sidebar.tsx`
- Modify: `frontend/components/app-shell/app-header.tsx`
- Delete: `frontend/components/logs/logs-drawer.tsx`
- Delete: `frontend/components/logs/logs-drawer.test.tsx`
- Delete: `frontend/components/logs/log-line.ts`
- Delete: `frontend/components/logs/log-line.test.ts`
- Test: `frontend/app/(app)/logs/logs-page.test.tsx`

**Interfaces:**
- Consumes: `ConnectorStatus.failure.stackTrace` (Task 2), `useApiData`, `ConnectorStatusBadge`, sidebar `navGroups`.
- Produces: `/logs` page.

- [ ] **Step 1: Write the failing test**

`frontend/app/(app)/logs/logs-page.test.tsx`:

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import LogsPage from "./page";
import type { ConnectorStatus } from "@/lib/api";

const failed: ConnectorStatus = {
  source: "CTB",
  connectorName: "ctb-revenue",
  lastRunAt: "2026-10-02T12:00:00Z",
  status: "failed",
  failureCount: 1,
  failure: {
    type: "AUTH_FAILED",
    message: "CTB login failed",
    at: "2026-10-02T12:00:05Z",
    stackTrace: "java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.java:1)",
  },
};

vi.mock("@/lib/use-api-data", () => ({
  useApiData: () => ({ data: [failed], loading: false, error: null, reload: async () => {} }),
}));

describe("LogsPage", () => {
  it("renders a table row and reveals the stack trace when expanded", () => {
    render(<LogsPage />);
    expect(screen.getByText("CTB")).toBeInTheDocument();
    // stack trace hidden until the row is clicked
    expect(screen.queryByText(/RuntimeException: boom/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByText("CTB"));
    expect(screen.getByText(/RuntimeException: boom/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- logs-page.test.tsx`
Expected: FAIL — `/logs/page.tsx` does not exist.

- [ ] **Step 3: Type**

In `types.ts`, add `stackTrace` to the failure shape:

```ts
  failure?: { type: string; message: string | null; at: string; stackTrace?: string | null } | null;
```

- [ ] **Step 4: The page**

`frontend/app/(app)/logs/page.tsx`:

```tsx
"use client";

import { useState } from "react";
import { Copy, ChevronDown } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { Button } from "@/components/ui/button";
import { ConnectorStatusBadge } from "@/components/connectors/connector-status-badge";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import type { ConnectorStatus } from "@/lib/api";

function stackTraceOf(c: ConnectorStatus): string {
  return c.failure?.stackTrace ?? "";
}

async function copy(text: string) {
  await navigator.clipboard.writeText(text);
}

export default function LogsPage() {
  const connectors = useApiData((api) => api.listConnectorStatuses());
  const [expanded, setExpanded] = useState<string | null>(null);

  if (connectors.loading) return <LoadingState rows={4} />;
  if (connectors.error) {
    return (
      <ErrorState
        title="Couldn't load logs"
        message={connectors.error.message}
        onRetry={connectors.reload}
      />
    );
  }
  const rows = connectors.data ?? [];
  const allText = rows.map(stackTraceOf).filter(Boolean).join("\n\n");

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Logs</h1>
          <p className="text-sm text-muted-foreground">
            Connector runs and the full failure detail behind each.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => copy(allText)} disabled={!allText}>
          <Copy className="mr-1.5 h-4 w-4" aria-hidden="true" />
          Copy all
        </Button>
      </div>

      <div className="rounded-lg border">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              <th className="px-4 py-2 font-medium">Source</th>
              <th className="px-4 py-2 font-medium">Connector</th>
              <th className="px-4 py-2 font-medium">Status</th>
              <th className="px-4 py-2 font-medium">Last run</th>
              <th className="px-4 py-2 font-medium">Failure</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {rows.map((c) => {
              const isOpen = expanded === c.source;
              return (
                <FragmentRow
                  key={c.source}
                  connector={c}
                  isOpen={isOpen}
                  onToggle={() => setExpanded(isOpen ? null : c.source)}
                />
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function FragmentRow({
  connector: c,
  isOpen,
  onToggle,
}: {
  connector: ConnectorStatus;
  isOpen: boolean;
  onToggle: () => void;
}) {
  return (
    <>
      <tr
        className="cursor-pointer border-b hover:bg-muted/40"
        onClick={onToggle}
        data-testid={`row-${c.source}`}
      >
        <td className="px-4 py-2 font-medium">{c.source}</td>
        <td className="px-4 py-2 text-muted-foreground">{c.connectorName}</td>
        <td className="px-4 py-2">
          <ConnectorStatusBadge status={c.status} />
        </td>
        <td className="px-4 py-2 text-muted-foreground">
          {c.lastRunAt ? new Date(c.lastRunAt).toLocaleString() : "—"}
        </td>
        <td className="px-4 py-2 text-destructive">
          {c.failure ? `${c.failure.type}: ${c.failure.message ?? ""}` : "—"}
        </td>
        <td className="px-4 py-2 text-right">
          <Button
            variant="ghost"
            size="sm"
            aria-label={`Copy ${c.source} log`}
            disabled={!stackTraceOf(c)}
            onClick={(e) => {
              e.stopPropagation();
              copy(stackTraceOf(c));
            }}
          >
            <Copy className="h-4 w-4" aria-hidden="true" />
          </Button>
        </td>
      </tr>
      {isOpen && c.failure?.stackTrace ? (
        <tr className="border-b bg-muted/20">
          <td colSpan={6} className="px-4 py-3">
            <pre className="whitespace-pre-wrap text-xs text-muted-foreground">
              {c.failure.stackTrace}
            </pre>
          </td>
        </tr>
      ) : null}
    </>
  );
}
```

- [ ] **Step 5: Sidebar nav item**

In `app-sidebar.tsx`, import `ScrollText` from lucide-react and add the item after "Data health":

```tsx
      { title: "Logs", href: "/logs", icon: ScrollText },
```

- [ ] **Step 6: Remove the header drawer**

In `app-header.tsx`, remove the `LogsDrawer` import and its usage, keeping only `AskGoldysDrawer`. Delete `frontend/components/logs/logs-drawer.tsx`, `logs-drawer.test.tsx`, `log-line.ts`, `log-line.test.ts`.

- [ ] **Step 7: Run test to verify it passes**

Run: `cd frontend && bun run test -- logs-page.test.tsx`
Expected: PASS.

- [ ] **Step 8: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`

```bash
git add frontend/lib/api/types.ts "frontend/app/(app)/logs/" frontend/components/app-shell/app-sidebar.tsx frontend/components/app-shell/app-header.tsx
git rm frontend/components/logs/logs-drawer.tsx frontend/components/logs/logs-drawer.test.tsx frontend/components/logs/log-line.ts frontend/components/logs/log-line.test.ts
git commit -m "feat: move logs to a sidebar table with stack-trace drill-down"
```

---

## Task 4: Full verification pass

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
