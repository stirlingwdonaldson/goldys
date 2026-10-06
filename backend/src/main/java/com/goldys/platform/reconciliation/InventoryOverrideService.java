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

/**
 * Records permission-gated, append-only manual overrides for a date's resolved purchases (COGS).
 */
@Service
public class InventoryOverrideService {
  private static final ResourceKey RESOURCE = new ResourceKey("inventory.cost");
  private static final Clock CLOCK = Clock.systemUTC();

  private final InventoryOverrideRepository repository;
  private final PermissionService permissions;
  private final InventoryProjector projector;

  public InventoryOverrideService(
      InventoryOverrideRepository repository,
      PermissionService permissions,
      InventoryProjector projector) {
    this.repository = repository;
    this.permissions = permissions;
    this.projector = projector;
  }

  @Transactional
  public InventoryOverride save(
      UserRole actor,
      String actorEmail,
      LocalDate date,
      BigDecimal overriddenPurchases,
      String reason) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);

    Instant now = CLOCK.instant();
    Optional<InventoryOverride> current = repository.lockCurrent(date);
    if (current.isPresent()) {
      current.get().supersede(now);
      repository.saveAndFlush(current.get());
    }
    InventoryOverride saved =
        repository.save(
            InventoryOverride.create(date, overriddenPurchases, reason, actorEmail, now));
    projector.recompute(date);
    return saved;
  }

  /** The current overridden purchases for a date, if any override is active. */
  public Optional<BigDecimal> currentPurchases(LocalDate date) {
    return repository.findCurrent(date).map(InventoryOverride::overriddenPurchases);
  }
}
