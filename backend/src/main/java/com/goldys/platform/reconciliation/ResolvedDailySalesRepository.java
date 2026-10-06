package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ResolvedDailySalesRepository extends JpaRepository<ResolvedDailySales, LocalDate> {
  Optional<ResolvedDailySales> findTopByOrderByTradingDateDesc();

  List<ResolvedDailySales> findByTradingDateBetweenOrderByTradingDateAsc(
      LocalDate from, LocalDate to);

  List<ResolvedDailySales> findByTradingDateIn(Collection<LocalDate> dates);

  long countByHasConflictTrue();
}
