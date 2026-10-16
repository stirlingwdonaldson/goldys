package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.canonical.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class InvoicePurchaseBreakdownTest {
  final LocalDate date = LocalDate.of(2026, 10, 1);
  final UUID supplier = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();

  InvoiceMetadataView header(UUID id, UUID supplierId, String label, String gross) {
    return new InvoiceMetadataView(
        label,
        "SHARED",
        date,
        null,
        new BigDecimal(gross),
        null,
        null,
        null,
        null,
        null,
        id,
        supplierId);
  }

  EnrichedInvoiceLine line(UUID parent, String total, String wet) {
    return new EnrichedInvoiceLine(
        "SHARED",
        date,
        "item",
        "sku",
        BigDecimal.ONE,
        BigDecimal.ONE,
        new BigDecimal(total),
        "EACH",
        null,
        null,
        wet == null ? null : new BigDecimal(wet),
        null,
        null,
        null,
        null,
        parent);
  }

  @Test
  void mixedHeaderAvailabilityDoesNotChangePurchaseTaxBasis() {
    var result =
        InvoicePurchaseBreakdown.summarize(
                List.of(header(a, supplier, "A", "110"), header(b, supplier, "A", "220")),
                List.of(line(a, "100", null)),
                date,
                date)
            .getFirst();
    assertThat(result.lineTotal()).isEqualByComparingTo("100");
    assertThat(result.invoiceCount()).isEqualTo(2);
    assertThat(result.invoicesWithoutLines()).isEqualTo(1);
    assertThat(result.wetAmount()).isNull();
    assertThat(result.notices()).anyMatch(n -> n.contains("1 invoices have no lines"));
  }

  @Test
  void sameNamedSuppliersRemainSeparateAndSignedLinesCountOnce() {
    var result =
        InvoicePurchaseBreakdown.summarize(
            List.of(
                header(a, supplier, "Same", "110"), header(b, UUID.randomUUID(), "Same", "220")),
            List.of(line(a, "100", "0"), line(a, "-10", null), line(b, "200", "2")),
            date,
            date);
    assertThat(result).hasSize(2);
    assertThat(result.stream().map(x -> x.lineTotal()).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("290");
    assertThat(result)
        .anySatisfy(
            x -> {
              assertThat(x.lineTotal()).isEqualByComparingTo("90");
              assertThat(x.wetAmount()).isEqualByComparingTo("0");
              assertThat(x.notices()).anyMatch(n -> n.contains("WET incomplete"));
            });
  }

  @Test
  void orphanLinesStayUnresolvedDespiteMatchingDisplayNumber() {
    var result =
        InvoicePurchaseBreakdown.summarize(
            List.of(header(a, supplier, "A", "110")), List.of(line(null, "12", null)), date, date);
    assertThat(result).hasSize(2);
    assertThat(result)
        .anySatisfy(
            x -> {
              assertThat(x.supplierId()).isNull();
              assertThat(x.supplier()).isEqualTo("Unknown");
              assertThat(x.lineTotal()).isEqualByComparingTo("12");
            });
  }

  @Test
  void headerOnlyAndKnownZeroRemainDifferentStates() {
    var result =
        InvoicePurchaseBreakdown.summarize(
                List.of(header(a, supplier, "A", "110")), List.of(), date, date)
            .getFirst();
    assertThat(result.lineTotal()).isNull();
    var zero =
        InvoicePurchaseBreakdown.summarize(
                List.of(header(a, supplier, "A", "0")), List.of(line(a, "0", "0")), date, date)
            .getFirst();
    assertThat(zero.lineTotal()).isEqualByComparingTo("0");
    assertThat(zero.wetAmount()).isEqualByComparingTo("0");
  }
}
