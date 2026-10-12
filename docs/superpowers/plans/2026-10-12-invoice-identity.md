# Invoice Identity — Supplier-Scoped Headers & Reorder-Stable Lines (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the two identity choices in the invoice canonical layer that are wrong for real CTB data — (1) invoice headers key on `invoiceNumber` alone, so a number shared across suppliers cross-supersedes; (2) invoice lines key on CSV position (`seq`), so a reordered re-export cross-wires lines and a short re-export leaves stale tail lines. Result: supplier-scoped header identity, content-stable line identity, and correct supersession of disappearing lines.

**Spec:** `docs/superpowers/specs/2026-10-12-invoice-identity-design.md` (source of truth for the formulas; this plan sequences and details the code).

**Tech Stack:** Java 25, Spring Boot 3.5, JUnit 5 + AssertJ + Mockito, Gradle (`backend/gradlew`). Google-Java-Format via `spotlessApply`.

## Global Constraints

- **Bitemporal supersede-not-edit everywhere.** The re-key is a *runner* that closes old rows and inserts new rows carrying the same facts — never `UPDATE`/`DELETE` (V2's `raw_record_append_only` trigger is the model; canonical rows follow the same invariant).
- **No Flyway DML migration.** Identity changes are *value* changes (logical id and `source_record_ref`), not schema changes. The only data change is the one-off re-key runner.
- **Supplier normalisation is conservative and shared.** One pure function, applied identically to the CSV `Supplier` column and (later) the AJAX `supplier`. Never fuzz-match.
- **Never delete, never guess on disappearing lines.** Absent lines are retained + flagged unless the re-export is proven complete (reconciles with the header within the `0.02` ex-tax tolerance).
- **COGS is ex-tax line totals.** Header totals are never used for COGS; this plan changes identity, not the COGS driver.

## Open questions — resolve before/at the marked task

1. **Supplier-normalisation boundaries (Task 1).** The spec strips trailing legal suffixes (`PTY LTD`, `AUST`, `LTD`, …). Validate against the production supplier list (the production-data audit has it) so the stripper never merges two genuinely distinct suppliers. *Gate: list the distinct suppliers and the pairs the stripper would merge before writing the stripper.*
2. **CSV-first vs AJAX-first (Task 6, re-key).** The AJAX pull (`ctb-invoices-ajax`) carries stable source invoice ids but is raw-only today. This plan keeps AJAX raw-only and makes the CSV content-stable. If the AJAX path is canonicalised first, header identity gets a real source id and the line-identity work shrinks — decide before starting Task 6.
3. **Completeness test (Task 4).** "Re-export reconciles with header ⇒ authoritative" must be confirmed against the 2 cross-supplier and 18 repeat-group production cases. *Gate: confirm the `0.02` header-reconciliation signal separates "correction" from "short export" on those cases before relying on it.*

## Review Focus

1. **Cross-supplier isolation (F03)** — two suppliers, same number ⇒ two headers, no supersession. → Task 2.
2. **Reorder stability (F05)** — reorder ⇒ same lines, no cross-wiring. → Task 3.
3. **Short-export safety** — fewer lines that don't reconcile ⇒ absent lines stay, `INVOICE_EXPORT_INCOMPLETE` flagged. → Task 4.
4. **Re-key idempotence** — run twice ⇒ no new versions. → Task 6.
5. **Gap recovery still works after the identity change** — the joins must move to the supplier-scoped header. → Task 5.

---

### Task 1: Supplier normalisation (pure function)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/SupplierKey.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/SupplierKeyTest.java`

**Interfaces:**
- Produces: `SupplierKey.normalize(String supplier) -> String` — trim, collapse internal whitespace, uppercase, strip trailing legal-suffix noise case-insensitively (spec §1). Used by the header identity (Task 2) and later the AJAX path.

- [ ] **Step 1: Write the failing test** — assert `"ACME PTY LTD"`, `"ACME PTY. LTD."`, `"acme pty ltd"` → one value; `"ACME FOODS"` vs `"ACME BEV"` stay distinct; `null`/blank → `null` (or a sentinel).
- [ ] **Step 2: Run to see it fail.** `./gradlew test --tests com.goldys.platform.canonical.SupplierKeyTest`
- [ ] **Step 3: Implement `SupplierKey`** per spec §1 (no Soundex/levenshtein).
- [ ] **Step 4: Run to pass, then commit** `feat(invoice): conservative supplier normalisation key`.

> **Gate:** apply `SupplierKey.normalize` to the production supplier list and eyeball the resulting groups (open question 1) before proceeding.

---

### Task 2: Supplier-scoped header identity

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceService.java` (`logicalIdFor` at ~:68, `lockCurrentSourceFact` at ~:33)
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceQuery.java` / `CanonicalInvoiceRepository.java` (any `invoiceNumber`-keyed lookups)
- Test: extend `CanonicalInvoiceServiceTest` / add a cross-supplier collision test

**Interfaces:**
- `source_record_ref` becomes `normalizedSupplier + ":" + invoiceNumber` (spec §1, prefer the explicit ref).
- `logicalIdFor` becomes `UUID.nameUUIDFromBytes("invoice:" + normalizedSupplier + ":" + invoiceNumber)`.
- `lockCurrentSourceFact(sourceSystem, ref)` now locks on the supplier-scoped ref, so two suppliers with the same number can't collide.

- [ ] **Step 1: Write the failing test** — ingest `(ACME FOODS, INV-7)` then `(ACME BEV, INV-7)` ⇒ two current headers; re-ingest `(ACME FOODS, INV-7)` supersedes only the ACME FOODS one.
- [ ] **Step 2: Run to see it fail.**
- [ ] **Step 3: Implement** the supplier-scoped logical id + ref. `InvoiceInput` already carries `supplierName`, so no new input field is required.
- [ ] **Step 4: Run to pass, then commit** `feat(invoice): supplier-scoped header identity (F03)`.

---

### Task 3: Content-stable line identity

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java` (line ref at ~:109, `invoice.invoiceNumber() + ":" + seq`)
- Create: a small line-ref helper (e.g. `InvoiceLineRef.of(headerRef, stockCode, description, qty, unitCost, lineTotal, occurrence)`) — or fold into the ingest service if it stays tiny
- Test: extend `CtInvoiceCsvIngestServiceTest`

**Interfaces:**
- Replace `invoiceNumber + ":" + seq` with `source_record_ref = headerRef + ":" + sha256(normalized content) + ":" + occurrenceIndex` (spec §2). `normalized content` = trimmed, case-normalised `StockCode`, `StockDescription`, `LineQuantity`, `LineUnitCostExTax`, `LineTotalExTax`; `occurrenceIndex` disambiguates identical lines in document order.
- The line logical id (in `CanonicalInvoiceLineService.logicalIdFor`, `"invoice-line:" + invoiceNumber + ":" + sourceRecordRef`) must use the supplier-scoped header too.

- [ ] **Step 1: Write the failing test** — ingest N lines, re-ingest the same N reordered ⇒ same N current lines, no cross-wired supersession; N identical lines survive as N distinct refs.
- [ ] **Step 2: Run to see it fail.**
- [ ] **Step 3: Implement** the content-stable ref + update the line logical id.
- [ ] **Step 4: Run to pass, then commit** `feat(invoice): reorder-stable line identity (F05)`.

---

### Task 4: Disappearing lines — supersede only when complete

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java` (`convergePdfLinesAfterCsvIngest` at ~:126)
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineService.java` (`prunePdfOriginLines`, `findCurrentPdfOriginByInvoiceNumber` at ~:94, :120)
- Test: extend the ingest-service and line-service tests

**Interfaces:**
- When a re-export's line set is a strict subset of current: if the new line total reconciles with the header (within `0.02`) ⇒ authoritative, supersede absent current lines; otherwise ⇒ retain absent lines and flag `INVOICE_EXPORT_INCOMPLETE` naming the absent lines + shortfall (spec §3).

- [ ] **Step 1: Write the failing test** — short export (doesn't reconcile) ⇒ absent lines stay current + flag; complete corrected export (reconciles) ⇒ absent lines superseded.
- [ ] **Step 2: Run to see it fail.**
- [ ] **Step 3: Implement** per spec §3.
- [ ] **Step 4: Run to pass, then commit** `feat(invoice): supersede disappearing lines only when the export is complete`.

> **Gate:** validate against the production repeat-group / short-export cases (open question 3).

---

### Task 5: Move gap recovery & convergence joins to the supplier-scoped header

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoiceGapRecoveryService.java` (joins on `invoiceNumber` at ~:65, :80, :142; gap-line identity at ~:28)
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceQuery.java` / `CanonicalInvoiceLineService.java` (PDF-origin queries keyed by invoice number)

**Interfaces:**
- Every place that joins on `invoiceNumber` alone must join on the supplier-scoped header instead; the PDF gap-line identity gains the supplier scope (`headerRef + ":pdf:" + sha256 + ":" + seq`).

- [ ] **Step 1: Write the failing test** — gap recovery on two same-number invoices under different suppliers recovers into the correct header, not the other.
- [ ] **Step 2: Run to see it fail.**
- [ ] **Step 3: Implement** the supplier-scoped joins.
- [ ] **Step 4: Run to pass, then commit** `fix(invoice): scope gap recovery and convergence to the supplier`.

---

### Task 6: One-off re-key runner + projection recompute

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoiceIdentityRekeyRunner.java` (an `ApplicationRunner`, `@Order(HIGHEST_PRECEDENCE)`, gated on `app.invoice-identity-rekey=true`)
- Modify: `docker-compose.prod.yml` (wire `APP_INVOICE_IDENTITY_REKEY`), `.env.example`
- Test: `InvoiceIdentityRekeyRunnerTest` + an idempotence test

**Interfaces:**
- For each current header + line, compute the new supplier-scoped / content-stable refs; supersede the old row and insert the new row carrying the same fact (spec §4). Idempotent: a re-run recognises already-re-keyed refs and creates no new versions.
- After re-keying, trigger the inventory COGS projection recompute (the existing `recomputeAll` / startup-rebuild path) so the resolved layer reflects the new line set (spec §5).

- [ ] **Step 1: Write the failing test** — a current header with ordinal-ref lines re-keys to supplier-scoped + content-stable refs; a second run is a no-op.
- [ ] **Step 2: Run to see it fail.**
- [ ] **Step 3: Implement** the runner + compose wiring.
- [ ] **Step 4: Run to pass, then commit** `feat(invoice): one-off identity re-key runner + projection recompute`.

> **Rollout gate (production):** back up / confirm current COGS figures, run with `APP_INVOICE_IDENTITY_REKEY=true` on a read-only-verified DB, diff resolved COGS before/after, then unset. Never run the re-key twice without re-verifying idempotence.

---

### Task 7: Documentation

**Files:**
- Modify: `docs/connectors/matching-and-identity.md` (the `canonical_invoice` and `canonical_invoice_line` sections)

- [ ] Update the header section (supplier-scoped logical id + ref, normalisation rules) and the line section (content-stable ref, occurrence disambiguation, disappear-vs-short-export rule), and note the AJAX stable id as the preferred future source id (spec §6).

---

## Deferred / out of scope

- **AJAX path canonicalisation** — would give headers a genuine source id; keep raw-only here (open question 2).
- **`supplier_name_key` / structured columns** — unrelated to identity; a separate hardening slice.
- **Confidence-scored matching + review queue + alias overrides** — a later phase.

## Test plan (cross-cutting)

- Cross-supplier collision (F03), supplier normalisation, reordered re-export (F05), short export (gap), complete corrected export, duplicate identical lines, re-key idempotence, projection recompute, PDF gap-recovery regression — one assertion per test, TDD per task, full `./gradlew test spotlessCheck` before finishing.
