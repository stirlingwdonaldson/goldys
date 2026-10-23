package com.goldys.platform.canonical;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal processing receipts; canonical writers own locking and the enclosing transaction. */
@Repository
class InventoryObservationReceiptRepository {
  private final JdbcTemplate jdbc;

  InventoryObservationReceiptRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  boolean accept(String kind, String source, String ref, UUID raw, UUID headerVersion) {
    int inserted =
        jdbc.update(
            """
        insert into inventory_observation_receipt(observation_kind,source_system,source_record_ref,raw_record_id,header_version_id)
        values (?,?,?,?,?) on conflict do nothing
        """,
            kind,
            source,
            ref,
            raw,
            headerVersion);
    if (inserted == 0) return false;
    // This is reception order, not inferred vendor business-revision order. Header context is
    // excluded from this comparison so a new header cannot revive an older detail quantity.
    return !Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        select exists (
          select 1 from inventory_observation_receipt receipt
          join raw_record later on later.id=receipt.raw_record_id
          join raw_record incoming on incoming.id=?
          where receipt.observation_kind=? and receipt.source_system=? and receipt.source_record_ref=?
            and (later.fetched_at,later.id)>(incoming.fetched_at,incoming.id))
        """,
            Boolean.class,
            raw,
            kind,
            source,
            ref));
  }
}
