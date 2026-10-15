package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalInvoiceLineRepository extends BitemporalRepository<CanonicalInvoiceLine> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from CanonicalInvoiceLine l where l.sourceRecordRef = :ref "
          + "and l.sourceSystem = :source and l.supersededAt is null")
  Optional<CanonicalInvoiceLine> lockCurrentSourceFact(String source, String ref);

  @Query("select l from CanonicalInvoiceLine l where l.supersededAt is null")
  List<CanonicalInvoiceLine> findAllCurrent();

  /**
   * Current lines whose parent invoice reference is still unresolved — legacy rows ingested before
   * the parent-reference column existed. The one-off backfill resolves these from the invoice
   * number; orphans and ambiguous cases are left null by the caller.
   */
  @Query(
      "select l from CanonicalInvoiceLine l where l.invoiceLogicalId is null and l.supersededAt is null")
  List<CanonicalInvoiceLine> findCurrentWithNullInvoiceLogicalId();

  /** The current lines of one invoice, locked for enrichment. Row order is not significant. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from CanonicalInvoiceLine l where l.invoiceNumber = :invoiceNumber "
          + "and l.supersededAt is null")
  List<CanonicalInvoiceLine> lockCurrentByInvoiceNumber(String invoiceNumber);

  @Query(
      "select count(l) from CanonicalInvoiceLine l where l.invoiceNumber = :invoiceNumber "
          + "and l.supersededAt is null")
  long countCurrentByInvoiceNumber(String invoiceNumber);

  /** The current lines of one invoice, unlocked (used to test whether a line matches at all). */
  @Query(
      "select l from CanonicalInvoiceLine l where l.invoiceNumber = :invoiceNumber "
          + "and l.supersededAt is null")
  List<CanonicalInvoiceLine> findCurrentByInvoiceNumber(String invoiceNumber);

  /**
   * The current (non-superseded) PDF-derived lines of one invoice, so convergence can close them
   * once the CSV export catches up. {@code origin='PDF'} marks gap-recovery lines; null is a legacy
   * CSV row and is deliberately not matched.
   */
  @Query(
      "select l from CanonicalInvoiceLine l where l.invoiceNumber = :invoiceNumber "
          + "and l.origin = 'PDF' and l.supersededAt is null")
  List<CanonicalInvoiceLine> findCurrentPdfOriginByInvoiceNumber(String invoiceNumber);

  /**
   * The current (non-superseded) CSV-sourced lines of one invoice: explicit {@code origin='CSV'}
   * plus legacy rows whose {@code origin} is null. Used by convergence to compare the CSV-only line
   * total against the header without counting the still-current PDF gap lines.
   */
  @Query(
      "select l from CanonicalInvoiceLine l where l.invoiceNumber = :invoiceNumber "
          + "and l.supersededAt is null and (l.origin is null or l.origin <> 'PDF')")
  List<CanonicalInvoiceLine> findCurrentCsvByInvoiceNumber(String invoiceNumber);

  /**
   * The current CSV-sourced lines of one invoice entity (by parent logical id), used by the
   * line-convergence step to close lines a re-ingested export no longer carries.
   */
  @Query(
      "select l from CanonicalInvoiceLine l where l.invoiceLogicalId = :invoiceLogicalId "
          + "and l.supersededAt is null and (l.origin is null or l.origin <> 'PDF')")
  List<CanonicalInvoiceLine> findCurrentCsvByInvoiceLogicalId(UUID invoiceLogicalId);
}
