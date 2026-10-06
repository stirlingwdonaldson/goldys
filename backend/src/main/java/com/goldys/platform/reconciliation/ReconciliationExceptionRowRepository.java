package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReconciliationExceptionRowRepository
    extends JpaRepository<ReconciliationExceptionRow, ReconciliationExceptionRow.Id> {
  List<ReconciliationExceptionRow> findByEntityTypeOrderByTradingDateAsc(String entityType);

  List<ReconciliationExceptionRow> findByEntityTypeAndTradingDateIn(
      String entityType, Collection<LocalDate> dates);
}
