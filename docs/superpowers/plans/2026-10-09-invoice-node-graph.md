# Invoice Node Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a date-range-scoped, 3-column progressive-drill node graph (suppliers → invoices → line items) to the Kitchen screen, reusing the PR #69 `@xyflow/react` flow canvas.

**Architecture:** Three lazy read endpoints serve ranked per-level data through the existing inventory layering (`api` → `application` → `semantic` interface → `reconciliation` impl → `canonical` facades). A pure frontend builder (`buildInvoiceGraph`) applies the top-N + "Other" rollup heuristic, and an additive `drill`/`onDrill` hook on `FlowCanvas` drives progressive disclosure.

**Tech Stack:** Java + Spring Boot (backend, Gradle, JUnit 5 / AssertJ / Mockito / ArchUnit), Next.js 15 + React 19 + TypeScript (frontend, bun, Vitest + Testing Library), `@xyflow/react` 12.

**Spec:** `docs/superpowers/specs/2026-10-09-invoice-node-graph-design.md`

## Global Constraints

- Reuse `@xyflow/react` through the existing `ColumnGraph`/`FlowCanvas`/`layoutColumns`/`FlowTone` abstraction — no new graph library.
- Supplier identity is the **normalized name**; orphaned lines (no header) bucket to the sentinel name `"Unknown"`.
- Top-N constants: suppliers 8, invoices 8, lines 10 (defined once in the builder, exported).
- Layering is archunit-enforced: `..api..` must not depend on `..canonical..`; the `InvoiceGraphQuery` interface lives in `..semantic..` (a leaf) and is implemented only in `..reconciliation..`.
- Every endpoint authorizes `inventory.cost` READ (same `ResourceKey` as `InventoryReportingService`).
- Backend reads are in-memory from the existing `findAllCurrent()` facades — no new repository queries, no new migrations.
- Line-item `category` is out of scope (not yet populated by any ingest path). Line-item nodes are leaves (no `drill`, no `href`).
- No supplier/product masters, no fuzzy matching, no POS/recipe linkage, no write-back.

## Review Focus

These are the input classes a reasonable person would most likely hit, ordered by risk. Each is pinned by a test in the owning task named below.

1. **Empty range / no invoices** — the graph must render a single `missing` "No invoices in range" node, never a blank canvas. → Task 7.
2. **Lines whose invoice has no header** — must surface as a `warn` "Unknown supplier" node that is never folded into "Other" and is never drillable. → Task 2 (service) + Task 7 (builder).
3. **A supplier with invoices but zero lines** — `totalSpend` must fall back to the sum of header `totalAmount`, not show a fabricated zero or disappear. → Task 2.
4. **Duplicate `productNameKey` lines on one invoice** — must be deduped and summed before ranking so a split line can't double-count or crowd out real lines. → Task 7.
5. **A rollup of exactly one item** — must promote the single item to a normal node rather than render an "Other 1" rollup. → Task 7.

---

### Task 1: Expose invoice metadata from the canonical layer

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/InvoiceMetadataView.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalInvoiceQueryTest.java`

**Interfaces:**
- Consumes: `CanonicalInvoiceRepository.findAllCurrent()` (package-private, existing).
- Produces: `public List<InvoiceMetadataView> currentInvoices()` on `CanonicalInvoiceQuery`, and the record `InvoiceMetadataView(String supplierName, String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount, String purchaseNumber, String pdfFilename)` — consumed by Task 2.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/goldys/platform/canonical/CanonicalInvoiceQueryTest.java`:

```java
package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalInvoiceQueryTest {

  private final CanonicalInvoiceRepository repository = mock(CanonicalInvoiceRepository.class);
  private final CanonicalInvoiceQuery query = new CanonicalInvoiceQuery(repository);

  @Test
  void mapsCurrentInvoicesToMetadataViews() {
    CanonicalInvoice inv = mock(CanonicalInvoice.class);
    when(inv.supplierName()).thenReturn("A. Foods");
    when(inv.invoiceNumber()).thenReturn("INV-1");
    when(inv.invoiceDate()).thenReturn(LocalDate.of(2026, 9, 1));
    when(inv.totalAmount()).thenReturn(new BigDecimal("1420.15"));
    when(inv.purchaseNumber()).thenReturn("PO-88");
    when(inv.pdfFilename()).thenReturn("inv-1.pdf");
    when(repository.findAllCurrent()).thenReturn(List.of(inv));

    List<InvoiceMetadataView> result = query.currentInvoices();

    assertThat(result).hasSize(1);
    InvoiceMetadataView v = result.get(0);
    assertThat(v.supplierName()).isEqualTo("A. Foods");
    assertThat(v.invoiceNumber()).isEqualTo("INV-1");
    assertThat(v.invoiceDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(v.totalAmount()).isEqualByComparingTo("1420.15");
    assertThat(v.purchaseNumber()).isEqualTo("PO-88");
    assertThat(v.pdfFilename()).isEqualTo("inv-1.pdf");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.canonical.CanonicalInvoiceQueryTest"`
Expected: FAIL — `currentInvoices()` does not exist / `InvoiceMetadataView` unresolved.

- [ ] **Step 3: Write the minimal implementation**

Create `backend/src/main/java/com/goldys/platform/canonical/InvoiceMetadataView.java`:

```java
package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A read-only view of one current invoice's metadata, for modules outside this package. */
public record InvoiceMetadataView(
    String supplierName,
    String invoiceNumber,
    LocalDate invoiceDate,
    BigDecimal totalAmount,
    String purchaseNumber,
    String pdfFilename) {}
```

Modify `CanonicalInvoiceQuery.java` — add `import java.util.List;` to the imports, then these two members inside the class:

```java
  /** All current invoice metadata, so modules outside this package can read invoices. */
  public List<InvoiceMetadataView> currentInvoices() {
    return repository.findAllCurrent().stream().map(CanonicalInvoiceQuery::toView).toList();
  }

  private static InvoiceMetadataView toView(CanonicalInvoice i) {
    return new InvoiceMetadataView(
        i.supplierName(), i.invoiceNumber(), i.invoiceDate(), i.totalAmount(),
        i.purchaseNumber(), i.pdfFilename());
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.canonical.CanonicalInvoiceQueryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/InvoiceMetadataView.java \
        backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceQuery.java \
        backend/src/test/java/com/goldys/platform/canonical/CanonicalInvoiceQueryTest.java
git commit -m "feat(invoice): expose invoice metadata view for the graph query"
```

---

### Task 2: Semantic interface + reconciliation implementation

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/SupplierGraphNode.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/InvoiceGraphNode.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/LineGraphNode.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/InvoiceGraphQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/InvoiceGraphServiceImpl.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/InvoiceGraphServiceImplTest.java`

**Interfaces:**
- Consumes: `CanonicalInvoiceQuery.currentInvoices()` / `currentSupplierNames()` (Task 1 + existing), `CanonicalInventoryQuery.currentEnrichedLines()` (existing, returns `EnrichedInvoiceLine` with `invoiceNumber()`, `invoiceDate()`, `productNameKey()`, `stockCode()`, `quantity()`, `unitCost()`, `lineTotal()`, `uom()`).
- Produces: `InvoiceGraphQuery.suppliers(LocalDate, LocalDate)`, `.invoicesForSupplier(String, LocalDate, LocalDate)`, `.linesForInvoice(String)` — consumed by Task 3.

- [ ] **Step 1: Write the three DTO records + the interface** (these are the contract the test imports)

`backend/src/main/java/com/goldys/platform/semantic/SupplierGraphNode.java`:

```java
package com.goldys.platform.semantic;

import java.math.BigDecimal;

/** Purchases grouped by supplier for the invoice graph; `totalSpend` is null when unknown. */
public record SupplierGraphNode(String name, int invoiceCount, BigDecimal totalSpend) {}
```

`backend/src/main/java/com/goldys/platform/semantic/InvoiceGraphNode.java`:

```java
package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One invoice header for the invoice graph. */
public record InvoiceGraphNode(
    String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount,
    String purchaseNumber, String pdfFilename) {}
```

`backend/src/main/java/com/goldys/platform/semantic/LineGraphNode.java`:

```java
package com.goldys.platform.semantic;

import java.math.BigDecimal;

/** One invoice line for the invoice graph; `uom` is null when not PDF-enriched. */
public record LineGraphNode(
    String productNameKey, String stockCode, BigDecimal quantity,
    BigDecimal unitCost, BigDecimal lineTotal, String uom) {}
```

`backend/src/main/java/com/goldys/platform/semantic/InvoiceGraphQuery.java`:

```java
package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;

/** The invoice node graph's read model: ranked per-level data for supplier → invoice → line drill. */
public interface InvoiceGraphQuery {
  List<SupplierGraphNode> suppliers(LocalDate from, LocalDate to);

  List<InvoiceGraphNode> invoicesForSupplier(String supplier, LocalDate from, LocalDate to);

  List<LineGraphNode> linesForInvoice(String invoiceNumber);
}
```

- [ ] **Step 2: Write the failing test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/InvoiceGraphServiceImplTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichedInvoiceLine;
import com.goldys.platform.canonical.InvoiceMetadataView;
import com.goldys.platform.semantic.InvoiceGraphNode;
import com.goldys.platform.semantic.LineGraphNode;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvoiceGraphServiceImplTest {

  private final CanonicalInvoiceQuery invoices = mock(CanonicalInvoiceQuery.class);
  private final CanonicalInventoryQuery inventory = mock(CanonicalInventoryQuery.class);
  private final InvoiceGraphServiceImpl service = new InvoiceGraphServiceImpl(invoices, inventory);

  private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
  private static final LocalDate TO = LocalDate.of(2026, 9, 30);

  @Test
  void ranksSuppliersBySpendAndCountsInvoices() {
    when(invoices.currentInvoices())
        .thenReturn(List.of(
            invoice("A. Foods", "INV-1", "2026-09-01", "100.00"),
            invoice("A. Foods", "INV-2", "2026-09-02", "50.00"),
            invoice("B. Beverages", "INV-3", "2026-09-03", "10.00")));
    when(invoices.currentSupplierNames())
        .thenReturn(Map.of("INV-1", "A. Foods", "INV-2", "A. Foods", "INV-3", "B. Beverages"));
    when(inventory.currentEnrichedLines())
        .thenReturn(List.of(
            line("INV-1", "2026-09-01", "20.00"),
            line("INV-2", "2026-09-02", "30.00"),
            line("INV-3", "2026-09-03", "5.00")));

    List<SupplierGraphNode> result = service.suppliers(FROM, TO);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("A. Foods");
    assertThat(result.get(0).invoiceCount()).isEqualTo(2);
    assertThat(result.get(0).totalSpend()).isEqualByComparingTo("50.00");
    assertThat(result.get(1).name()).isEqualTo("B. Beverages");
  }

  @Test
  void fallsBackToHeaderTotalWhenASupplierHasInvoicesButNoLines() {
    when(invoices.currentInvoices())
        .thenReturn(List.of(invoice("A. Foods", "INV-1", "2026-09-01", "100.00")));
    when(invoices.currentSupplierNames()).thenReturn(Map.of("INV-1", "A. Foods"));
    when(inventory.currentEnrichedLines()).thenReturn(List.of());

    List<SupplierGraphNode> result = service.suppliers(FROM, TO);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).totalSpend()).isEqualByComparingTo("100.00");
  }

  @Test
  void bucketsLinesWithoutAHeaderAsUnknown() {
    when(invoices.currentInvoices()).thenReturn(List.of());
    when(invoices.currentSupplierNames()).thenReturn(Map.of());
    when(inventory.currentEnrichedLines())
        .thenReturn(List.of(line("INV-9", "2026-09-10", "12.40")));

    List<SupplierGraphNode> result = service.suppliers(FROM, TO);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("Unknown");
    assertThat(result.get(0).invoiceCount()).isZero();
    assertThat(result.get(0).totalSpend()).isEqualByComparingTo("12.40");
  }

  @Test
  void filtersInvoicesBySupplierNameAndDateDescending() {
    when(invoices.currentInvoices())
        .thenReturn(List.of(
            invoice("A. Foods", "INV-1", "2026-09-01", "100.00"),
            invoice("A. Foods", "INV-2", "2026-09-28", "50.00"),
            invoice("A. Foods", "INV-3", "2026-10-15", "1.00"),
            invoice("B. Beverages", "INV-4", "2026-09-05", "10.00")));

    List<InvoiceGraphNode> result = service.invoicesForSupplier("A. Foods", FROM, TO);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).invoiceNumber()).isEqualTo("INV-2"); // newest first
    assertThat(result.get(1).invoiceNumber()).isEqualTo("INV-1");
  }

  @Test
  void filtersLinesByInvoiceNumber() {
    when(inventory.currentEnrichedLines())
        .thenReturn(List.of(
            line("INV-1", "2026-09-01", "20.00"),
            line("INV-2", "2026-09-02", "30.00")));

    List<LineGraphNode> result = service.linesForInvoice("INV-1");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).productNameKey()).isEqualTo("key");
    assertThat(result.get(0).lineTotal()).isEqualByComparingTo("20.00");
  }

  private static InvoiceMetadataView invoice(
      String supplier, String number, String date, String total) {
    return new InvoiceMetadataView(
        supplier, number, LocalDate.parse(date), new BigDecimal(total), null, null);
  }

  private static EnrichedInvoiceLine line(String invoice, String date, String lineTotal) {
    return new EnrichedInvoiceLine(
        invoice, LocalDate.parse(date), "key", null, BigDecimal.ONE, BigDecimal.ZERO,
        new BigDecimal(lineTotal), null, null, null, null);
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.InvoiceGraphServiceImplTest"`
Expected: FAIL — `InvoiceGraphServiceImpl` does not exist.

- [ ] **Step 4: Write the implementation**

Create `backend/src/main/java/com/goldys/platform/reconciliation/InvoiceGraphServiceImpl.java`:

```java
package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichedInvoiceLine;
import com.goldys.platform.canonical.InvoiceMetadataView;
import com.goldys.platform.semantic.InvoiceGraphNode;
import com.goldys.platform.semantic.InvoiceGraphQuery;
import com.goldys.platform.semantic.LineGraphNode;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * {@link InvoiceGraphQuery} over canonical invoice facts. Supplier identity is the normalized
 * name on the invoice header; lines whose invoice number has no header bucket to {@code "Unknown"}.
 * Spend is the sum of line totals, falling back to header totals when a supplier has invoices but
 * no lines.
 */
@Service
public class InvoiceGraphServiceImpl implements InvoiceGraphQuery {

  private static final String UNKNOWN = "Unknown";

  private final CanonicalInvoiceQuery invoices;
  private final CanonicalInventoryQuery inventory;

  public InvoiceGraphServiceImpl(
      CanonicalInvoiceQuery invoices, CanonicalInventoryQuery inventory) {
    this.invoices = invoices;
    this.inventory = inventory;
  }

  @Override
  public List<SupplierGraphNode> suppliers(LocalDate from, LocalDate to) {
    Map<String, Integer> invoiceCount = new LinkedHashMap<>();
    Map<String, BigDecimal> headerSpend = new LinkedHashMap<>();
    for (InvoiceMetadataView i : invoices.currentInvoices()) {
      if (!inRange(i.invoiceDate(), from, to)) {
        continue;
      }
      invoiceCount.merge(i.supplierName(), 1, Integer::sum);
      if (i.totalAmount() != null) {
        headerSpend.merge(i.supplierName(), i.totalAmount(), BigDecimal::add);
      }
    }

    Map<String, String> supplierByInvoice = invoices.currentSupplierNames();
    Map<String, BigDecimal> lineSpend = new LinkedHashMap<>();
    BigDecimal unknownSpend = BigDecimal.ZERO;
    boolean hasUnknown = false;
    for (EnrichedInvoiceLine line : inventory.currentEnrichedLines()) {
      if (!inRange(line.invoiceDate(), from, to) || line.lineTotal() == null) {
        continue;
      }
      String supplier = supplierByInvoice.get(line.invoiceNumber());
      if (supplier == null) {
        hasUnknown = true;
        unknownSpend = unknownSpend.add(line.lineTotal());
      } else {
        lineSpend.merge(supplier, line.lineTotal(), BigDecimal::add);
      }
    }

    List<SupplierGraphNode> out = new ArrayList<>();
    for (Map.Entry<String, Integer> e : invoiceCount.entrySet()) {
      String supplier = e.getKey();
      BigDecimal spend = lineSpend.get(supplier);
      if (spend == null) {
        spend = headerSpend.get(supplier); // invoices but no lines: fall back to header totals
      }
      out.add(new SupplierGraphNode(supplier, e.getValue(), spend));
    }
    for (String supplier : lineSpend.keySet()) {
      if (!invoiceCount.containsKey(supplier)) {
        out.add(new SupplierGraphNode(supplier, 0, lineSpend.get(supplier)));
      }
    }
    out.sort(Comparator.comparing(
        SupplierGraphNode::totalSpend, Comparator.nullsLast(Comparator.reverseOrder())));
    if (hasUnknown) {
      out.add(new SupplierGraphNode(UNKNOWN, 0, unknownSpend));
    }
    return out;
  }

  @Override
  public List<InvoiceGraphNode> invoicesForSupplier(
      String supplier, LocalDate from, LocalDate to) {
    return invoices.currentInvoices().stream()
        .filter(i -> supplier.equals(i.supplierName()))
        .filter(i -> inRange(i.invoiceDate(), from, to))
        .sorted(Comparator.comparing(InvoiceMetadataView::invoiceDate).reversed())
        .map(i -> new InvoiceGraphNode(
            i.invoiceNumber(), i.invoiceDate(), i.totalAmount(),
            i.purchaseNumber(), i.pdfFilename()))
        .toList();
  }

  @Override
  public List<LineGraphNode> linesForInvoice(String invoiceNumber) {
    return inventory.currentEnrichedLines().stream()
        .filter(l -> invoiceNumber.equals(l.invoiceNumber()))
        .sorted(Comparator.comparing(EnrichedInvoiceLine::lineTotal).reversed())
        .map(l -> new LineGraphNode(
            l.productNameKey(), l.stockCode(), l.quantity(), l.unitCost(),
            l.lineTotal(), l.uom()))
        .toList();
  }

  private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
    return date != null && !date.isBefore(from) && !date.isAfter(to);
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.InvoiceGraphServiceImplTest"`
Expected: PASS.

- [ ] **Step 6: Run the architecture test to confirm layering still holds**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.architecture.ArchitectureBoundariesTest"`
Expected: PASS (semantic leaf + reconciliation impl rules satisfied).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/semantic/SupplierGraphNode.java \
        backend/src/main/java/com/goldys/platform/semantic/InvoiceGraphNode.java \
        backend/src/main/java/com/goldys/platform/semantic/LineGraphNode.java \
        backend/src/main/java/com/goldys/platform/semantic/InvoiceGraphQuery.java \
        backend/src/main/java/com/goldys/platform/reconciliation/InvoiceGraphServiceImpl.java \
        backend/src/test/java/com/goldys/platform/reconciliation/InvoiceGraphServiceImplTest.java
git commit -m "feat(invoice): semantic invoice-graph query (suppliers, invoices, lines)"
```

---

### Task 3: Authorize reads via the application service

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/application/InvoiceGraphService.java`
- Test: `backend/src/test/java/com/goldys/platform/application/InvoiceGraphServiceTest.java`

**Interfaces:**
- Consumes: `InvoiceGraphQuery` (Task 2), `PermissionService.require(UserRole, ResourceKey, PermissionAction)`.
- Produces: `InvoiceGraphService.suppliers(UserRole, LocalDate, LocalDate)`, `.invoicesForSupplier(UserRole, String, LocalDate, LocalDate)`, `.linesForInvoice(UserRole, String)` — consumed by Task 4.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/goldys/platform/application/InvoiceGraphServiceTest.java`:

```java
package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.InvoiceGraphQuery;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceGraphServiceTest {

  private final InvoiceGraphQuery query = mock(InvoiceGraphQuery.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final InvoiceGraphService service = new InvoiceGraphService(query, permissions);

  private static UserRole role() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  @Test
  void delegatesSuppliersAfterAuthorizing() {
    when(query.suppliers(any(), any()))
        .thenReturn(List.of(new SupplierGraphNode("A. Foods", 1, BigDecimal.ONE)));

    List<SupplierGraphNode> result =
        service.suppliers(role(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    assertThat(result).hasSize(1);
    verify(permissions).require(any(), any(), any());
  }

  @Test
  void rejectsReadsWithoutInventoryPermission() {
    doThrow(new RuntimeException("denied")).when(permissions).require(any(), any(), any());

    assertThatThrownBy(
            () -> service.suppliers(role(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .isInstanceOf(RuntimeException.class);
    verify(query, never()).suppliers(any(), any());
  }

  @Test
  void requiresReadOnTheInventoryCostResource() {
    when(query.suppliers(any(), any())).thenReturn(List.of());

    service.suppliers(role(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    verify(permissions)
        .require(role(), new ResourceKey("inventory.cost"), PermissionAction.READ);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.application.InvoiceGraphServiceTest"`
Expected: FAIL — `InvoiceGraphService` does not exist.

- [ ] **Step 3: Write the implementation**

Create `backend/src/main/java/com/goldys/platform/application/InvoiceGraphService.java`:

```java
package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.InvoiceGraphNode;
import com.goldys.platform.semantic.InvoiceGraphQuery;
import com.goldys.platform.semantic.LineGraphNode;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The invoice node graph's read model: authorizes the read and delegates to the semantic
 * {@link InvoiceGraphQuery}. Uses the same {@code inventory.cost} gate as
 * {@link InventoryReportingService}.
 */
@Service
public class InvoiceGraphService {

  private static final ResourceKey RESOURCE = new ResourceKey("inventory.cost");

  private final InvoiceGraphQuery query;
  private final PermissionService permissions;

  public InvoiceGraphService(InvoiceGraphQuery query, PermissionService permissions) {
    this.query = query;
    this.permissions = permissions;
  }

  public List<SupplierGraphNode> suppliers(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.suppliers(from, to);
  }

  public List<InvoiceGraphNode> invoicesForSupplier(
      UserRole role, String supplier, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.invoicesForSupplier(supplier, from, to);
  }

  public List<LineGraphNode> linesForInvoice(UserRole role, String invoiceNumber) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.linesForInvoice(invoiceNumber);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.application.InvoiceGraphServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/application/InvoiceGraphService.java \
        backend/src/test/java/com/goldys/platform/application/InvoiceGraphServiceTest.java
git commit -m "feat(invoice): authorize invoice-graph reads via InvoiceGraphService"
```

---

### Task 4: Serve the graph from the controller

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/api/InventoryController.java`
- Test: `backend/src/test/java/com/goldys/platform/api/InventoryControllerTest.java`

**Interfaces:**
- Consumes: `InvoiceGraphService` (Task 3), existing `InventoryReportingService`, `CurrentUserService.roleOf(AccountUserDetails)`.
- Produces: `GET /api/inventory/graph/suppliers?from=&to=`, `GET /api/inventory/graph/suppliers/{name}/invoices?from=&to=`, `GET /api/inventory/graph/invoices/{number}/lines`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/goldys/platform/api/InventoryControllerTest.java`:

```java
package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.InventoryReportingService;
import com.goldys.platform.application.InvoiceGraphService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.InvoiceGraphNode;
import com.goldys.platform.semantic.LineGraphNode;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(InventoryController.class)
@Import(SecurityConfig.class)
class InventoryControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean InventoryReportingService reporting;
  @MockitoBean InvoiceGraphService graph;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void suppliersReturnsRankedSuppliers() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(graph.suppliers(any(), any(), any()))
        .thenReturn(List.of(new SupplierGraphNode("A. Foods", 214, new BigDecimal("81230.40"))));

    mvc.perform(
            get("/api/inventory/graph/suppliers")
                .param("from", "2026-09-01")
                .param("to", "2026-09-30")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("A. Foods"))
        .andExpect(jsonPath("$[0].invoiceCount").value(214));
  }

  @Test
  void invoicesReturnsTheSuppliersInvoices() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(graph.invoicesForSupplier(any(), any(), any(), any()))
        .thenReturn(List.of(new InvoiceGraphNode(
            "INV-1042", LocalDate.of(2026, 9, 28), new BigDecimal("1420.15"), "PO-88", "inv-1042.pdf")));

    mvc.perform(
            get("/api/inventory/graph/suppliers/A.%20Foods/invoices")
                .param("from", "2026-09-01")
                .param("to", "2026-09-30")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].invoiceNumber").value("INV-1042"))
        .andExpect(jsonPath("$[0].purchaseNumber").value("PO-88"));
  }

  @Test
  void linesReturnsTheInvoicesLines() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(graph.linesForInvoice(any(), any()))
        .thenReturn(List.of(new LineGraphNode(
            "chicken breast", "CB-1", new BigDecimal("4"),
            new BigDecimal("12.5"), new BigDecimal("50.00"), "CTN")));

    mvc.perform(
            get("/api/inventory/graph/invoices/INV-1042/lines")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].productNameKey").value("chicken breast"))
        .andExpect(jsonPath("$[0].uom").value("CTN"));
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.api.InventoryControllerTest"`
Expected: FAIL — the three endpoints don't exist (404).

- [ ] **Step 3: Write the implementation**

Modify `backend/src/main/java/com/goldys/platform/api/InventoryController.java` — add a field, extend the constructor, and add three endpoints. Add imports for `InvoiceGraphService`, `InvoiceGraphNode`, `LineGraphNode`, `SupplierGraphNode`, and `PathVariable`.

```java
  private final InventoryReportingService reporting;
  private final InvoiceGraphService graph;
  private final CurrentUserService currentUser;

  public InventoryController(
      InventoryReportingService reporting,
      InvoiceGraphService graph,
      CurrentUserService currentUser) {
    this.reporting = reporting;
    this.graph = graph;
    this.currentUser = currentUser;
  }
```

Then add (after the existing `lines(...)` method):

```java
  @GetMapping("/graph/suppliers")
  List<SupplierGraphNode> suppliers(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return graph.suppliers(currentUser.roleOf(user), from, to);
  }

  @GetMapping("/graph/suppliers/{supplier}/invoices")
  List<InvoiceGraphNode> invoicesForSupplier(
      @PathVariable String supplier,
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return graph.invoicesForSupplier(currentUser.roleOf(user), supplier, from, to);
  }

  @GetMapping("/graph/invoices/{invoiceNumber}/lines")
  List<LineGraphNode> linesForInvoice(
      @PathVariable String invoiceNumber,
      @AuthenticationPrincipal AccountUserDetails user) {
    return graph.linesForInvoice(currentUser.roleOf(user), invoiceNumber);
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.api.InventoryControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/api/InventoryController.java \
        backend/src/test/java/com/goldys/platform/api/InventoryControllerTest.java
git commit -m "feat(invoice): serve invoice graph from /api/inventory/graph/*"
```

---

### Task 5: Frontend API surface (types + live + demo)

**Files:**
- Modify: `frontend/lib/api/types.ts`
- Modify: `frontend/lib/api/index.ts`
- Create: `frontend/lib/api/invoice-graph.ts`
- Modify: `frontend/lib/api/live.ts`
- Modify: `frontend/lib/api/demo.ts`

**Interfaces:**
- Consumes: `fetchApi` (existing), the `Api` interface (existing).
- Produces: `getInvoiceGraphSuppliers(from, to)`, `getInvoiceGraphInvoices(supplier, from, to)`, `getInvoiceGraphLines(invoiceNumber)` + types `SupplierGraphNode`, `InvoiceGraphNode`, `LineGraphNode` — consumed by Tasks 7 and 8.

- [ ] **Step 1: Add the types**

In `frontend/lib/api/types.ts`, add after `InventoryLineBreakdown` (near line 135):

```ts
/** One supplier in the invoice node graph (mirrors `semantic.SupplierGraphNode`). */
export interface SupplierGraphNode {
  name: string;
  invoiceCount: number;
  totalSpend: number | null;
}

/** One invoice header in the invoice node graph (mirrors `semantic.InvoiceGraphNode`). */
export interface InvoiceGraphNode {
  invoiceNumber: string;
  invoiceDate: string;
  totalAmount: number | null;
  purchaseNumber: string | null;
  pdfFilename: string | null;
}

/** One invoice line in the invoice node graph (mirrors `semantic.LineGraphNode`). */
export interface LineGraphNode {
  productNameKey: string;
  stockCode: string | null;
  quantity: number;
  unitCost: number;
  lineTotal: number;
  uom: string | null;
}
```

Then add three methods to the `Api` interface (next to `getInventoryLines`):

```ts
  getInvoiceGraphSuppliers(from: string, to: string): Promise<SupplierGraphNode[]>;
  getInvoiceGraphInvoices(
    supplier: string,
    from: string,
    to: string,
  ): Promise<InvoiceGraphNode[]>;
  getInvoiceGraphLines(invoiceNumber: string): Promise<LineGraphNode[]>;
```

- [ ] **Step 2: Re-export the types**

In `frontend/lib/api/index.ts`, add `InvoiceGraphNode`, `LineGraphNode`, `SupplierGraphNode` to the `export type { ... }` list (alphabetical, next to `InventorySummary`).

- [ ] **Step 3: Write the API module**

Create `frontend/lib/api/invoice-graph.ts`:

```ts
import { fetchApi } from "./client";
import type { InvoiceGraphNode, LineGraphNode, SupplierGraphNode } from "./types";

/** Ranked supplier spend for the invoice graph (`GET /api/inventory/graph/suppliers`). */
export async function getInvoiceGraphSuppliers(
  from: string,
  to: string,
): Promise<SupplierGraphNode[]> {
  return fetchApi<SupplierGraphNode[]>(
    `/api/inventory/graph/suppliers?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
}

/** One supplier's invoices, newest first (`GET /api/inventory/graph/suppliers/{name}/invoices`). */
export async function getInvoiceGraphInvoices(
  supplier: string,
  from: string,
  to: string,
): Promise<InvoiceGraphNode[]> {
  return fetchApi<InvoiceGraphNode[]>(
    `/api/inventory/graph/suppliers/${encodeURIComponent(supplier)}/invoices?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
}

/** One invoice's line items, largest first (`GET /api/inventory/graph/invoices/{number}/lines`). */
export async function getInvoiceGraphLines(
  invoiceNumber: string,
): Promise<LineGraphNode[]> {
  return fetchApi<LineGraphNode[]>(
    `/api/inventory/graph/invoices/${encodeURIComponent(invoiceNumber)}/lines`,
  );
}
```

- [ ] **Step 4: Wire live.ts**

In `frontend/lib/api/live.ts`, add `import { getInvoiceGraphSuppliers, getInvoiceGraphInvoices, getInvoiceGraphLines } from "./invoice-graph";` and add these three shorthand entries to the `liveApi` object (next to `getProvenance`):

```ts
  getInvoiceGraphSuppliers,
  getInvoiceGraphInvoices,
  getInvoiceGraphLines,
```

- [ ] **Step 5: Wire demo.ts**

In `frontend/lib/api/demo.ts`, add `InvoiceGraphNode`, `LineGraphNode`, `SupplierGraphNode` to the `import type { ... } from "./types"` block, then add these three methods after `getInventorySummary` (around line 736):

```ts
  async getInvoiceGraphSuppliers(from: string, to: string): Promise<SupplierGraphNode[]> {
    await delay(300);
    return [
      { name: "Paramount Liquor", invoiceCount: 214, totalSpend: 9240.0 },
      { name: "Oranges & Lemons", invoiceCount: 98, totalSpend: 4820.5 },
      { name: "Sealane Beverages", invoiceCount: 51, totalSpend: 1240.0 },
      { name: "Unknown", invoiceCount: 0, totalSpend: 12.4 },
    ];
  },

  async getInvoiceGraphInvoices(
    supplier: string,
    from: string,
    to: string,
  ): Promise<InvoiceGraphNode[]> {
    await delay(300);
    return [
      {
        invoiceNumber: "INV-1042",
        invoiceDate: "2026-09-28",
        totalAmount: 1420.15,
        purchaseNumber: "PO-88",
        pdfFilename: "inv-1042.pdf",
      },
      {
        invoiceNumber: "INV-1091",
        invoiceDate: "2026-09-21",
        totalAmount: 980.4,
        purchaseNumber: "PO-91",
        pdfFilename: "inv-1091.pdf",
      },
    ];
  },

  async getInvoiceGraphLines(invoiceNumber: string): Promise<LineGraphNode[]> {
    await delay(300);
    return [
      { productNameKey: "chicken breast", stockCode: "CB-1", quantity: 4, unitCost: 12.5, lineTotal: 50.0, uom: "CTN" },
      { productNameKey: "beef mince", stockCode: "BM-2", quantity: 10, unitCost: 9.0, lineTotal: 90.0, uom: "KG" },
    ];
  },
```

- [ ] **Step 6: Verify it compiles and lints**

Run: `cd frontend && bun run typecheck && bun run lint`
Expected: PASS (no type errors, no lint errors).

- [ ] **Step 7: Commit**

```bash
git add frontend/lib/api/types.ts frontend/lib/api/index.ts \
        frontend/lib/api/invoice-graph.ts frontend/lib/api/live.ts frontend/lib/api/demo.ts
git commit -m "feat(frontend): add invoice-graph API surface (live + demo)"
```

---

### Task 6: Add drill interaction to the flow canvas

**Files:**
- Modify: `frontend/components/flow/types.ts`
- Modify: `frontend/components/flow/flow-canvas.tsx`
- Test: `frontend/components/flow/flow-canvas.test.ts`

**Interfaces:**
- Consumes: `StepNodeData` (existing), `FlowCanvasProps` (existing).
- Produces: `StepNodeData.drill?: string` and `FlowCanvas.onDrill?: (id: string) => void`, plus exported `resolveNodeClick(data, onDrill?, navigate?)` — consumed by Task 7/8.

- [ ] **Step 1: Write the failing test**

Create `frontend/components/flow/flow-canvas.test.ts`:

```ts
import { describe, it, expect, vi } from "vitest";
import { resolveNodeClick } from "./flow-canvas";
import type { StepNodeData } from "./types";

const data = (overrides: Partial<StepNodeData> = {}): StepNodeData => ({
  title: "Node",
  icon: () => null,
  tone: "neutral",
  ...overrides,
});

describe("resolveNodeClick", () => {
  it("prefers drill over navigation when both are present", () => {
    const onDrill = vi.fn();
    const navigate = vi.fn();
    resolveNodeClick(data({ drill: "supplier:X", href: "/somewhere" }), onDrill, navigate);
    expect(onDrill).toHaveBeenCalledWith("supplier:X");
    expect(navigate).not.toHaveBeenCalled();
  });

  it("navigates when there is no drill", () => {
    const navigate = vi.fn();
    resolveNodeClick(data({ href: "/somewhere" }), vi.fn(), navigate);
    expect(navigate).toHaveBeenCalledWith("/somewhere");
  });

  it("falls back to href navigation when a drill is set but no onDrill handler is provided", () => {
    const navigate = vi.fn();
    resolveNodeClick(data({ drill: "supplier:X", href: "/somewhere" }), undefined, navigate);
    expect(navigate).toHaveBeenCalledWith("/somewhere");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun test components/flow/flow-canvas.test.ts`
Expected: FAIL — `resolveNodeClick` is not exported.

- [ ] **Step 3: Add the `drill` field**

In `frontend/components/flow/types.ts`, add to `StepNodeData` (after `href`):

```ts
  /** An opaque id the consumer handles via `FlowCanvas` `onDrill`. Takes precedence over `href`. */
  drill?: string;
```

- [ ] **Step 4: Add the `onDrill` prop and the click resolver**

In `frontend/components/flow/flow-canvas.tsx`:

1. Update the type import to `import type { ColumnGraph, StepNode, StepNodeData } from "./types";` (add `StepNodeData`).
2. Extend `FlowCanvasProps` with `onDrill?: (id: string) => void;`
3. Pass `onDrill` through to `FlowCanvasInner` (add it to its props).
4. Replace the `onNodeClick` body so it calls `resolveNodeClick(data, onDrill, (href) => router.push(href))`.
5. Add the exported helper near the top of the file (after the imports):

```ts
/**
 * Decides what a node click does: a node with a `drill` id and an `onDrill` handler drills in;
 * otherwise a node with an `href` navigates. Extracted so the precedence is unit-testable without
 * rendering ReactFlow.
 */
export function resolveNodeClick(
  data: StepNodeData,
  onDrill: ((id: string) => void) | undefined,
  navigate: (href: string) => void,
): void {
  if (onDrill && data.drill) {
    onDrill(data.drill);
    return;
  }
  if (data.href) {
    navigate(data.href);
  }
}
```

The `onNodeClick` becomes:

```tsx
        onNodeClick={(_, node) => {
          const data = (node as StepNode).data;
          resolveNodeClick(data, onDrill, (href) => router.push(href));
        }}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && bun test components/flow/flow-canvas.test.ts`
Expected: PASS.

- [ ] **Step 6: Run the full frontend test suite to confirm no regressions**

Run: `cd frontend && bun test && bun run typecheck`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/components/flow/types.ts frontend/components/flow/flow-canvas.tsx \
        frontend/components/flow/flow-canvas.test.ts
git commit -m "feat(frontend): add drill interaction to the flow canvas"
```

---

### Task 7: The pure builder + heuristic

**Files:**
- Create: `frontend/components/inventory/invoice-graph.ts`
- Test: `frontend/components/inventory/invoice-graph.test.ts`

**Interfaces:**
- Consumes: `ColumnGraph`/`StepNodeData`/`FlowTone` (existing), `SupplierGraphNode`/`InvoiceGraphNode`/`LineGraphNode` (Task 5).
- Produces: `buildInvoiceGraph(input): ColumnGraph`, `SUPPLIER_TOP_N`, `INVOICE_TOP_N`, `LINE_TOP_N`, `UNKNOWN_SUPPLIER`, `supplierDrillId(name)`, `invoiceDrillId(number)` — consumed by Task 8.

- [ ] **Step 1: Write the failing test**

Create `frontend/components/inventory/invoice-graph.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import type { InvoiceGraphNode, LineGraphNode, SupplierGraphNode } from "@/lib/api/types";
import {
  buildInvoiceGraph,
  INVOICE_TOP_N,
  LINE_TOP_N,
  SUPPLIER_TOP_N,
  supplierDrillId,
  invoiceDrillId,
  type InvoiceGraphInput,
} from "./invoice-graph";

const supplier = (name: string, invoiceCount: number, totalSpend: number | null): SupplierGraphNode => ({
  name,
  invoiceCount,
  totalSpend,
});

const invoice = (number: string, date: string, totalAmount: number): InvoiceGraphNode => ({
  invoiceNumber: number,
  invoiceDate: date,
  totalAmount,
  purchaseNumber: null,
  pdfFilename: null,
});

const line = (key: string, lineTotal: number, uom: string | null = null): LineGraphNode => ({
  productNameKey: key,
  stockCode: null,
  quantity: 1,
  unitCost: lineTotal,
  lineTotal,
  uom,
});

const input = (overrides: Partial<InvoiceGraphInput> = {}): InvoiceGraphInput => ({
  suppliers: [],
  invoices: null,
  lines: null,
  focusedSupplier: null,
  focusedInvoice: null,
  ...overrides,
});

describe("buildInvoiceGraph", () => {
  it("renders the top-N suppliers plus an Other rollup", () => {
    const suppliers = Array.from({ length: SUPPLIER_TOP_N + 2 }, (_, i) =>
      supplier(`Supplier ${i}`, 1, 100 - i),
    );
    const g = buildInvoiceGraph(input({ suppliers }));
    expect(g.columns).toHaveLength(1);
    const col0 = g.columns[0];
    expect(col0).toHaveLength(SUPPLIER_TOP_N + 1);
    expect(col0[0].data.title).toBe("Supplier 0");
    expect(col0[SUPPLIER_TOP_N].data.title).toBe("Other 2 suppliers");
  });

  it("shows the Unknown supplier as a warn, non-drillable node that is never folded into Other", () => {
    const suppliers = [
      supplier("A. Foods", 1, 100),
      supplier("Unknown", 0, 12.4),
    ];
    const g = buildInvoiceGraph(input({ suppliers }));
    const col0 = g.columns[0];
    const unknown = col0.find((n) => n.id === "sup:unknown");
    expect(unknown).toBeDefined();
    expect(unknown!.data.tone).toBe("warn");
    expect(unknown!.data.drill).toBeUndefined();
    expect(col0.some((n) => n.id === "sup:__other__")).toBe(false);
  });

  it("returns a single missing node for an empty range", () => {
    const g = buildInvoiceGraph(input({ suppliers: [] }));
    expect(g.columns).toHaveLength(1);
    expect(g.columns[0][0].id).toBe("empty");
    expect(g.columns[0][0].data.tone).toBe("missing");
    expect(g.columns[0][0].data.title).toBe("No invoices in range");
  });

  it("adds the invoices column and edges when a supplier is focused", () => {
    const g = buildInvoiceGraph(
      input({
        suppliers: [supplier("A. Foods", 2, 150)],
        focusedSupplier: "A. Foods",
        invoices: [invoice("INV-2", "2026-09-28", 50), invoice("INV-1", "2026-09-01", 100)],
      }),
    );
    expect(g.columns).toHaveLength(2);
    const inv = g.columns[1];
    expect(inv[0].data.title).toBe("INV-2"); // newest first
    expect(inv[0].data.drill).toBe(invoiceDrillId("INV-2"));
    expect(g.edges.some((e) => e.source === "sup:A. Foods" && e.target === "inv:INV-2")).toBe(true);
  });

  it("dedupes identical product keys and orders lines by line total", () => {
    const lines = [
      line("chicken breast", 50),
      line("chicken breast", 30), // duplicates the key above -> summed to 80
      line("beef mince", 90),
    ];
    const g = buildInvoiceGraph(
      input({ suppliers: [supplier("A. Foods", 1, 100)], focusedSupplier: "A. Foods",
              invoices: [invoice("INV-1", "2026-09-01", 100)], focusedInvoice: "INV-1", lines }),
    );
    expect(g.columns).toHaveLength(3);
    const linesColumn = g.columns[2];
    // beef mince (90) ranks above the deduped chicken breast (80)
    expect(linesColumn[0].data.title).toBe("beef mince");
    expect(linesColumn[1].data.title).toBe("chicken breast");
  });

  it("never renders an Other rollup for a single excess item (promote instead)", () => {
    const suppliers = Array.from({ length: SUPPLIER_TOP_N + 1 }, (_, i) =>
      supplier(`Supplier ${i}`, 1, 100 - i),
    );
    const g = buildInvoiceGraph(input({ suppliers }));
    const col0 = g.columns[0];
    // 9 suppliers: top-8 + the 9th promoted as a normal node (no "Other 1 suppliers" rollup)
    expect(col0.some((n) => n.data.title.startsWith("Other 1"))).toBe(false);
    expect(col0).toHaveLength(SUPPLIER_TOP_N + 1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun test components/inventory/invoice-graph.test.ts`
Expected: FAIL — `./invoice-graph` does not exist.

- [ ] **Step 3: Write the builder**

Create `frontend/components/inventory/invoice-graph.ts`:

```ts
import { Inbox, Package, Receipt, Truck } from "lucide-react";
import type { ColumnGraph, FlowTone, StepNodeData } from "@/components/flow/types";
import type { InvoiceGraphNode, LineGraphNode, SupplierGraphNode } from "@/lib/api/types";

export const SUPPLIER_TOP_N = 8;
export const INVOICE_TOP_N = 8;
export const LINE_TOP_N = 10;
export const UNKNOWN_SUPPLIER = "Unknown";

export function supplierDrillId(name: string): string {
  return `supplier:${name}`;
}

export function invoiceDrillId(number: string): string {
  return `invoice:${number}`;
}

export interface InvoiceGraphInput {
  suppliers: SupplierGraphNode[];
  invoices: InvoiceGraphNode[] | null;
  lines: LineGraphNode[] | null;
  focusedSupplier: string | null;
  focusedInvoice: string | null;
}

function money(v: number | null | undefined): string {
  return v == null || !Number.isFinite(Number(v)) ? "—" : `$${Number(v).toFixed(2)}`;
}

function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}

/** Top-N, but never a rollup of one: a single leftover is promoted to a normal node. */
function split<T>(items: T[], n: number): { shown: T[]; rest: T[] } {
  if (items.length <= n + 1) {
    return { shown: items, rest: [] };
  }
  return { shown: items.slice(0, n), rest: items.slice(n) };
}

export function buildInvoiceGraph(input: InvoiceGraphInput): ColumnGraph {
  const real = input.suppliers
    .filter((s) => s.name !== UNKNOWN_SUPPLIER)
    .slice()
    .sort((a, b) => (b.totalSpend ?? -1) - (a.totalSpend ?? -1));
  const unknown = input.suppliers.find((s) => s.name === UNKNOWN_SUPPLIER);

  const { shown: topSuppliers, rest: restSuppliers } = split(real, SUPPLIER_TOP_N);

  const supplierNodes: { id: string; data: StepNodeData }[] = topSuppliers.map((s) => ({
    id: `sup:${s.name}`,
    data: {
      title: s.name,
      subtitle: `${money(s.totalSpend)} · ${plural(s.invoiceCount, "invoice", "invoices")}`,
      icon: Truck,
      tone: (s.invoiceCount > 0 && (s.totalSpend == null || s.totalSpend === 0)
        ? "missing"
        : "neutral") as FlowTone,
      drill: supplierDrillId(s.name),
    },
  }));

  if (restSuppliers.length > 0) {
    supplierNodes.push({
      id: "sup:__other__",
      data: {
        title: `Other ${plural(restSuppliers.length, "supplier", "suppliers")}`,
        subtitle: money(restSuppliers.reduce((sum, s) => sum + (s.totalSpend ?? 0), 0)),
        icon: Truck,
        tone: "neutral",
      },
    });
  }

  if (unknown) {
    supplierNodes.push({
      id: "sup:unknown",
      data: {
        title: "Unknown supplier",
        subtitle: money(unknown.totalSpend),
        detail: "Lines with no matching invoice",
        icon: Truck,
        tone: "warn",
      },
    });
  }

  if (supplierNodes.length === 0) {
    return {
      columns: [[{
        id: "empty",
        data: {
          title: "No invoices in range",
          subtitle: "Nothing ingested for this period",
          icon: Inbox,
          tone: "missing",
        },
      }]],
      edges: [],
    };
  }

  const columns: { id: string; data: StepNodeData }[][] = [supplierNodes];
  const edges: ColumnGraph["edges"] = [];

  if (input.focusedSupplier && input.invoices) {
    const sorted = input.invoices
      .slice()
      .sort((a, b) => b.invoiceDate.localeCompare(a.invoiceDate));
    const { shown: topInvoices, rest: restInvoices } = split(sorted, INVOICE_TOP_N);

    const invoiceNodes: { id: string; data: StepNodeData }[] = topInvoices.map((inv) => ({
      id: `inv:${inv.invoiceNumber}`,
      data: {
        title: inv.invoiceNumber,
        subtitle: `${inv.invoiceDate} · ${money(inv.totalAmount)}`,
        detail: inv.purchaseNumber ?? inv.pdfFilename ?? undefined,
        icon: Receipt,
        tone: "neutral",
        drill: invoiceDrillId(inv.invoiceNumber),
      },
    }));

    if (restInvoices.length > 0) {
      invoiceNodes.push({
        id: "inv:__other__",
        data: {
          title: `Other ${plural(restInvoices.length, "invoice", "invoices")}`,
          subtitle: money(restInvoices.reduce((sum, i) => sum + (i.totalAmount ?? 0), 0)),
          icon: Receipt,
          tone: "neutral",
        },
      });
    }

    columns.push(invoiceNodes);
    for (const n of invoiceNodes) {
      edges.push({ source: `sup:${input.focusedSupplier}`, target: n.id });
    }
  }

  if (input.focusedInvoice && input.lines) {
    const deduped = dedupeLines(input.lines);
    const { shown: topLines, rest: restLines } = split(deduped, LINE_TOP_N);

    const lineNodes: { id: string; data: StepNodeData }[] = topLines.map((l) => ({
      id: `line:${input.focusedInvoice}:${l.productNameKey ?? l.stockCode ?? "(unnamed)"}`,
      data: {
        title: l.productNameKey ?? l.stockCode ?? "(unnamed)",
        subtitle: `${l.quantity} × ${money(l.unitCost)}`,
        detail: l.uom ?? undefined,
        icon: Package,
        tone: "neutral",
      },
    }));

    if (restLines.length > 0) {
      lineNodes.push({
        id: "line:__other__",
        data: {
          title: `Other ${plural(restLines.length, "line", "lines")}`,
          subtitle: money(restLines.reduce((sum, l) => sum + l.lineTotal, 0)),
          icon: Package,
          tone: "neutral",
        },
      });
    }

    columns.push(lineNodes);
    for (const n of lineNodes) {
      edges.push({ source: `inv:${input.focusedInvoice}`, target: n.id });
    }
  }

  return { columns, edges };
}

/** Sums duplicate product keys so a split line can't double-count, then ranks by line total. */
function dedupeLines(lines: LineGraphNode[]): LineGraphNode[] {
  const byKey = new Map<string, LineGraphNode>();
  for (const l of lines) {
    const key = l.productNameKey ?? l.stockCode ?? "(unnamed)";
    const existing = byKey.get(key);
    if (existing) {
      byKey.set(key, {
        ...existing,
        quantity: existing.quantity + l.quantity,
        lineTotal: existing.lineTotal + l.lineTotal,
      });
    } else {
      byKey.set(key, l);
    }
  }
  return [...byKey.values()].sort((a, b) => b.lineTotal - a.lineTotal);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && bun test components/inventory/invoice-graph.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/inventory/invoice-graph.ts \
        frontend/components/inventory/invoice-graph.test.ts
git commit -m "feat(frontend): invoice node-graph builder with top-N rollup heuristic"
```

---

### Task 8: The view + wire into the Kitchen screen

**Files:**
- Create: `frontend/components/inventory/invoice-graph-view.tsx`
- Modify: `frontend/app/(app)/kitchen/page.tsx`

**Interfaces:**
- Consumes: `buildInvoiceGraph` + `supplierDrillId`/`invoiceDrillId` prefixes (Task 7), the `Api` methods (Task 5), `FlowCanvas` with `onDrill` (Task 6), `useApiData` (existing).
- Produces: `<InvoiceGraphView from to />` on the Kitchen screen. No new public module interface.

- [ ] **Step 1: Write the view**

Create `frontend/components/inventory/invoice-graph-view.tsx`:

```tsx
"use client";

import { useMemo, useState } from "react";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { Skeleton } from "@/components/ui/skeleton";
import { useApiData } from "@/lib/use-api-data";
import { buildInvoiceGraph } from "./invoice-graph";

const SUPPLIER_PREFIX = "supplier:";
const INVOICE_PREFIX = "invoice:";

interface InvoiceGraphViewProps {
  from: string;
  to: string;
}

/**
 * The invoice node graph: suppliers → invoices → line items, one column revealed per drill. The
 * `onDrill` handler decodes the `supplier:`/`invoice:` ids the builder stamps on nodes.
 */
export function InvoiceGraphView({ from, to }: InvoiceGraphViewProps) {
  const [focusedSupplier, setFocusedSupplier] = useState<string | null>(null);
  const [focusedInvoice, setFocusedInvoice] = useState<string | null>(null);

  const suppliers = useApiData((api) => api.getInvoiceGraphSuppliers(from, to), [from, to]);
  const invoices = useApiData(
    (api) =>
      focusedSupplier
        ? api.getInvoiceGraphInvoices(focusedSupplier, from, to)
        : Promise.resolve(null),
    [focusedSupplier, from, to],
  );
  const lines = useApiData(
    (api) =>
      focusedInvoice ? api.getInvoiceGraphLines(focusedInvoice) : Promise.resolve(null),
    [focusedInvoice],
  );

  const graph = useMemo(
    () =>
      buildInvoiceGraph({
        suppliers: suppliers.data ?? [],
        invoices: invoices.data,
        lines: lines.data,
        focusedSupplier,
        focusedInvoice,
      }),
    [suppliers.data, invoices.data, lines.data, focusedSupplier, focusedInvoice],
  );

  function onDrill(id: string) {
    if (id.startsWith(SUPPLIER_PREFIX)) {
      setFocusedSupplier(id.slice(SUPPLIER_PREFIX.length));
      setFocusedInvoice(null);
    } else if (id.startsWith(INVOICE_PREFIX)) {
      setFocusedInvoice(id.slice(INVOICE_PREFIX.length));
    }
  }

  function back() {
    if (focusedInvoice) setFocusedInvoice(null);
    else setFocusedSupplier(null);
  }

  if (suppliers.loading) return <Skeleton className="h-[360px] w-full" />;

  return (
    <div className="flex flex-col gap-2">
      {focusedSupplier || focusedInvoice ? (
        <button
          type="button"
          onClick={back}
          className="self-start text-sm text-muted-foreground underline-offset-2 hover:underline"
        >
          ← Back
        </button>
      ) : null}
      <FlowCanvas
        graph={graph}
        onDrill={onDrill}
        ariaLabel="Invoices: suppliers, invoices, and line items"
        height={360}
      />
    </div>
  );
}
```

- [ ] **Step 2: Wire it into the Kitchen screen**

In `frontend/app/(app)/kitchen/page.tsx`:
1. Add `import { InvoiceGraphView } from "@/components/inventory/invoice-graph-view";`
2. Insert a section after the `<SummaryStats .../>` block and before the `uom.length === 0 ...` empty-state check:

```tsx
      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-semibold">Purchase lineage</h2>
        <InvoiceGraphView from={from} to={to} />
      </section>
```

- [ ] **Step 3: Verify it compiles, lints, and the suite still passes**

Run: `cd frontend && bun run typecheck && bun run lint && bun test`
Expected: PASS. (There is no dedicated ReactFlow-rendering component test, matching the existing convention in PR #69 — `pipeline-graph` and `provenance-graph` are tested only as pure builders; the flow canvas is exercised by the `resolveNodeClick` unit test from Task 6.)

- [ ] **Step 4: Manual smoke check**

Run the dev servers, open the Kitchen screen, and confirm: suppliers render (top-8 + "Other"), clicking a supplier reveals its invoices, clicking an invoice reveals its lines, "← Back" collapses a column, and an empty range shows the "No invoices in range" node. Confirm demo mode shows the fixture suppliers including the "Unknown" warn node.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/inventory/invoice-graph-view.tsx \
        frontend/app/\(app\)/kitchen/page.tsx
git commit -m "feat(frontend): invoice node-graph view on the Kitchen screen"
```

---

## Execution order & dependencies

Tasks 1→4 are strictly ordered (backend, each depends on the previous). Task 5 is independent of 1–4 and can run in parallel. Tasks 6 and 7 are independent of each other and of the backend; Task 8 depends on 5, 6, and 7. A parallel split: **stream A = Tasks 1–4 (backend)**, **stream B = Task 5, then 6 & 7, then 8 (frontend)**.

## Verification (whole branch, before finishing)

```bash
cd backend && ./gradlew test          # all backend tests incl. archunit
cd frontend && bun test && bun run typecheck && bun run lint
```
