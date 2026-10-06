package com.goldys.platform.reconciliation;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface LabourOverrideRepository extends JpaRepository<LabourOverride, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from LabourOverride o where o.tradingDate = :date "
          + "and o.department = :department and o.supersededAt is null")
  Optional<LabourOverride> lockCurrent(LocalDate date, String department);

  @Query(
      "select o from LabourOverride o where o.tradingDate = :date "
          + "and o.department = :department and o.supersededAt is null")
  Optional<LabourOverride> findCurrent(LocalDate date, String department);

  @Query("select o from LabourOverride o where o.supersededAt is null")
  List<LabourOverride> findAllCurrent();
}
