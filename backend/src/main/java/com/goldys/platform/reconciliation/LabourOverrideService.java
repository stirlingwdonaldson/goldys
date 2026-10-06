package com.goldys.platform.reconciliation;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records permission-gated, append-only manual overrides for a date/department's actual hours. */
@Service
public class LabourOverrideService {
  private static final ResourceKey RESOURCE = new ResourceKey("labour.hours");
  private static final Clock CLOCK = Clock.systemUTC();

  private final LabourOverrideRepository repository;
  private final PermissionService permissions;
  private final LabourProjector projector;

  public LabourOverrideService(
      LabourOverrideRepository repository,
      PermissionService permissions,
      LabourProjector projector) {
    this.repository = repository;
    this.permissions = permissions;
    this.projector = projector;
  }

  @Transactional
  public LabourOverride save(
      UserRole actor,
      String actorEmail,
      LocalDate date,
      String department,
      BigDecimal overriddenActualHours,
      String reason) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);

    Instant now = CLOCK.instant();
    Optional<LabourOverride> current = repository.lockCurrent(date, department);
    if (current.isPresent()) {
      current.get().supersede(now);
      repository.saveAndFlush(current.get());
    }
    LabourOverride saved =
        repository.save(
            LabourOverride.create(
                date, department, overriddenActualHours, reason, actorEmail, now));
    projector.recompute(date);
    return saved;
  }

  /** The current overridden actual hours for a date/department, if any override is active. */
  public Optional<BigDecimal> currentActualHours(LocalDate date, String department) {
    return repository.findCurrent(date, department).map(LabourOverride::overriddenActualHours);
  }
}
