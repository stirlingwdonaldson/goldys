package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalRecipeDefinitionRepository
    extends BitemporalRepository<CanonicalRecipeDefinition> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from CanonicalRecipeDefinition r where r.sourceSystem='CTB' and r.sourceRecordRef=:ref and r.supersededAt is null")
  Optional<CanonicalRecipeDefinition> lockCurrent(String ref);

  @Query(
      "select r from CanonicalRecipeDefinition r where r.sourceSystem='CTB' and r.sourceRecordRef=:ref and r.supersededAt is null")
  Optional<CanonicalRecipeDefinition> findCurrent(String ref);

  @Query("select r from CanonicalRecipeDefinition r where r.supersededAt is null")
  List<CanonicalRecipeDefinition> findAllCurrent();
}
