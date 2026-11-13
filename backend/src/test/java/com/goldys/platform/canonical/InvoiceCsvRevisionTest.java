package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceCsvRevisionTest {
  @Test
  void matchesCorrectionByExactProductAndHeaderDeltaEvenWhenDiscountBasisIsUnknown() {
    var old = line("old", "adjustment", "0.11");
    var updated = input("new", "adjustment", "0.12");
    assertThat(
            InvoiceCsvRevision.match(
                List.of(old), List.of(updated), false, bd("2300.00"), bd("2300.01")))
        .containsEntry(updated, old);
  }

  @Test
  void retainedOmissionsAreNotMistakenForRemovedLines() {
    var retained = line("retained", "retained", "100");
    var old = line("old", "changed", "10");
    var updated = input("new", "changed", "12");
    assertThat(
            InvoiceCsvRevision.match(
                List.of(retained, old), List.of(updated), false, bd("110"), bd("112")))
        .containsOnlyKeys(updated)
        .containsEntry(updated, old);
  }

  @Test
  void ambiguousProductOccurrencesCannotBePairedEvenWhenTotalsBalance() {
    var first = line("first", "same product", "10");
    var second = line("second", "same product", "20");
    assertThatThrownBy(
            () ->
                InvoiceCsvRevision.match(
                    List.of(first, second),
                    List.of(input("new", "same product", "31")),
                    false,
                    bd("30"),
                    bd("31")))
        .isInstanceOf(InvoiceRevisionWithheldException.class);
  }

  @Test
  void multipleChangedOccurrencesCannotCompeteForOneExistingLine() {
    assertThatThrownBy(
            () ->
                InvoiceCsvRevision.match(
                    List.of(line("old", "same", "10")),
                    List.of(input("a", "same", "11"), input("b", "same", "12")),
                    false,
                    bd("10"),
                    bd("23")))
        .isInstanceOf(InvoiceRevisionWithheldException.class);
  }

  @Test
  void unknownHeaderCannotEstablishARevision() {
    assertThatThrownBy(
            () ->
                InvoiceCsvRevision.match(
                    List.of(line("old", "same", "10")),
                    List.of(input("new", "same", "11")),
                    false,
                    null,
                    bd("11")))
        .isInstanceOf(InvoiceRevisionWithheldException.class);
  }

  @Test
  void marginScalesWithChangedAmountsRatherThanInvoiceValue() {
    var old = line("old", "same", "100000");
    var updated = input("new", "same", "100001");
    assertThat(
            InvoiceCsvRevision.match(
                List.of(old), List.of(updated), false, bd("100000"), bd("100001.02")))
        .containsEntry(updated, old);
    assertThatThrownBy(
            () ->
                InvoiceCsvRevision.match(
                    List.of(old), List.of(updated), false, bd("100000"), bd("100001.03")))
        .isInstanceOf(InvoiceRevisionWithheldException.class);
  }

  @Test
  void signedCreditCorrectionsUseTheSameCheck() {
    var old = line("old", "credit", "-10");
    var updated = input("new", "credit", "-12");
    assertThat(
            InvoiceCsvRevision.match(List.of(old), List.of(updated), false, bd("-10"), bd("-12")))
        .containsEntry(updated, old);
  }

  @Test
  void newNonzeroLineNeedsACorrespondingHeaderChange() {
    assertThatThrownBy(
            () ->
                InvoiceCsvRevision.match(
                    List.of(line("old", "same", "10")),
                    List.of(input("old", "same", "10"), input("added", "extra", "1")),
                    false,
                    bd("10"),
                    bd("10")))
        .isInstanceOf(InvoiceRevisionWithheldException.class);
  }

  @Test
  void zeroValueAdditionAndIdenticalReplaysDoNotNeedAReplacementPolicy() {
    var old = line("old", "same", "10");
    var unchanged = input("old", "same", "10");
    var extra = input("extra", "extra", "0");
    assertThat(
            InvoiceCsvRevision.match(
                List.of(old), List.of(unchanged, extra), false, bd("10"), bd("10")))
        .containsOnlyKeys(unchanged);
    assertThat(InvoiceCsvRevision.match(List.of(old), List.of(unchanged), false, null, null))
        .containsEntry(unchanged, old);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static InvoiceLineInput input(String ref, String product, String amount) {
    return new InvoiceLineInput(
        "CTB",
        ref,
        "INV",
        null,
        LocalDate.of(2026, 10, 28),
        product,
        null,
        BigDecimal.ONE,
        bd(amount),
        bd(amount),
        null,
        null,
        null,
        null,
        null,
        UUID.randomUUID(),
        "CSV");
  }

  private static CanonicalInvoiceLine line(String ref, String product, String amount) {
    var input = input(ref, product, amount);
    return CanonicalInvoiceLine.create(
        UUID.randomUUID(),
        "CTB",
        ref,
        input.rawRecordId(),
        Instant.EPOCH,
        Instant.EPOCH,
        "INV",
        null,
        input.invoiceDate(),
        product,
        null,
        input.quantity(),
        input.unitCost(),
        input.lineTotal(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "CSV");
  }
}
