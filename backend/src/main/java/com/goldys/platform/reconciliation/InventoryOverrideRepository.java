package com.goldys.platform.reconciliation;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface InventoryOverrideRepository extends JpaRepository<InventoryOverride, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from InventoryOverride o where o.tradingDate = :date and o.supersededAt is null")
  Optional<InventoryOverride> lockCurrent(LocalDate date);

  @Query(
      "select o from InventoryOverride o where o.tradingDate = :date and o.supersededAt is null")
  Optional<InventoryOverride> findCurrent(LocalDate date);

  @Query("select o from InventoryOverride o where o.supersededAt is null")
  List<InventoryOverride> findAllCurrent();
}
