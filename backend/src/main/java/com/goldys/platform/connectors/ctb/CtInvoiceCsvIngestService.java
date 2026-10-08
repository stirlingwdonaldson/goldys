package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceIngest;
import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.InvoiceInput;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for CTB supplier invoices: persist the dropped CSV byte-faithfully, then
 * canonicalize the invoice header and each line item. Line items come from the CSV itself (the
 * export carries both header and line columns), so COGS is available without the PDF path.
 */
@Service
public class CtInvoiceCsvIngestService {
  private final IngestionService ingestion;
  private final CtInvoiceCsvParser parser;
  private final CanonicalInvoiceIngest canonical;
  private final CanonicalInvoiceLineIngest lineCanonical;

  public CtInvoiceCsvIngestService(
      IngestionService ingestion,
      CtInvoiceCsvParser parser,
      CanonicalInvoiceIngest canonical,
      CanonicalInvoiceLineIngest lineCanonical) {
    this.ingestion = ingestion;
    this.parser = parser;
    this.canonical = canonical;
    this.lineCanonical = lineCanonical;
  }

  public void ingest(byte[] csv) {
    UUID rawId =
        ingestion.ingestPush(
            "CTB",
            "ctb-invoices",
            FetchMethod.FILE_EXPORT,
            "text/csv",
            csv,
            StandardCharsets.UTF_8.name(),
            "ctb-invoices");

    for (CtInvoice invoice : parser.parse(csv)) {
      canonical.record(
          new InvoiceInput(
              "CTB",
              invoice.invoiceNumber(),
              invoice.supplierName(),
              invoice.invoiceDate(),
              invoice.dueDate(),
              invoice.incTaxAmount(),
              invoice.purchaseNumber(),
              null, // account number — not in the current export
              null, // tax code — not in the current export
              invoice.amountExTax(),
              invoice.gstAmount(),
              invoice.freightAmount(),
              null, // freight GST — not a direct column in the current export
              rawId));

      int seq = 0;
      for (CtInvoiceLine line : invoice.lines()) {
        seq++;
        lineCanonical.record(
            new InvoiceLineInput(
                "CTB",
                invoice.invoiceNumber() + ":" + seq,
                invoice.invoiceNumber(),
                invoice.invoiceDate(),
                ProductNameKey.normalize(line.description()),
                quantity(line.rawQuantity()),
                line.unitCostExTax() == null ? BigDecimal.ZERO : line.unitCostExTax(),
                line.lineTotalExTax(),
                null, // category — PDF-only, not in the CSV
                rawId));
      }
    }
  }

  /** Best-effort quantity: the first number in the coarse LineQuantity; 1 when unparseable. */
  private static BigDecimal quantity(String rawQuantity) {
    if (rawQuantity != null) {
      for (String token : rawQuantity.trim().split("\\s+")) {
        try {
          return new BigDecimal(token);
        } catch (NumberFormatException ignored) {
          // keep scanning for the first number
        }
      }
    }
    return BigDecimal.ONE;
  }
}
