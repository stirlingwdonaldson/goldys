package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ResolvedLabourDayRepository
    extends JpaRepository<ResolvedLabourDay, ResolvedLabourDay.Id> {

  List<ResolvedLabourDay> findByTradingDateBetweenOrderByTradingDateAscDepartmentAsc(
      LocalDate from, LocalDate to);

  void deleteByTradingDateIn(Collection<LocalDate> dates);

  long countByHasConflictTrue();
}
