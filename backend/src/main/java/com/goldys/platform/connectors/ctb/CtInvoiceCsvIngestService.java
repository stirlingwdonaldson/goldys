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
  private final InvoiceIngestFlagService flags;

  public CtInvoiceCsvIngestService(
      IngestionService ingestion,
      CtInvoiceCsvParser parser,
      CanonicalInvoiceIngest canonical,
      CanonicalInvoiceLineIngest lineCanonical,
      InvoiceIngestFlagService flags) {
    this.ingestion = ingestion;
    this.parser = parser;
    this.canonical = canonical;
    this.lineCanonical = lineCanonical;
    this.flags = flags;
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
              firstPdfFilename(invoice),
              rawId));
      flagPdfAnomalies(invoice);

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
                line.stockCode(),
                quantity(line.rawQuantity()),
                line.unitCostExTax() == null ? BigDecimal.ZERO : line.unitCostExTax(),
                line.lineTotalExTax(),
                null, // category — PDF-only, not in the CSV
                null, // uom — PDF enrichment
                null, // unitQuantity — PDF enrichment
                null, // packSize — PDF enrichment
                null, // wetAmount — PDF enrichment
                rawId));
      }
    }
  }

  /** The invoice's PDF filename (first-seen), or null when the CSV references none. */
  private static String firstPdfFilename(CtInvoice invoice) {
    return invoice.pdfFilenames().isEmpty() ? null : invoice.pdfFilenames().get(0);
  }

  /**
   * Surfaces PDF anomalies the CSV reveals: no PDF filename at all (missing-pdf), or more than one
   * distinct filename for one invoice (ambiguous-pdf) — never a silent drop.
   */
  private void flagPdfAnomalies(CtInvoice invoice) {
    if (invoice.pdfFilenames().isEmpty()) {
      flags.flag(
          InvoiceIngestFlagType.MISSING_PDF,
          invoice.invoiceNumber(),
          null,
          null,
          "no PDF filename in CSV");
    } else if (invoice.pdfFilenames().size() > 1) {
      flags.flag(
          InvoiceIngestFlagType.AMBIGUOUS_PDF,
          invoice.invoiceNumber(),
          null,
          null,
          "multiple PDFs: " + String.join(", ", invoice.pdfFilenames()));
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
