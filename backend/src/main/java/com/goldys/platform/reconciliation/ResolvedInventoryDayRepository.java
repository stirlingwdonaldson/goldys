package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ResolvedInventoryDayRepository extends JpaRepository<ResolvedInventoryDay, LocalDate> {

  List<ResolvedInventoryDay> findByTradingDateBetweenOrderByTradingDateAsc(
      LocalDate from, LocalDate to);

  void deleteByTradingDateIn(Collection<LocalDate> dates);
}
