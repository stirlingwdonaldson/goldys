package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalRecipeCatalogRepository extends BitemporalRepository<CanonicalRecipeCatalog> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from CanonicalRecipeCatalog r where r.sourceSystem='CTB' and r.sourceRecordRef=:ref and r.supersededAt is null")
  Optional<CanonicalRecipeCatalog> lockCurrent(String ref);

  @Query("select r from CanonicalRecipeCatalog r where r.supersededAt is null")
  List<CanonicalRecipeCatalog> findAllCurrent();
}
