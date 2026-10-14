package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceIngest;
import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.InvoiceIdentity;
import com.goldys.platform.canonical.InvoiceInput;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.StockItemIngest;
import com.goldys.platform.canonical.StockItemInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.IngestionStageKind;
import com.goldys.platform.ingestion.IngestionStageOutcome;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for CTB supplier invoices: persist the dropped CSV byte-faithfully, then
 * canonicalize the invoice header and each line item. Line items come from the CSV itself (the
 * export carries both header and line columns), so COGS is available without the PDF path.
 *
 * <p>Line identity is content-based, not ordinal: each line's source reference is derived from its
 * content (stock code, description, quantity, unit cost, line total), so a re-ordered export keeps
 * line identities stable and a re-ingested export is idempotent. After recording, any current
 * CSV-sourced line whose content is no longer present is superseded, so a shortened export does not
 * leave stale lines counting toward COGS.
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
  private final StockItemIngest stockItems;
  private final InvoiceIngestFlagService flags;
  private final CanonicalInvoiceQuery invoiceQuery;
  private final CanonicalInventoryQuery inventoryQuery;

  public CtInvoiceCsvIngestService(
      IngestionService ingestion,
      CtInvoiceCsvParser parser,
      CanonicalInvoiceIngest canonical,
      CanonicalInvoiceLineIngest lineCanonical,
      StockItemIngest stockItems,
      InvoiceIngestFlagService flags,
      CanonicalInvoiceQuery invoiceQuery,
      CanonicalInventoryQuery inventoryQuery) {
    this.ingestion = ingestion;
    this.parser = parser;
    this.canonical = canonical;
    this.lineCanonical = lineCanonical;
    this.stockItems = stockItems;
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
        InvoiceIdentity identity =
            canonical.record(
                new InvoiceInput(
                    "CTB",
                    invoice.invoiceNumber(),
                    invoice.supplierName(),
                    invoice.supplierCode(),
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

        recordLines(invoice, identity, rawId);
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

  /** Records one invoice's lines with content-based identity, then closes any removed CSV lines. */
  private void recordLines(CtInvoice invoice, InvoiceIdentity identity, UUID rawId) {
    String invoiceScopedRef = identity.sourceRecordRef();
    UUID invoiceLogicalId = identity.logicalEntityId();

    Map<String, Integer> signatureCounts = new HashMap<>();
    Set<String> lineSourceRefs = new LinkedHashSet<>();
    for (CtInvoiceLine line : invoice.lines()) {
      String productNameKey = ProductNameKey.normalize(line.description());
      String stockCode = line.stockCode();
      BigDecimal quantity = quantity(line.rawQuantity());
      BigDecimal unitCost = line.unitCostExTax() == null ? BigDecimal.ZERO : line.unitCostExTax();
      BigDecimal lineTotal = line.lineTotalExTax();

      String signature = lineSignature(stockCode, productNameKey, quantity, unitCost, lineTotal);
      // A repeated identical line within one invoice gets a discriminator so exact duplicates stay
      // distinct (never silently merged); unique lines keep a clean, position-free reference.
      int occurrence = signatureCounts.merge(signature, 1, Integer::sum);
      String lineRef =
          invoiceScopedRef + ":" + signature + (occurrence > 1 ? "#" + occurrence : "");
      lineSourceRefs.add(lineRef);

      lineCanonical.record(
          new InvoiceLineInput(
              "CTB",
              lineRef,
              invoice.invoiceNumber(),
              invoiceLogicalId,
              invoice.invoiceDate(),
              productNameKey,
              stockCode,
              quantity,
              unitCost,
              lineTotal,
              null, // category — PDF-only, not in the CSV
              null, // uom — PDF enrichment
              null, // unitQuantity — PDF enrichment
              null, // packSize — PDF enrichment
              null, // wetAmount — PDF enrichment
              rawId,
              "CSV"));
      recordStockItem(identity, stockCode, productNameKey, rawId);
    }

    // Close any current CSV line this export no longer carries (a shortened/changed export).
    lineCanonical.convergeCsvLines(invoiceLogicalId, lineSourceRefs);
  }

  /** Records the supplier-scoped stock item a line references (a purchased ingredient). */
  private void recordStockItem(
      InvoiceIdentity identity, String stockCode, String productNameKey, UUID rawId) {
    if (stockCode == null || stockCode.isBlank()) {
      return;
    }
    stockItems.record(
        new StockItemInput(
            "CTB",
            identity.supplierSourceRef() + "::" + stockCode,
            identity.supplierLogicalId(),
            stockCode,
            productNameKey,
            null,
            rawId));
  }

  /**
   * A content signature for one line over its canonical fields, so identity is independent of line
   * order. Uses the same values {@code sameFact} compares; for identical CSV content the signature
   * is identical, making re-ingest idempotent.
   */
  private static String lineSignature(
      String stockCode,
      String productNameKey,
      BigDecimal quantity,
      BigDecimal unitCost,
      BigDecimal lineTotal) {
    String code = stockCode == null ? "" : stockCode.trim();
    String name = productNameKey == null ? "" : productNameKey;
    String q = quantity == null ? "" : quantity.toPlainString();
    String cost = unitCost == null ? "" : unitCost.toPlainString();
    String total = lineTotal == null ? "" : lineTotal.toPlainString();
    return sha256Hex(
        (code + "|" + name + "|" + q + "|" + cost + "|" + total).getBytes(StandardCharsets.UTF_8));
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

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
