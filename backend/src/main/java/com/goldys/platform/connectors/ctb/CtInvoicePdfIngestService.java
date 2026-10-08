package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Invoice-PDF pipeline: persist the PDF byte-faithfully, extract its text through the swappable
 * {@link DocumentTextExtractor}, parse line items, and canonicalize each. Invoice attribution
 * (number + date) is supplied by the caller, since the PDF text layout is unconfirmed; the parser
 * extracts only line-item observations.
 */
@Service
public class CtInvoicePdfIngestService {
  private final IngestionService ingestion;
  private final DocumentTextExtractor extractor;
  private final InvoiceLineTextParser parser;
  private final CanonicalInvoiceLineIngest canonical;

  public CtInvoicePdfIngestService(
      IngestionService ingestion,
      DocumentTextExtractor extractor,
      InvoiceLineTextParser parser,
      CanonicalInvoiceLineIngest canonical) {
    this.ingestion = ingestion;
    this.extractor = extractor;
    this.parser = parser;
    this.canonical = canonical;
  }

  public void ingest(byte[] pdf, String invoiceNumber, LocalDate invoiceDate) {
    UUID rawId =
        ingestion.ingestPush(
            "CTB",
            "ctb-invoice-pdf",
            FetchMethod.FILE_EXPORT,
            "application/pdf",
            pdf,
            null,
            "ctb-invoice-pdf");

    String text = extractor.extractText(pdf, "application/pdf");
    int seq = 0;
    for (InvoiceLine line : parser.parse(text)) {
      seq++;
      canonical.record(
          new InvoiceLineInput(
              "CTB",
              invoiceNumber + ":" + seq,
              invoiceNumber,
              invoiceDate,
              line.productNameKey(),
              null, // stockCode — not extracted by the provisional PDF parser
              line.quantity(),
              line.unitCost(),
              line.lineTotal(),
              null, // category
              null, // uom
              null, // unitQuantity
              null, // packSize
              null, // wetAmount
              rawId));
    }
  }
}
