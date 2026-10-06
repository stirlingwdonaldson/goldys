package com.goldys.platform.reconciliation;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ReservationOverrideRepository extends JpaRepository<ReservationOverride, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from ReservationOverride o where o.tradingDate = :date "
          + "and o.servicePeriod = :period and o.supersededAt is null")
  Optional<ReservationOverride> lockCurrent(LocalDate date, String period);

  @Query(
      "select o from ReservationOverride o where o.tradingDate = :date "
          + "and o.servicePeriod = :period and o.supersededAt is null")
  Optional<ReservationOverride> findCurrent(LocalDate date, String period);

  @Query("select o from ReservationOverride o where o.supersededAt is null")
  List<ReservationOverride> findAllCurrent();
}
