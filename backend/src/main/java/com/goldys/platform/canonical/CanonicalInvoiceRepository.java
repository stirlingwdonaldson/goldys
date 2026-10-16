package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalInvoiceRepository extends BitemporalRepository<CanonicalInvoice> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select count(distinct i.logicalEntityId) from CanonicalInvoice i where i.pdfFilename=:filename and i.supersededAt is null")
  long currentInvoiceCountForPdfFilename(String filename);

  @Query(
      "select i from CanonicalInvoice i where i.sourceRecordRef = :ref "
          + "and i.sourceSystem = :source and i.supersededAt is null")
  Optional<CanonicalInvoice> lockCurrentSourceFact(String source, String ref);

  @Query(
      "select i.invoiceNumber from CanonicalInvoice i "
          + "where i.pdfFilename = :pdfFilename and i.supersededAt is null")
  Optional<String> currentInvoiceNumberByPdfFilename(String pdfFilename);

  @Query(
      "select i.pdfFilename from CanonicalInvoice i "
          + "where i.invoiceNumber = :invoiceNumber and i.supersededAt is null")
  List<String> currentPdfFilenamesByInvoiceNumber(String invoiceNumber);

  /**
   * The current ex-tax header amounts for an invoice. Ordered by {@code recordedAt desc} so the
   * facade's first-present value is deterministic: an invoice number can in principle have several
   * current rows, and an unordered first match would make the resolved header depend on row order.
   */
  @Query(
      "select i.amountExTax from CanonicalInvoice i "
          + "where i.invoiceNumber = :invoiceNumber and i.supersededAt is null "
          + "order by i.recordedAt desc")
  List<BigDecimal> currentAmountsExTaxByInvoiceNumber(String invoiceNumber);

  @Query(
      "select count(i) from CanonicalInvoice i "
          + "where i.invoiceNumber = :invoiceNumber and i.supersededAt is null")
  long countCurrentByInvoiceNumber(String invoiceNumber);

  /**
   * The current invoice's stable logical id for an invoice number, newest-recorded first. An
   * invoice number can in principle have several current rows (supplier-scoped identity is still
   * being rolled out), so ordering makes the first-present value deterministic.
   */
  @Query(
      "select i.logicalEntityId from CanonicalInvoice i "
          + "where i.invoiceNumber = :invoiceNumber and i.supersededAt is null "
          + "order by i.recordedAt desc")
  List<UUID> currentLogicalIdsByInvoiceNumber(String invoiceNumber);

  /**
   * All current invoices, newest-recorded first. The ordering makes the facade's first-match
   * lookups (e.g. the invoice-date resolution used by gap recovery) deterministic.
   */
  @Query("select i from CanonicalInvoice i where i.supersededAt is null order by i.recordedAt desc")
  List<CanonicalInvoice> findAllCurrent();

  @Query(
      "select distinct i.logicalEntityId from CanonicalInvoice i where i.invoiceNumber = :number")
  List<UUID> allLogicalIdsByInvoiceNumber(String number);

  @Query(
      "select i from CanonicalInvoice i where i.logicalEntityId = :id and i.supersededAt is null")
  Optional<CanonicalInvoice> currentByLogicalId(UUID id);
}
