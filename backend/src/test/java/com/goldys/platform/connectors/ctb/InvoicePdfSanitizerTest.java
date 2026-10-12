package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InvoicePdfSanitizerTest {

  @Test
  void keepsRealInvoiceNumbers() {
    assertThat(InvoicePdfSanitizer.invoiceNumber("758262")).isEqualTo("758262");
    assertThat(InvoicePdfSanitizer.invoiceNumber(" NU3379 ")).isEqualTo("NU3379");
    assertThat(InvoicePdfSanitizer.invoiceNumber("053202347")).isEqualTo("053202347");
    assertThat(InvoicePdfSanitizer.invoiceNumber("INV-1")).isEqualTo("INV-1");
  }

  @Test
  void rejectsAHeaderLabelReturnedAsTheInvoiceNumber() {
    assertThat(InvoicePdfSanitizer.invoiceNumber("Tax Invoice No")).isNull();
    assertThat(InvoicePdfSanitizer.invoiceNumber("Invoice Number:")).isNull();
    assertThat(InvoicePdfSanitizer.invoiceNumber("   ")).isNull();
    assertThat(InvoicePdfSanitizer.invoiceNumber(null)).isNull();
  }

  @Test
  void stripsALeadingLabelFromARealNumber() {
    assertThat(InvoicePdfSanitizer.invoiceNumber("Tax Invoice No: 351289")).isEqualTo("351289");
    assertThat(InvoicePdfSanitizer.invoiceNumber("Invoice # 759352")).isEqualTo("759352");
  }

  @Test
  void treatsNotesWithoutACodeOrAmountAsAnnotations() {
    assertThat(InvoicePdfSanitizer.isAnnotation(line(null, "(2 BOXES)", new BigDecimal("2"))))
        .isTrue();
    assertThat(InvoicePdfSanitizer.isAnnotation(line(null, "COMMENT", null))).isTrue();
    assertThat(InvoicePdfSanitizer.isAnnotation(line(null, "Delivery after 10am", null))).isTrue();
  }

  @Test
  void neverDropsALineWithACodeAnAmountOrAQuantity() {
    assertThat(InvoicePdfSanitizer.isAnnotation(line("B12BG", "(Brush Grill)", null))).isFalse();
    assertThat(
            InvoicePdfSanitizer.isAnnotation(
                new PdfExtractedLine(
                    null, "Fuel Levy", null, null, null, null, null, new BigDecimal("4.50"))))
        .isFalse();
    assertThat(InvoicePdfSanitizer.isAnnotation(line(null, "Oysters", new BigDecimal("3"))))
        .isFalse();
  }

  @Test
  void flagTextAnnotationsNeedNoCodeAndANoteLikeDescription() {
    assertThat(InvoicePdfSanitizer.isAnnotationText(null, "(pick up saturday 1 box)")).isTrue();
    assertThat(InvoicePdfSanitizer.isAnnotationText("", "Notes:")).isTrue();
    assertThat(InvoicePdfSanitizer.isAnnotationText(null, "Brush Grill Medium")).isFalse();
    assertThat(InvoicePdfSanitizer.isAnnotationText("CODE", "COMMENT")).isFalse();
  }

  private static PdfExtractedLine line(String code, String description, BigDecimal quantity) {
    return new PdfExtractedLine(code, description, quantity, null, null, null, null, null);
  }
}
