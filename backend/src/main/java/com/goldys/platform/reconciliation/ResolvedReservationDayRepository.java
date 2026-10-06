package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ResolvedReservationDayRepository
    extends JpaRepository<ResolvedReservationDay, ResolvedReservationDay.Id> {

  List<ResolvedReservationDay> findByTradingDateBetweenOrderByTradingDateAscServicePeriodAsc(
      LocalDate from, LocalDate to);

  void deleteByTradingDateIn(Collection<LocalDate> dates);

  long countByHasConflictTrue();
}
