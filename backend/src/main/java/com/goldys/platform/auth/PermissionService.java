package com.goldys.platform.auth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * The one and only permission enforcement point.
 *
 * <p>Every protected read and override action must route through {@link #require}. There is no
 * owner bypass and no ordinal seniority comparison here; both would defeat the table-driven model
 * this service exists to protect.
 */
@Service
public class PermissionService {
  private final PermissionLookup lookup;
  private final ScopedValue<Set<Check>> evidence = ScopedValue.newInstance();

  public record Check(
      UserRole role, ResourceKey resource, PermissionAction action, boolean allowed) {}

  public record Observed<T>(T value, List<Check> checks) {}

  /**
   * Capture actual checks during one synchronous operation, including denied discovery queries.
   * This is evidence, never an alternative authorization path. Async work must not use this scope.
   */
  public <T> Observed<T> observe(Supplier<T> operation) {
    if (evidence.isBound()) throw new IllegalStateException("Nested permission observation");
    Set<Check> checks = new LinkedHashSet<>();
    T value = ScopedValue.where(evidence, checks).call(operation::get);
    return new Observed<>(value, List.copyOf(checks));
  }

  public PermissionService(PermissionLookup lookup) {
    this.lookup = lookup;
  }

  /**
   * Authorizes a request, throwing {@link AccessDeniedException} when the role is not explicitly
   * granted the action on the resource.
   */
  public void require(UserRole role, ResourceKey resource, PermissionAction action) {
    if (!isAllowed(role, resource, action)) {
      throw AccessDeniedException.forResource(resource.value());
    }
  }

  /** True when the role is granted the action on the resource (a query, not an enforcement). */
  public boolean isAllowed(UserRole role, ResourceKey resource, PermissionAction action) {
    boolean allowed = lookup.isAllowed(role, resource, action);
    if (evidence.isBound()) evidence.get().add(new Check(role, resource, action, allowed));
    return allowed;
  }
}
