package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SourceProductSaleFactRepository extends BitemporalRepository<SourceProductSaleFact> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select f from SourceProductSaleFact f where f.sourceRecordRef = :ref "
          + "and f.sourceSystem = :source and f.supersededAt is null")
  Optional<SourceProductSaleFact> lockCurrentSourceFact(String source, String ref);

  @Modifying
  @Query(
      value =
          """
      insert into source_product_sale_observation(source_system, source_record_ref, raw_record_id)
      values (:source, :ref, :rawId) on conflict do nothing
      """,
      nativeQuery = true)
  int registerObservation(String source, String ref, UUID rawId);

  @Query(
      value =
          """
      select exists (
        select 1 from source_product_sale_observation observation
        join raw_record later on later.id = observation.raw_record_id
        join raw_record incoming on incoming.id = :rawId
        where observation.source_system = :source and observation.source_record_ref = :ref
          and (later.fetched_at, later.id) > (incoming.fetched_at, incoming.id))
      """,
      nativeQuery = true)
  boolean hasLaterObservation(String source, String ref, UUID rawId);
}
