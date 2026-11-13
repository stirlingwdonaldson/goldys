package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Matches CSV occurrences without treating a balanced total as proof of export completeness. */
final class InvoiceCsvRevision {
  private InvoiceCsvRevision() {}

  static Map<InvoiceLineInput, CanonicalInvoiceLine> match(
      List<CanonicalInvoiceLine> existing,
      List<InvoiceLineInput> inputs,
      boolean completeSnapshot,
      BigDecimal previousHeader,
      BigDecimal header) {
    var remaining = new ArrayList<>(existing);
    Map<InvoiceLineInput, CanonicalInvoiceLine> matches = new LinkedHashMap<>();
    for (InvoiceLineInput input : inputs) {
      var match =
          remaining.stream()
              .filter(line -> line.sameCsvContent(input))
              .sorted(
                  java.util.Comparator.comparing(
                      line -> !line.sourceRecordRef().equals(input.sourceRecordRef())))
              .findFirst();
      if (match.isPresent()) {
        matches.put(input, match.get());
        remaining.remove(match.get());
      }
    }
    if (!completeSnapshot && !existing.isEmpty()) {
      reconcileChanges(inputs, remaining, matches, previousHeader, header);
    }
    return matches;
  }

  private static void reconcileChanges(
      List<InvoiceLineInput> inputs,
      List<CanonicalInvoiceLine> remaining,
      Map<InvoiceLineInput, CanonicalInvoiceLine> matches,
      BigDecimal previousHeader,
      BigDecimal header) {
    var changed = inputs.stream().filter(input -> !matches.containsKey(input)).toList();
    if (changed.isEmpty()) return;
    if (previousHeader == null || header == null) throw new InvoiceRevisionWithheldException();
    BigDecimal delta = BigDecimal.ZERO;
    int roundedAmounts =
        2; // Each header and each changed before/after line can round by half a cent.
    for (InvoiceLineInput input : changed) {
      var candidates = remaining.stream().filter(line -> sameProduct(line, input)).toList();
      if (candidates.size() > 1
          || (!candidates.isEmpty()
              && changed.stream().filter(other -> sameProduct(other, input)).count() > 1)) {
        throw new InvoiceRevisionWithheldException();
      }
      delta = delta.add(input.lineTotal());
      roundedAmounts++;
      if (!candidates.isEmpty()) {
        var previous = candidates.getFirst();
        matches.put(input, previous);
        remaining.remove(previous);
        delta = delta.subtract(previous.lineTotal());
        roundedAmounts++;
      }
    }
    // A percentage of invoice value would hide missing purchases on large invoices. Bound the
    // rounding allowance by the number of changed amounts, capped at five cents for any invoice.
    BigDecimal tolerance =
        new BigDecimal("0.005")
            .multiply(BigDecimal.valueOf(roundedAmounts))
            .min(new BigDecimal("0.05"));
    if (header.subtract(previousHeader).subtract(delta).abs().compareTo(tolerance) > 0) {
      throw new InvoiceRevisionWithheldException();
    }
  }

  private static boolean sameProduct(CanonicalInvoiceLine line, InvoiceLineInput input) {
    return Objects.equals(line.stockCode(), input.stockCode())
        && Objects.equals(line.productNameKey(), input.productNameKey());
  }

  private static boolean sameProduct(InvoiceLineInput line, InvoiceLineInput input) {
    return Objects.equals(line.stockCode(), input.stockCode())
        && Objects.equals(line.productNameKey(), input.productNameKey());
  }
}
