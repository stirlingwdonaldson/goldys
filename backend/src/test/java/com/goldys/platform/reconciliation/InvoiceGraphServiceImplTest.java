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
