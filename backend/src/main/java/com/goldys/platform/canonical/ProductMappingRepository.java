package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ProductMappingRepository extends BitemporalRepository<ProductMapping> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select m from ProductMapping m where m.sourceRecordRef = :ref "
          + "and m.sourceSystem = :source and m.supersededAt is null")
  Optional<ProductMapping> lockCurrentSourceFact(String source, String ref);

  /** The current (non-superseded, non-rejected) mappings for a business product id. */
  @Query(
      "select m from ProductMapping m where m.productId = :productId "
          + "and m.supersededAt is null and m.reviewStatus <> 'REJECTED'")
  List<ProductMapping> currentAcceptedForProduct(UUID productId);
}
