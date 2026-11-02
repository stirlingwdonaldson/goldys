package com.goldys.platform.reconciliation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Rebuildable indexed claims; financial facts and source observations are never changed. */
@Service
public class ReceiptEvidenceProjector {
  private final JdbcTemplate jdbc;

  public ReceiptEvidenceProjector(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recomputeAll() {
    // Serialize the refreshes so readers see one committed receipt-evidence snapshot.
    jdbc.execute("select pg_advisory_xact_lock(hashtextextended('receipt-evidence-projection',0))");
    jdbc.execute("refresh materialized view concurrently resolved_receipt_mapping_claim");
    jdbc.execute("refresh materialized view concurrently resolved_receipt_mapping");
    jdbc.execute("refresh materialized view concurrently resolved_sale_transaction");
    jdbc.execute("analyze resolved_receipt_mapping_claim");
    jdbc.execute("analyze resolved_receipt_mapping");
    jdbc.execute("analyze resolved_sale_transaction");
  }
}
