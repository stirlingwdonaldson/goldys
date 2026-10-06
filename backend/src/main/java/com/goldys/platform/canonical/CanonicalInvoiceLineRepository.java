package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
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
}
