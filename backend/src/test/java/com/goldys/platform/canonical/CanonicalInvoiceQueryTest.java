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
