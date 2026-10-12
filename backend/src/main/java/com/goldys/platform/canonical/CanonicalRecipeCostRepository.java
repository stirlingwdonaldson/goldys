package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalRecipeCostRepository extends BitemporalRepository<CanonicalRecipeCost> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from CanonicalRecipeCost r where r.sourceRecordRef = :ref "
          + "and r.sourceSystem = :source and r.supersededAt is null")
  Optional<CanonicalRecipeCost> lockCurrentRecipeCost(String ref, String source);

  @Query("select r from CanonicalRecipeCost r where r.supersededAt is null")
  List<CanonicalRecipeCost> findAllCurrent();
}
