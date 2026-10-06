package com.goldys.platform.reconciliation;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ProductSalesOverrideRepository extends JpaRepository<ProductSalesOverride, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from ProductSalesOverride o where o.productNameKey = :key "
          + "and o.tradingDate = :date and o.supersededAt is null")
  Optional<ProductSalesOverride> lockCurrent(String key, LocalDate date);

  @Query(
      "select o from ProductSalesOverride o where o.productNameKey = :key "
          + "and o.tradingDate = :date and o.supersededAt is null")
  Optional<ProductSalesOverride> findCurrent(String key, LocalDate date);

  @Query("select o from ProductSalesOverride o where o.supersededAt is null")
  List<ProductSalesOverride> findAllCurrent();

  long countByRecordedAtAfter(Instant since);
}
