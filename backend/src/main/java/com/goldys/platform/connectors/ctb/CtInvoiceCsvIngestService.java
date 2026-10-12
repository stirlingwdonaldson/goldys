package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceIngest;
import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.InvoiceInput;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.IngestionStageKind;
import com.goldys.platform.ingestion.IngestionStageOutcome;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for CTB supplier invoices: persist the dropped CSV byte-faithfully, then
 * canonicalize the invoice header and each line item. Line items come from the CSV itself (the
 * export carries both header and line columns), so COGS is available without the PDF path.
 */
@Service
public class CtInvoiceCsvIngestService {
  /**
   * Ex-tax reconciliation tolerance (2 cents). Must stay in step with the recovery tolerance in
   * {@link InvoiceGapRecoveryService}: convergence reuses the same "does the CSV line sum reconcile
   * with the header" test, so a drift between the two would converge on a different signal.
   */
  private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

  private final IngestionService ingestion;
  private final CtInvoiceCsvParser parser;
  private final CanonicalInvoiceIngest canonical;
  private final CanonicalInvoiceLineIngest lineCanonical;
  private final InvoiceIngestFlagService flags;
  private final CanonicalInvoiceQuery invoiceQuery;
  private final CanonicalInventoryQuery inventoryQuery;

  public CtInvoiceCsvIngestService(
      IngestionService ingestion,
      CtInvoiceCsvParser parser,
      CanonicalInvoiceIngest canonical,
      CanonicalInvoiceLineIngest lineCanonical,
      InvoiceIngestFlagService flags,
      CanonicalInvoiceQuery invoiceQuery,
      CanonicalInventoryQuery inventoryQuery) {
    this.ingestion = ingestion;
    this.parser = parser;
    this.canonical = canonical;
    this.lineCanonical = lineCanonical;
    this.flags = flags;
    this.invoiceQuery = invoiceQuery;
    this.inventoryQuery = inventoryQuery;
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

    List<CtInvoice> invoices;
    try {
      invoices = parser.parse(csv);
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.PARSED,
        invoices.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);

    try {
      for (CtInvoice invoice : invoices) {
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
                  rawId,
                  "CSV"));
        }

        convergePdfLinesAfterCsvIngest(invoice.invoiceNumber());
      }
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.CANONICALIZED,
        invoices.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);
  }

  /** The invoice's PDF filename (first-seen), or null when the CSV references none. */
  private static String firstPdfFilename(CtInvoice invoice) {
    return invoice.pdfFilenames().isEmpty() ? null : invoice.pdfFilenames().get(0);
  }

  /**
   * Convergence: once the CSV is (re)ingested, the PDF-derived lines gap recovery recorded for the
   * invoice are pruned so they stop over-counting COGS. The check runs after the header and lines
   * are recorded, so the new CSV lines are visible to the queries in the same ingest call.
   *
   * <p>Two outcomes, decided by the CSV's remaining shortfall ({@code header − CSV-only line
   * total}):
   *
   * <ul>
   *   <li>The CSV now covers the header within tolerance → supersede <em>all</em> current
   *       PDF-origin lines (the complete case).
   *   <li>The CSV is only partly fixed → keep PDF lines in a deterministic order up to the
   *       remaining shortfall and supersede the excess, flagging {@code INVOICE_EXPORT_INCOMPLETE}
   *       with a partial-catch-up detail. Without this, the still-stale PDF gap lines would keep
   *       their full amount and the invoice total would over-state COGS.
   * </ul>
   *
   * A missing header short-circuits (nothing to reconcile against). The CSV-only line total is
   * used, not the all-lines total: the PDF gap lines are still current at this point, and summing
   * them too would both mask a still-short CSV and wrongly keep stale PDF lines.
   */
  private void convergePdfLinesAfterCsvIngest(String invoiceNumber) {
    BigDecimal header = invoiceQuery.amountExTaxForInvoiceNumber(invoiceNumber).orElse(null);
    if (header == null) {
      return;
    }
    BigDecimal csvSum = inventoryQuery.currentCsvLineTotalForInvoice(invoiceNumber);
    BigDecimal remaining = header.subtract(csvSum);
    if (remaining.compareTo(TOLERANCE) <= 0) {
      lineCanonical.supersedePdfOriginLines(invoiceNumber);
      return;
    }
    int pruned = lineCanonical.prunePdfOriginLines(invoiceNumber, remaining, TOLERANCE);
    if (pruned > 0) {
      flags.flag(
          InvoiceIngestFlagType.INVOICE_EXPORT_INCOMPLETE,
          invoiceNumber,
          null,
          null,
          "partial CSV catch-up: pruned "
              + pruned
              + " PDF-derived line(s); CSV still short by "
              + remaining);
    }
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
