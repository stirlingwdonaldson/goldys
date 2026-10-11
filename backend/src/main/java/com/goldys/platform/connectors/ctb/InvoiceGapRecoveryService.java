package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.InvoiceExportReconciler;
import com.goldys.platform.canonical.InvoiceExportReconciler.Outcome;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.canonical.InvoiceMetadataView;
import com.goldys.platform.canonical.ProductNameKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Materialises invoice lines the CTB CSV export provably omitted. When the invoice header total
 * reconciles with the PDF line sum but not the CSV line sum (a short export), the PDF lines the CSV
 * is missing are recorded as canonical {@code origin=PDF} lines so COGS is complete.
 *
 * <p>The gap-line identity is content-stable: {@code sourceRecordRef = invoiceNumber + ":pdf:" +
 * sha256(pdf) + ":" + lineIndex}, so a re-run over the same PDF re-records the same fact (and the
 * canonical layer no-ops on {@code sameFact}). The line's invoice date is resolved from the
 * canonical invoice metadata, never from the PDF text.
 */
@Service
public class InvoiceGapRecoveryService {

  /**
   * Ex-tax reconciliation tolerance (2 cents): a line-sum that is within this of the header total
   * is treated as reconciling, so trivial rounding drift does not block recovery.
   */
  private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

  private final CanonicalInvoiceQuery invoiceQuery;
  private final CanonicalInventoryQuery inventoryQuery;
  private final CanonicalInvoiceLineIngest lineIngest;
  private final InvoiceIngestFlagService flags;

  public InvoiceGapRecoveryService(
      CanonicalInvoiceQuery invoiceQuery,
      CanonicalInventoryQuery inventoryQuery,
      CanonicalInvoiceLineIngest lineIngest,
      InvoiceIngestFlagService flags) {
    this.invoiceQuery = invoiceQuery;
    this.inventoryQuery = inventoryQuery;
    this.lineIngest = lineIngest;
    this.flags = flags;
  }

  public void recover(
      String invoiceNumber,
      String pdfFilename,
      byte[] pdf,
      UUID pdfRawRecordId,
      List<PdfExtractedLine> pdfLines,
      Set<Integer> unmatchedIndexes) {
    BigDecimal header = invoiceQuery.amountExTaxForInvoiceNumber(invoiceNumber).orElse(null);
    if (header == null) {
      flags.flag(
          InvoiceIngestFlagType.INVOICE_EXPORT_INCOMPLETE,
          invoiceNumber,
          pdfFilename,
          null,
          "no header total; cannot reconcile");
      return;
    }

    BigDecimal csvSum = inventoryQuery.currentLineTotalForInvoice(invoiceNumber);
    BigDecimal pdfSum =
        pdfLines.stream()
            .map(PdfExtractedLine::amountExTax)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    Outcome outcome = InvoiceExportReconciler.classify(header, csvSum, pdfSum, TOLERANCE);
    switch (outcome) {
      case CSV_COMPLETE -> {
        // The export already reconciles; there is no gap to recover.
      }
      case PDF_COMPLETE_CSV_SHORT ->
          recordGapLines(invoiceNumber, pdf, pdfRawRecordId, pdfLines, unmatchedIndexes);
      case UNRESOLVED, NO_HEADER ->
          flags.flag(
              InvoiceIngestFlagType.INVOICE_EXPORT_INCOMPLETE,
              invoiceNumber,
              pdfFilename,
              null,
              "csv short by "
                  + header.subtract(csvSum)
                  + "; pdf sum "
                  + pdfSum
                  + " does not reconcile");
    }
  }

  private void recordGapLines(
      String invoiceNumber,
      byte[] pdf,
      UUID pdfRawRecordId,
      List<PdfExtractedLine> pdfLines,
      Set<Integer> unmatchedIndexes) {
    LocalDate invoiceDate = resolveInvoiceDate(invoiceNumber);
    String pdfSha256 = sha256Hex(pdf);
    for (int index : unmatchedIndexes) {
      PdfExtractedLine line = pdfLines.get(index);
      BigDecimal amount = line.amountExTax();
      if (amount == null) {
        // Never fabricate a zero/total line from a PDF line with no amount.
        continue;
      }
      BigDecimal quantity = line.quantity() == null ? BigDecimal.ONE : line.quantity();
      lineIngest.record(
          new InvoiceLineInput(
              "CTB",
              invoiceNumber + ":pdf:" + pdfSha256 + ":" + index,
              invoiceNumber,
              invoiceDate,
              ProductNameKey.normalize(line.description()),
              line.stockCode(),
              quantity,
              unitCostFor(amount, line.quantity()),
              amount,
              null, // category — PDF-only, not carried on the gap line
              line.uom(),
              line.unitQuantity(),
              line.packSize(),
              line.wetAmount(),
              pdfRawRecordId,
              "PDF"));
    }
  }

  /** The line's invoice date, resolved from canonical invoice metadata (never the PDF text). */
  private LocalDate resolveInvoiceDate(String invoiceNumber) {
    return invoiceQuery.currentInvoices().stream()
        .filter(view -> invoiceNumber.equals(view.invoiceNumber()))
        .map(InvoiceMetadataView::invoiceDate)
        .findFirst()
        .orElse(null);
  }

  /**
   * Unit cost is line-total over quantity; zero when the PDF line carries no (non-zero) quantity.
   */
  private static BigDecimal unitCostFor(BigDecimal amount, BigDecimal quantity) {
    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return amount.divide(quantity, 4, RoundingMode.HALF_UP);
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
