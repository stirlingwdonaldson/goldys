# Low-Hanging-Fruit Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land three small, independent, behaviour-preserving cleanups: a stale doc line, the dead OIDC references flagged in `AGENTS.md`, and a 12-way duplicated null-coalesce helper.

**Architecture:** No new behaviour. Task 1 is a one-line doc correction. Task 2 deletes a dead file, drops two dead Next.js rewrites, simplifies one redirect check, and rewords "no OIDC session" javadoc. Task 3 extracts the identical `nz(...)` private helpers scattered across connectors/canonical/reconciliation into one leaf utility class and replaces call sites.

**Tech Stack:** Java 21 + Spring Boot (backend, Gradle wrapper pinned to 9.5.0); Next.js + TypeScript (frontend, Bun). No new dependencies.

**Spec:** `AGENTS.md` → "Gotchas" (the dead-OIDC line) and "Build and test"; `docs/architecture/current-state.md` (auth is email+password on `user_account`); `docs/connectors/source-access.md` (the doc being corrected). There is no formal spec — this plan is the spec for the cleanup.

## Branch strategy (read first)

`main` tip is `28c347d` (PR #194, `chore/agent-harness-cleanup`, merged). The current checkout is on `fix/playwright-browser-channel` (open PR #195) whose only delta vs main is `opencode.jsonc`. **Do not do this work on that branch.** Create an isolated worktree off `origin/main` via the `superpowers:using-git-worktrees` skill, then one task-specific branch.

Conflict check already done: every file these tasks touch is byte-identical between `origin/main` and the current branch (verified with `git diff --stat origin/main HEAD -- <files>` → empty), and PR #195's only change is `opencode.jsonc`, which none of these tasks touch. Rebase/merge onto `origin/main` is clean.

## Global Constraints

- Never commit to main; each task is its own commit on a task branch.
- Backend build/test uses the committed Gradle wrapper only (`./gradlew`, pinned 9.5.0). Never touch a migration; none are needed here.
- Frontend uses Bun only (`bun run …`); no npm/yarn lockfiles.
- `hibernate.ddl-auto: validate` — no schema change, so no Flyway migration.
- Behaviour-preserving: these are pure cleanups, so existing tests are the gate; do not change any resolved value, metric, or auth flow.
- Architecture: `ArchitectureBoundariesTest` enforces package rules. The new `util` package is a leaf (depends only on `java.math`/`java.lang`) and appears in no denylist, so it needs no rule change — but run the suite to confirm.

## Review Focus

1. **Redirect detection survives OIDC removal** — after Task 2, a `fetchApi("/api/…")` that gets redirected to `/login` must still reject with `AUTH_REQUIRED` (not `UNEXPECTED_REDIRECT`). Pinned by the remaining rows in `client.test.ts`'s `diagnoses followed redirect to %s`.
2. **`nz` fallbacks are preserved exactly** — the two String defaults are `"Unspecified"` (PaymentProjector) and `"Uncategorised"` (SaleItemProjector); the BigDecimal default is `BigDecimal.ZERO`; the Integer default is `0`. Any typo here changes reconciled data. Pinned by `NullsTest` + the existing `*IngestServiceTest`/`CtbStocktakeParserTest`/`CanonicalPaymentServiceTest` suites.
3. **Doc fix matches the real canonicalisation state** — stock/wastage are canonicalised (runner `StockWastageCanonicalizationRunner`, flag `app.stock-wastage-canonicalize=true`) but have no resolved projection yet; the sentence must not claim more than that.
4. **No stray OIDC reference remains in live code** — after Task 2, `grep -rniE 'oidc|oauth2|openid'` over `backend/src frontend` must return nothing (dated `docs/superpowers/specs/*` are historical records and stay untouched).

---

## Task 1: Fix stale stock/wastage doc line

**Files:**
- Modify: `docs/connectors/source-access.md:147-148`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing (doc-only).

- [ ] **Step 1: Read the current sentence and replace it**

Current text (lines 147–148, inside the "Line detail (raw-only backfill)" section):

```
(`app.recipe-ingredients-canonicalize=true`). Stocktake and wastage detail remain raw-only (slices
B–C).
```

Replace the second sentence with:

```
(`app.recipe-ingredients-canonicalize=true`). Stocktake and wastage detail are canonicalised by a
flag-gated runner (`app.stock-wastage-canonicalize=true`, `StockWastageCanonicalizationRunner`),
replayed from the existing raw ledger; they have no resolved projection yet (slices B–C).
```

- [ ] **Step 2: Verify no other "raw-only" claim about stock/wastage remains**

Run: `grep -n "raw-only" docs/connectors/source-access.md`
Expected: the remaining "raw-only" matches refer to datasets that are genuinely still raw-only (the deprecated `POST /api/ingest/opentable` line and the section heading); none claim stocktake/wastage detail is raw-only.

- [ ] **Step 3: Commit**

```bash
git add docs/connectors/source-access.md
git commit -m "docs(connectors): stock/wastage detail are canonicalised, not raw-only"
```

---

## Task 2: Remove dead OIDC references

Auth is email + password on `user_account`; OIDC was removed by `V7`/`V8`. `AGENTS.md` names the leftovers: `.env.oidc.example`, the `/oauth2` rewrites in `frontend/next.config.ts`, and "some comments".

**Files:**
- Delete: `.env.oidc.example`
- Modify: `frontend/next.config.ts:17-27`
- Modify: `frontend/lib/api/response.ts:49-51` (the `isAuthRedirect` condition)
- Modify: `frontend/lib/api/client.test.ts:163-168` (drop two fixture rows)
- Modify: 7 Java controllers' javadoc (see Step 4 list)
- Modify: `docs/operations/deployment.md:110-113` (the "leftovers" note)

**Interfaces:**
- Consumes: nothing.
- Produces: nothing. (`isAuthRedirect` remains `private`; its only caller is `readApiResponse` in the same file.)

- [ ] **Step 1: Delete `.env.oidc.example`**

Run: `git rm .env.oidc.example`
Expected: file removed from the index.

- [ ] **Step 2: Remove the OIDC rewrites from `frontend/next.config.ts`**

Replace the comment + rewrites block (lines 17–27) so only the `/api` rewrite remains:

```ts
  // Proxy API calls to the Spring Boot backend so the browser's same-origin
  // session cookies reach it. Defaults to local dev; set BACKEND_ORIGIN when
  // frontend and backend aren't co-located behind one origin.
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backendOrigin}/api/:path*` },
    ];
  },
```

- [ ] **Step 3: Simplify `isAuthRedirect` in `frontend/lib/api/response.ts`**

Replace the return condition (line 50) so only `/login` counts as an auth redirect:

```ts
    return original.pathname.startsWith("/api/") && original.origin === target.origin &&
      target.pathname === "/login";
```

- [ ] **Step 4: Re-word the "no OIDC session" javadoc in 7 controllers**

Change every occurrence of `no OIDC session` to `no user session` in:

- `backend/src/main/java/com/goldys/platform/api/OpenTableReservationsCsvIngestController.java` (~line 20)
- `backend/src/main/java/com/goldys/platform/api/OpenTableReviewsCsvIngestController.java` (~line 21)
- `backend/src/main/java/com/goldys/platform/api/OpenTableMenuItemsCsvIngestController.java` (~line 21)
- `backend/src/main/java/com/goldys/platform/api/CtInvoiceIngestController.java` (~line 18)
- `backend/src/main/java/com/goldys/platform/api/LightspeedIngestController.java` (~line 26)
- `backend/src/main/java/com/goldys/platform/api/DeputyIngestController.java` (~line 23)
- `backend/src/main/java/com/goldys/platform/api/OpenTableCsvIngestController.java` (~line 20)

- [ ] **Step 5: Update the OIDC "leftovers" note in `docs/operations/deployment.md`**

Replace the blockquote (lines 110–113):

```
> `.env.oidc.example`, the `/oauth2` rewrites in `frontend/next.config.ts`, and some
> code comments still mention OIDC. They are leftovers from the original OIDC
> design (replaced by `V7`/`V8`) and are not used.
```

with:

```
> OIDC was replaced by the email + password flow (`V7`/`V8`); no OIDC references remain in code or config.
```

- [ ] **Step 6: Remove the two OIDC redirect fixture rows from `frontend/lib/api/client.test.ts`**

Delete these two rows (lines 165–166) from the `it.each` table:

```ts
    ["http://localhost:3000/oauth2/authorization/fixture", "AUTH_REQUIRED"],
    ["http://localhost:3000/login/oauth2/code/fixture", "AUTH_REQUIRED"],
```

The table keeps `/login`, `/unexpected`, and the external `/login` rows.

- [ ] **Step 7: Run the frontend gate**

Run (from `frontend/`): `bun run typecheck && bun run lint && bun run test`
Expected: all pass. `client.test.ts`'s `diagnoses followed redirect to %s` still passes with the remaining rows.

- [ ] **Step 8: Confirm no stray OIDC reference remains in live code**

Run: `grep -rniE 'oidc|oauth2|openid' backend/src frontend`
Expected: no output (dated `docs/superpowers/specs/*` are excluded and stay untouched).

- [ ] **Step 9: Run the backend gate**

Run (from `backend/`): `./gradlew test spotlessCheck`
Expected: all pass (the javadoc change is comment-only; no test asserts OIDC wording).

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "chore: remove dead OIDC references (email+password auth only)"
```

---

## Task 3: Extract shared null-coalesce helper

The `private static nz(...)` helper is copy-pasted 12 times across 11 files, in three semantics:

- `nz(BigDecimal)` → `v == null ? BigDecimal.ZERO : v` (9 identical copies)
- `nz(Integer)` → `v == null ? 0 : v` (1 copy)
- `nz(String)` → `v == null || v.isBlank() ? <default> : v` (2 copies, defaults `"Unspecified"` and `"Uncategorised"`)

`CanonicalPaymentService.nz(String)` (`v == null ? "" : v`) is intentionally left alone: it neither blank-checks nor takes a fallback, so a shared helper would carry a parameter used once (YAGNI).

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/util/Nulls.java`
- Test: `backend/src/test/java/com/goldys/platform/util/NullsTest.java`
- Modify: 10 files that replace `nz(...)` with `Nulls.*` and delete their private helper (list in Step 3)

**Interfaces:**
- Produces: `com.goldys.platform.util.Nulls` with:
  - `public static BigDecimal zeroIfNull(BigDecimal v)`
  - `public static int zeroIfNull(Integer v)`
  - `public static String valueIfBlank(String v, String fallback)`

- [ ] **Step 1: Write the failing test `NullsTest.java`**

```java
package com.goldys.platform.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NullsTest {
  @Test
  void zeroIfNullBigDecimalReturnsZeroForNullAndValueOtherwise() {
    assertThat(Nulls.zeroIfNull((BigDecimal) null)).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(Nulls.zeroIfNull(new BigDecimal("1.5"))).isEqualByComparingTo("1.5");
  }

  @Test
  void zeroIfNullIntegerReturnsZeroForNullAndValueOtherwise() {
    assertThat(Nulls.zeroIfNull((Integer) null)).isZero();
    assertThat(Nulls.zeroIfNull(7)).isEqualTo(7);
  }

  @Test
  void valueIfBlankReturnsFallbackForNullBlankAndValueOtherwise() {
    assertThat(Nulls.valueIfBlank(null, "Unspecified")).isEqualTo("Unspecified");
    assertThat(Nulls.valueIfBlank("  ", "Unspecified")).isEqualTo("Unspecified");
    assertThat(Nulls.valueIfBlank("Cash", "Unspecified")).isEqualTo("Cash");
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `backend/`): `./gradlew test --tests 'com.goldys.platform.util.NullsTest'`
Expected: compile error — `Nulls` does not exist.

- [ ] **Step 3: Create `Nulls.java` and replace call sites**

Create `backend/src/main/java/com/goldys/platform/util/Nulls.java`:

```java
package com.goldys.platform.util;

import java.math.BigDecimal;

/** Null-coalescing helpers shared across connectors, canonical and reconciliation code. */
public final class Nulls {
  private Nulls() {}

  public static BigDecimal zeroIfNull(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  public static int zeroIfNull(Integer v) {
    return v == null ? 0 : v;
  }

  public static String valueIfBlank(String v, String fallback) {
    return v == null || v.isBlank() ? fallback : v;
  }
}
```

Replace the private `nz(...)` and its call sites (add `import static com.goldys.platform.util.Nulls.*;` or a qualified `Nulls.` reference) in exactly these files, then delete each private helper:

| File | Replacement | Fallback (String only) |
|---|---|---|
| `connectors/ctb/CtbConnector.java` | `nz(BigDecimal)` → `Nulls.zeroIfNull` | — |
| `connectors/ctb/CtbStocktakeParser.java` | `nz(BigDecimal)` → `Nulls.zeroIfNull` | — |
| `connectors/lightspeed/LightspeedAllSalesIngestService.java` | `nz(BigDecimal)` → `Nulls.zeroIfNull` | — |
| `connectors/lightspeed/LightspeedDeletedSaleIngestService.java` | `nz(BigDecimal)` → `Nulls.zeroIfNull` | — |
| `connectors/lightspeed/LightspeedIngestService.java` | `nz(BigDecimal)` → `Nulls.zeroIfNull` | — |
| `connectors/lightspeed/LightspeedPaymentIngestService.java` | `nz(BigDecimal)`→`zeroIfNull`, `nz(Integer)`→`zeroIfNull` | — |
| `connectors/lightspeed/LightspeedSaleItemIngestService.java` | `nz(BigDecimal)` → `Nulls.zeroIfNull` | — |
| `reconciliation/DeletedSaleProjector.java` | `nz(BigDecimal)` → `Nulls.zeroIfNull` | — |
| `reconciliation/PaymentProjector.java` | `nz(BigDecimal)`→`zeroIfNull`, `nz(String)`→`valueIfBlank` | `"Unspecified"` |
| `reconciliation/SaleItemProjector.java` | `nz(String)` → `Nulls.valueIfBlank` | `"Uncategorised"` |

Leave `canonical/CanonicalPaymentService.java` unchanged.

- [ ] **Step 4: Run the targeted tests to verify they pass**

Run (from `backend/`): `./gradlew test --tests 'com.goldys.platform.util.NullsTest' --tests 'com.goldys.platform.connectors.lightspeed.*' --tests 'com.goldys.platform.connectors.ctb.CtbStocktakeParserTest' --tests 'com.goldys.platform.canonical.CanonicalPaymentServiceTest'`
Expected: all pass; `NullsTest` now passes.

- [ ] **Step 5: Run the full backend gate**

Run (from `backend/`): `./gradlew test spotlessCheck`
Expected: all pass, including `ArchitectureBoundariesTest` (the `util` package is a leaf; no rule change expected).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/util/Nulls.java backend/src/test/java/com/goldys/platform/util/NullsTest.java
git add backend/src/main/java/com/goldys/platform/connectors backend/src/main/java/com/goldys/platform/reconciliation
git commit -m "refactor: extract shared Nulls helper (dedup nz)"
```

---

## Out of scope (noted for later, not in this plan)

- `SalesMetricExecutor`/`DerivedMetricExecutor` `TODO(provenance)` markers — need provenance-plumbing context first.
- Wastage parser duplicate-collapse symmetric test — separate, needs its own investigation.
- Larger clone groups (`PaymentMetricExecutor`≈`SaleItemMetricExecutor`, `JevProductClassifier.post`≈`JevReviewClassifier.post`, `DataExplorerQueryImpl` row-mappers) — each is a real refactor with its own test surface.
- The `docs/superpowers/specs/*` OIDC mentions — dated historical design records, intentionally left as-is.
