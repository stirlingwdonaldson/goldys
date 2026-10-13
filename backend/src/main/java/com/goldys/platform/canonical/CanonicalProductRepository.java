package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalProductRepository extends BitemporalRepository<CanonicalProduct> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from CanonicalProduct p where p.sourceRecordRef = :ref "
          + "and p.sourceSystem = :source and p.supersededAt is null")
  Optional<CanonicalProduct> lockCurrentProduct(String ref, String source);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from CanonicalProduct p where p.supersededAt is null")
  List<CanonicalProduct> lockAllCurrent();

  @Query("select p from CanonicalProduct p where p.supersededAt is null")
  List<CanonicalProduct> findAllCurrent();
}
