package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalInvoiceRepository extends BitemporalRepository<CanonicalInvoice> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select i from CanonicalInvoice i where i.sourceRecordRef = :ref "
          + "and i.sourceSystem = :source and i.supersededAt is null")
  Optional<CanonicalInvoice> lockCurrentSourceFact(String source, String ref);

  @Query(
      "select i.invoiceNumber from CanonicalInvoice i "
          + "where i.pdfFilename = :pdfFilename and i.supersededAt is null")
  Optional<String> currentInvoiceNumberByPdfFilename(String pdfFilename);
}
