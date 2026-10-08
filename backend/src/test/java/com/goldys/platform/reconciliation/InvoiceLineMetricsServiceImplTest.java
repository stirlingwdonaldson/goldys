package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichedInvoiceLine;
import com.goldys.platform.semantic.SupplierCogs;
import com.goldys.platform.semantic.UomUnitCost;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvoiceLineMetricsServiceImplTest {

  private final CanonicalInventoryQuery inventory = mock(CanonicalInventoryQuery.class);
  private final CanonicalInvoiceQuery invoices = mock(CanonicalInvoiceQuery.class);
  private final InvoiceLineMetricsServiceImpl service =
      new InvoiceLineMetricsServiceImpl(inventory, invoices);

  @Test
  void aggregatesUnitCostPerUom() {
    when(inventory.currentEnrichedLines())
        .thenReturn(
            List.of(
                line("INV-1", "2026-09-01", "KG", "2", "10.00", null),
                line("INV-1", "2026-09-01", "KG", "3", "12.00", null),
                line("INV-2", "2026-09-02", "EACH", "5", "25.00", null),
                line("INV-2", "2026-09-02", null, "1", "7.00", null)));

    List<UomUnitCost> result =
        service.unitCostByUom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    assertThat(result).hasSize(3);
    UomUnitCost kg = result.get(1); // EACH sorts before KG
    assertThat(kg.uom()).isEqualTo("KG");
    assertThat(kg.lineTotal()).isEqualByComparingTo("22.00");
    assertThat(kg.quantity()).isEqualByComparingTo("5");
    assertThat(kg.unitCost()).isEqualByComparingTo("4.4000");
    UomUnitCost each = result.get(0);
    assertThat(each.uom()).isEqualTo("EACH");
    assertThat(each.unitCost()).isEqualByComparingTo("5.0000");
  }

  @Test
  void aggregatesCogsAndWetBySupplierDescending() {
    when(invoices.currentSupplierNames())
        .thenReturn(Map.of("INV-1", "Paramount Liquor", "INV-2", "Oranges & Lemons"));
    when(inventory.currentEnrichedLines())
        .thenReturn(
            List.of(
                line("INV-1", "2026-09-01", "EACH", "12", "120.00", "18.50"),
                line("INV-1", "2026-09-01", "EACH", "6", "60.00", "12.75"),
                line("INV-2", "2026-09-02", "KG", "10", "30.00", null),
                line("INV-3", "2026-09-03", "KG", "1", "5.00", null)));

    List<SupplierCogs> result =
        service.cogsBySupplier(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    assertThat(result).hasSize(3);
    assertThat(result.get(0).supplier()).isEqualTo("Paramount Liquor");
    assertThat(result.get(0).lineTotal()).isEqualByComparingTo("180.00");
    assertThat(result.get(0).wetAmount()).isEqualByComparingTo("31.25");
    assertThat(result.get(1).supplier()).isEqualTo("Oranges & Lemons");
    assertThat(result.get(2).supplier()).isEqualTo("Unknown"); // INV-3 has no supplier mapping
  }

  private static EnrichedInvoiceLine line(
      String invoice,
      String date,
      String uom,
      String quantity,
      String lineTotal,
      String wetAmount) {
    return new EnrichedInvoiceLine(
        invoice,
        LocalDate.parse(date),
        "key",
        null,
        new BigDecimal(quantity),
        BigDecimal.ZERO,
        new BigDecimal(lineTotal),
        uom,
        null,
        null,
        wetAmount == null ? null : new BigDecimal(wetAmount));
  }
}
