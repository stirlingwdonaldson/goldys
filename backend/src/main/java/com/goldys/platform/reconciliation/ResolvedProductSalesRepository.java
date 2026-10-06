package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ResolvedProductSalesRepository
    extends JpaRepository<ResolvedProductSales, ResolvedProductSales.Id> {

  Optional<ResolvedProductSales> findByTradingDateAndProductNameKey(LocalDate date, String key);

  List<ResolvedProductSales> findByTradingDateBetweenOrderByTradingDateAscProductNameKeyAsc(
      LocalDate from, LocalDate to);

  long countByHasConflictTrue();
}
