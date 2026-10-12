package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalRecipeIngredientRepository
    extends BitemporalRepository<CanonicalRecipeIngredient> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select i from CanonicalRecipeIngredient i where i.sourceRecordRef = :ref "
          + "and i.sourceSystem = :source and i.supersededAt is null")
  Optional<CanonicalRecipeIngredient> lockCurrentSourceFact(String source, String ref);

  @Query("select i from CanonicalRecipeIngredient i where i.supersededAt is null")
  List<CanonicalRecipeIngredient> findAllCurrent();
}
