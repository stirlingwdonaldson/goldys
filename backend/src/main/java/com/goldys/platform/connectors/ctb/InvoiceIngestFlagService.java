package com.goldys.platform.connectors.ctb;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records invoice-ingestion flags append-only. Recording is idempotent on the flag's identity (type
 * + invoice number + PDF filename + stock code), so a re-pull of the same drop does not pile up
 * duplicate rows while every distinct anomaly remains a queryable record.
 */
@Service
public class InvoiceIngestFlagService {

  private static final Clock CLOCK = Clock.systemUTC();

  private final InvoiceIngestFlagRepository repository;

  public InvoiceIngestFlagService(InvoiceIngestFlagRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void flag(
      InvoiceIngestFlagType type,
      String invoiceNumber,
      String pdfFilename,
      String stockCode,
      String detail) {
    if (repository.existsEquivalent(type, invoiceNumber, pdfFilename, stockCode)) {
      return;
    }
    repository.save(
        InvoiceIngestFlag.create(
            type, invoiceNumber, pdfFilename, stockCode, detail, Instant.now(CLOCK)));
  }
}
