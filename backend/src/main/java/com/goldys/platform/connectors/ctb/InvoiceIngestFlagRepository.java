package com.goldys.platform.connectors.ctb;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InvoiceIngestFlagRepository extends JpaRepository<InvoiceIngestFlag, UUID> {

  boolean existsByFlagTypeAndInvoiceNumberAndResolvedAtIsNull(
      InvoiceIngestFlagType flagType, String invoiceNumber);

  /** All flags, most recent first, for the read API. */
  List<InvoiceIngestFlag> findAllByOrderByOccurredAtDesc();

  /** Flags still awaiting resolution, most recent first. */
  List<InvoiceIngestFlag> findByResolvedAtIsNullOrderByOccurredAtDesc();

  /** Open flags of one type, for the replay reconciliation pass. */
  List<InvoiceIngestFlag> findByFlagTypeAndResolvedAtIsNull(InvoiceIngestFlagType flagType);

  /**
   * True when an equivalent flag already exists, so a re-pull does not pile up duplicates. Each
   * nullable identity column is matched with explicit {@code IS NULL} handling — a derived {@code
   * findBy...} query would silently fail to match nulls ({@code x = NULL} is never true).
   *
   * <p>The identity keys on {@code detail} (the line description / message), not {@code stockCode}:
   * the PDF extractor produces an unstable stock code across runs (synthetic placeholders, LLM
   * variants, or none), so a stock-code key re-flagged the same line repeatedly. {@code stockCode}
   * is stored as an attribute, not part of the identity.
   */
  @Query(
      "select count(f) > 0 from InvoiceIngestFlag f where f.flagType = :type "
          + "and (:invoiceNumber is null and f.invoiceNumber is null "
          + "or f.invoiceNumber = :invoiceNumber) "
          + "and (:pdfFilename is null and f.pdfFilename is null "
          + "or f.pdfFilename = :pdfFilename) "
          + "and (:detail is null and f.detail is null "
          + "or f.detail = :detail)")
  boolean existsEquivalent(
      @Param("type") InvoiceIngestFlagType type,
      @Param("invoiceNumber") String invoiceNumber,
      @Param("pdfFilename") String pdfFilename,
      @Param("detail") String detail);
}
