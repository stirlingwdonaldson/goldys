package com.goldys.platform.reconciliation;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records permission-gated, append-only manual overrides for a trading date. */
@Service
public class DailySalesOverrideService {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");
  private static final Clock CLOCK = Clock.systemUTC();

  private final DailySalesOverrideRepository repository;
  private final PermissionService permissions;
  private final CanonicalDailySalesQuery dailySales;

  public DailySalesOverrideService(
      DailySalesOverrideRepository repository,
      PermissionService permissions,
      CanonicalDailySalesQuery dailySales) {
    this.repository = repository;
    this.permissions = permissions;
    this.dailySales = dailySales;
  }

  @Transactional
  public DailySalesOverride save(
      UserRole actor,
      String oidcIssuer,
      String oidcSubject,
      LocalDate date,
      String source,
      String reason) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);

    // Reject an override that names a source with no canonical row for the date — otherwise the
    // conflict would silently vanish with no resolved total anywhere.
    boolean sourceKnown =
        dailySales.currentDailySalesForDate(date).stream()
            .anyMatch(s -> s.sourceSystem().equals(source));
    if (!sourceKnown) {
      throw new IllegalArgumentException("No data from source '" + source + "' on " + date);
    }

    Instant now = CLOCK.instant();
    Optional<DailySalesOverride> current = repository.lockCurrent(date);
    if (current.isPresent()) {
      current.get().supersede(now);
      repository.saveAndFlush(current.get());
    }
    return repository.save(
        DailySalesOverride.create(date, source, reason, oidcIssuer, oidcSubject, now));
  }

  /** The current authoritative source for a date, if any override is active. */
  public Optional<String> currentAuthoritativeSource(LocalDate date) {
    return repository.findCurrent(date).map(DailySalesOverride::authoritativeSource);
  }
}
