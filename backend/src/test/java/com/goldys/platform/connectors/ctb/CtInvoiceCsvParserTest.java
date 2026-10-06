package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CtInvoiceCsvParserTest {

  private final CtInvoiceCsvParser parser = new CtInvoiceCsvParser();

  @Test
  void parsesInvoiceMetadataRows() {
    byte[] csv =
        ("Supplier,Invoice Number,Invoice Date,Due Date,Total\n"
                + "Bidfood,INV-1001,2026-09-20,2026-10-04,1200.50\n"
                + "PFD,INV-1002,2026-09-21,,340.00\n")
            .getBytes(StandardCharsets.UTF_8);

    List<CtInvoice> invoices = parser.parse(csv);

    assertThat(invoices).hasSize(2);
    assertThat(invoices.get(0).supplierName()).isEqualTo("Bidfood");
    assertThat(invoices.get(0).invoiceNumber()).isEqualTo("INV-1001");
    assertThat(invoices.get(0).invoiceDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    assertThat(invoices.get(0).totalAmount()).isEqualByComparingTo(new BigDecimal("1200.50"));
    assertThat(invoices.get(1).dueDate()).isNull();
  }

  @Test
  void rejectsAMissingColumn() {
    byte[] csv = "Supplier,Invoice Date,Total\nBidfood,2026-09-20,100\n".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Invoice Number");
  }
}
