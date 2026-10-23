package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

interface CanonicalRecipeSectionRepository extends BitemporalRepository<CanonicalRecipeSection> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from CanonicalRecipeSection r where r.sourceRecordRef=:ref and r.supersededAt is null")
  Optional<CanonicalRecipeSection> lockCurrent(String ref);

  @Query("select r from CanonicalRecipeSection r where r.supersededAt is null")
  List<CanonicalRecipeSection> findAllCurrent();

  @Query(
      "select r from CanonicalRecipeSection r where r.recipeId=:recipeId and r.supersededAt is null")
  List<CanonicalRecipeSection> currentFor(int recipeId);
}
