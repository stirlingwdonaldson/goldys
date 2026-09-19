package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalSaleItemRepository extends BitemporalRepository<CanonicalSaleItem> {
  List<CanonicalSaleItem> findAllBySourceSystemAndSourceRecordRefOrderByRecordedAt(
      String sourceSystem, String sourceRecordRef);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from CanonicalSaleItem s where s.sourceSystem = :source "
          + "and s.sourceRecordRef = :sourceRef and s.supersededAt is null")
  Optional<CanonicalSaleItem> lockCurrentSourceFact(String source, String sourceRef);

  @Query(
      "select s from CanonicalSaleItem s where s.logicalEntityId = :logicalId "
          + "and s.sourceSystem = :source and s.supersededAt is null")
  Optional<CanonicalSaleItem> findCurrent(UUID logicalId, String source);

  @Query(
      "select s from CanonicalSaleItem s where s.logicalEntityId = :logicalId "
          + "and s.sourceSystem = :source and s.recordedAt <= :asOf "
          + "and (s.supersededAt is null or s.supersededAt > :asOf)")
  Optional<CanonicalSaleItem> findKnownAt(UUID logicalId, String source, Instant asOf);
}
