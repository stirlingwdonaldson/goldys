package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.InventoryObservation;
import com.goldys.platform.semantic.InventoryObservationQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Authorizes source-qualified stock observations independently of dashboard aggregates. */
@Service
public class InventoryObservationService {
  private final InventoryObservationQuery query;
  private final PermissionService permissions;

  public InventoryObservationService(
      InventoryObservationQuery query, PermissionService permissions) {
    this.query = query;
    this.permissions = permissions;
  }

  public Page observations(
      UserRole role, LocalDate from, LocalDate to, String kind, int page, int size) {
    permissions.require(role, new ResourceKey("inventory.stock"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("inventory.cost"), PermissionAction.READ);
    if (from == null
        || to == null
        || from.isAfter(to)
        || !Set.of("ALL", "COUNT", "WASTE").contains(kind)
        || page < 0
        || page > 100000
        || size < 1
        || size > 200)
      throw new IllegalArgumentException("Invalid observation range, kind or page");
    List<InventoryObservation> rows =
        query.observations(from, to, kind, Math.multiplyExact(page, size), size + 1);
    boolean more = rows.size() > size;
    return new Page(
        rows.stream().limit(size).toList(),
        page,
        size,
        more,
        List.of(
            "Incomplete observation listing: expected source population is unverified; this page is not a stock balance",
            "Counts apply only at their recorded dates; stocktake partial/locked state is unknown when absent",
            "Quantities retain source units; source costs have unverified currency, tax and valuation bases"));
  }

  public record Page(
      List<InventoryObservation> observations,
      int page,
      int size,
      boolean hasMore,
      List<String> notices) {}
}
