package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DeterministicInvoicePdfExtractorTest {

  private final DeterministicInvoicePdfExtractor extractor = new DeterministicInvoicePdfExtractor();

  private static final String TEXT =
      "Tax Invoice\n"
          + "Invoice Number\n"
          + "F58991755\n"
          + "QTY\nCODE\nDESCRIPTION\nUNIT\nUNIT PRICE\n"
          + "3.25\nBEEF025\nBEEF RUMP CAP\nKG\n31.50\n"
          + "1\nHAMB008\nBEEF BURGER 150GM\nEACH\n5.00\n"
          + "Total\n117.00\n";

  @Test
  void extractsTheLineTable() {
    PdfExtractedInvoice out = extractor.extract(TEXT);

    assertThat(out.invoiceNumber()).isEqualTo("F58991755");
    assertThat(out.lines()).hasSize(2);
    assertThat(out.lines().get(0).stockCode()).isEqualTo("BEEF025");
    assertThat(out.lines().get(0).description()).isEqualTo("BEEF RUMP CAP");
    assertThat(out.lines().get(0).quantity()).isEqualByComparingTo(new BigDecimal("3.25"));
    assertThat(out.lines().get(0).uom()).isEqualTo("KG");
  }

  @Test
  void returnsEmptyForUnrecognizedText() {
    assertThat(extractor.extract("No table here\nJust words\n").lines()).isEmpty();
  }

  @Test
  void keepsHyphenatedInvoiceNumbersIntact() {
    PdfExtractedInvoice out =
        extractor.extract(
            "Invoice Number\nSI-00008962\n"
                + "QTY\nCODE\nDESCRIPTION\nUNIT\nUNIT PRICE\n"
                + "1\nBEEF\nBEEF\nKG\n5.00\n");

    assertThat(out.invoiceNumber()).isEqualTo("SI-00008962");
  }
}
