package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.EnrichedInvoiceLine;
import com.goldys.platform.canonical.InvoiceMetadataView;
import com.goldys.platform.semantic.SupplierCogs;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Shares the line-only amount basis; header evidence never fills missing purchase lines. */
final class InvoicePurchaseBreakdown {
  private InvoicePurchaseBreakdown() {}

  static List<SupplierCogs> summarize(
      List<InvoiceMetadataView> headers,
      List<EnrichedInvoiceLine> lines,
      LocalDate from,
      LocalDate to) {
    Map<UUID, InvoiceMetadataView> byInvoice = new HashMap<>();
    Map<UUID, Bucket> buckets = new LinkedHashMap<>();
    Set<UUID> withLines = new HashSet<>();
    for (InvoiceMetadataView header : headers) {
      if (header.invoiceId() != null) byInvoice.put(header.invoiceId(), header);
      if (inRange(header.invoiceDate(), from, to)) {
        Bucket b =
            buckets.computeIfAbsent(
                header.supplierId(),
                key -> new Bucket(key == null ? "Unknown" : header.supplierName()));
        b.invoices++;
      }
    }
    for (EnrichedInvoiceLine line : lines) {
      if (!inRange(line.invoiceDate(), from, to)) continue;
      InvoiceMetadataView header = byInvoice.get(line.invoiceId());
      UUID supplier = header == null ? null : header.supplierId();
      Bucket b =
          buckets.computeIfAbsent(
              supplier, key -> new Bucket(key == null ? "Unknown" : header.supplierName()));
      if (line.invoiceId() != null) withLines.add(line.invoiceId());
      b.total = add(b.total, line.lineTotal());
      b.wet = add(b.wet, line.wetAmount());
      if (line.wetAmount() == null) b.missingWet++;
    }
    for (InvoiceMetadataView header : headers) {
      if (inRange(header.invoiceDate(), from, to) && !withLines.contains(header.invoiceId()))
        buckets.get(header.supplierId()).missingLines++;
    }
    List<SupplierCogs> result = new ArrayList<>();
    buckets.forEach(
        (id, b) -> {
          List<String> notices = new ArrayList<>();
          notices.add(
              "Incomplete: known ex-tax invoice-line purchases only; source coverage is unverified");
          if (b.missingLines > 0)
            notices.add(
                b.missingLines
                    + " invoices have no lines in this period; header totals are shown separately");
          if (b.missingWet > 0)
            notices.add(
                "WET incomplete: "
                    + b.missingWet
                    + " lines have unknown WET; shown amount is a known subtotal");
          if (id == null)
            notices.add("Supplier relationship unresolved; no number-only attribution applied");
          result.add(
              new SupplierCogs(
                  b.name, b.total, b.wet, id, b.invoices, b.missingLines, List.copyOf(notices)));
        });
    result.sort(
        Comparator.comparing(
            SupplierCogs::lineTotal, Comparator.nullsLast(Comparator.reverseOrder())));
    return result;
  }

  private static BigDecimal add(BigDecimal a, BigDecimal b) {
    return b == null ? a : a == null ? b : a.add(b);
  }

  private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
    return date != null && !date.isBefore(from) && !date.isAfter(to);
  }

  private static final class Bucket {
    final String name;
    int invoices, missingLines, missingWet;
    BigDecimal total, wet;

    Bucket(String name) {
      this.name = name;
    }
  }
}
