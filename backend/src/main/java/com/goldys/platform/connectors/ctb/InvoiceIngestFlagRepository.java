package com.goldys.platform.connectors.ctb;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InvoiceIngestFlagRepository extends JpaRepository<InvoiceIngestFlag, UUID> {

  /** All flags, most recent first, for the read API. */
  List<InvoiceIngestFlag> findAllByOrderByOccurredAtDesc();

  /**
   * True when an equivalent flag already exists, so a re-pull does not pile up duplicates. Each
   * nullable identity column is matched with explicit {@code IS NULL} handling — a derived {@code
   * findBy...} query would silently fail to match nulls ({@code x = NULL} is never true).
   */
  @Query(
      "select count(f) > 0 from InvoiceIngestFlag f where f.flagType = :type "
          + "and (:invoiceNumber is null and f.invoiceNumber is null "
          + "or f.invoiceNumber = :invoiceNumber) "
          + "and (:pdfFilename is null and f.pdfFilename is null "
          + "or f.pdfFilename = :pdfFilename) "
          + "and (:stockCode is null and f.stockCode is null "
          + "or f.stockCode = :stockCode)")
  boolean existsEquivalent(
      @Param("type") InvoiceIngestFlagType type,
      @Param("invoiceNumber") String invoiceNumber,
      @Param("pdfFilename") String pdfFilename,
      @Param("stockCode") String stockCode);
}
