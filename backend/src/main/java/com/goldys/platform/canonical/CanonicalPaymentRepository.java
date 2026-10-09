package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalPaymentRepository extends BitemporalRepository<CanonicalPayment> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from CanonicalPayment p where p.sourceRecordRef = :ref "
          + "and p.sourceSystem = :source and p.supersededAt is null")
  Optional<CanonicalPayment> lockCurrentPayment(String ref, String source);

  @Query("select p from CanonicalPayment p where p.supersededAt is null")
  List<CanonicalPayment> findAllCurrent();
}
