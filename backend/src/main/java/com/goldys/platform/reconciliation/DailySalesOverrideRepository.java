package com.goldys.platform.reconciliation;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface DailySalesOverrideRepository extends JpaRepository<DailySalesOverride, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from DailySalesOverride o where o.tradingDate = :date and o.supersededAt is null")
  Optional<DailySalesOverride> lockCurrent(LocalDate date);

  @Query(
      "select o from DailySalesOverride o where o.tradingDate = :date and o.supersededAt is null")
  Optional<DailySalesOverride> findCurrent(LocalDate date);
}
