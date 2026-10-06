package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceIngest;
import com.goldys.platform.canonical.InvoiceInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for CTB supplier-invoice metadata: persist the dropped CSV
 * byte-faithfully, then parse and canonicalize each invoice row. Line items arrive separately via
 * the invoice PDF path.
 */
@Service
public class CtInvoiceCsvIngestService {
  private final IngestionService ingestion;
  private final CtInvoiceCsvParser parser;
  private final CanonicalInvoiceIngest canonical;

  public CtInvoiceCsvIngestService(
      IngestionService ingestion, CtInvoiceCsvParser parser, CanonicalInvoiceIngest canonical) {
    this.ingestion = ingestion;
    this.parser = parser;
    this.canonical = canonical;
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
              invoice.totalAmount(),
              rawId));
    }
  }
}
