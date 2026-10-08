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
        ("Co./Last Name,Purchase#,Date,Supplier Invoice #,Account #,Amount,Tax Code,GST Amount,"
                + "Freight Amount,Freight GST Amount,Inc-Tax Amount\n"
                + "Bidfood,P-1001,2026-09-20,INV-1001,ACC-7,\"$1,091.00\",GST,$99.18,\"$25.00\","
                + "$2.50,\"$1,217.68\"\n"
                + "PFD,P-1002,2026-09-21,INV-1002,,340.00,,30.90,,,370.90\n")
            .getBytes(StandardCharsets.UTF_8);

    List<CtInvoice> invoices = parser.parse(csv);

    assertThat(invoices).hasSize(2);

    CtInvoice first = invoices.get(0);
    assertThat(first.supplierName()).isEqualTo("Bidfood");
    assertThat(first.purchaseNumber()).isEqualTo("P-1001");
    assertThat(first.invoiceDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    assertThat(first.invoiceNumber()).isEqualTo("INV-1001");
    assertThat(first.accountNumber()).isEqualTo("ACC-7");
    assertThat(first.amountExTax()).isEqualByComparingTo(new BigDecimal("1091.00"));
    assertThat(first.taxCode()).isEqualTo("GST");
    assertThat(first.gstAmount()).isEqualByComparingTo(new BigDecimal("99.18"));
    assertThat(first.freightAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
    assertThat(first.freightGstAmount()).isEqualByComparingTo(new BigDecimal("2.50"));
    assertThat(first.incTaxAmount()).isEqualByComparingTo(new BigDecimal("1217.68"));

    CtInvoice second = invoices.get(1);
    assertThat(second.accountNumber()).isNull();
    assertThat(second.taxCode()).isNull();
    assertThat(second.freightAmount()).isNull();
    assertThat(second.freightGstAmount()).isNull();
    assertThat(second.incTaxAmount()).isEqualByComparingTo(new BigDecimal("370.90"));
  }

  @Test
  void rejectsAMissingColumn() {
    byte[] csv =
        "Co./Last Name,Date,Inc-Tax Amount\nBidfood,2026-09-20,100\n"
            .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parse(csv))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Supplier Invoice #");
  }
}
