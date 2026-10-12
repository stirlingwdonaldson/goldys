package com.goldys.platform.canonical;

import static com.goldys.platform.canonical.InvoiceExportReconciler.classify;
import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.InvoiceExportReconciler.Outcome;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of the CTB invoice-export completeness classifier: decide whether the CSV export
 * omitted lines by comparing the summed CSV/PDF lines against the invoice header total.
 */
class InvoiceExportReconcilerTest {

  private static final BigDecimal e = new BigDecimal("0.02");

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  @Test
  void noHeader() {
    assertThat(classify(null, bd("10"), bd("10"), e)).isEqualTo(Outcome.NO_HEADER);
  }

  @Test
  void csvReconciles() {
    assertThat(classify(bd("100"), bd("100"), bd("0"), e)).isEqualTo(Outcome.CSV_COMPLETE);
  }

  @Test
  void pdfReconcilesCsvShort() {
    assertThat(classify(bd("100"), bd("40"), bd("100"), e))
        .isEqualTo(Outcome.PDF_COMPLETE_CSV_SHORT);
  }

  @Test
  void neitherReconciles() {
    assertThat(classify(bd("100"), bd("40"), bd("90"), e)).isEqualTo(Outcome.UNRESOLVED);
  }

  @Test
  void withinToleranceIsComplete() {
    assertThat(classify(bd("100.00"), bd("99.99"), bd("0"), bd("0.02")))
        .isEqualTo(Outcome.CSV_COMPLETE);
  }
}
