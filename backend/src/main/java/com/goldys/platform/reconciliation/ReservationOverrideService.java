package com.goldys.platform.reconciliation;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records permission-gated, append-only manual overrides for a date/service-period's covers. */
@Service
public class ReservationOverrideService {
  private static final ResourceKey RESOURCE = new ResourceKey("reservations.metrics");
  private static final Clock CLOCK = Clock.systemUTC();

  private final ReservationOverrideRepository repository;
  private final PermissionService permissions;
  private final ReservationProjector projector;

  public ReservationOverrideService(
      ReservationOverrideRepository repository,
      PermissionService permissions,
      ReservationProjector projector) {
    this.repository = repository;
    this.permissions = permissions;
    this.projector = projector;
  }

  @Transactional
  public ReservationOverride save(
      UserRole actor,
      String actorEmail,
      LocalDate date,
      String servicePeriod,
      Long overriddenCovers,
      String reason) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);

    Instant now = CLOCK.instant();
    Optional<ReservationOverride> current = repository.lockCurrent(date, servicePeriod);
    if (current.isPresent()) {
      current.get().supersede(now);
      repository.saveAndFlush(current.get());
    }
    ReservationOverride saved =
        repository.save(
            ReservationOverride.create(
                date, servicePeriod, overriddenCovers, reason, actorEmail, now));
    projector.recompute(date);
    return saved;
  }

  /** The current overridden covers for a date/service-period, if any override is active. */
  public Optional<Long> currentCovers(LocalDate date, String servicePeriod) {
    return repository.findCurrent(date, servicePeriod).map(ReservationOverride::overriddenCovers);
  }
}
