package com.goldys.platform.reconciliation;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, edits, and deletes standing resolution rules, permission-gated and append-only. */
@Service
public class ResolutionRuleService {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");
  private static final Clock CLOCK = Clock.systemUTC();

  private static final Set<String> ENTITY_TYPES = Set.of("daily_sales", "product_sales");
  private static final Set<String> STRATEGIES = Set.of("priority", "manual", "custom");
  private static final Set<String> CUSTOM_LOGIC = Set.of("flag", "highest", "lowest", "newest");

  private final ResolutionRuleRepository repository;
  private final PermissionService permissions;
  private final DailySalesProjector projector;
  private final ProductSalesProjector productProjector;

  public ResolutionRuleService(
      ResolutionRuleRepository repository,
      PermissionService permissions,
      DailySalesProjector projector,
      ProductSalesProjector productProjector) {
    this.repository = repository;
    this.permissions = permissions;
    this.projector = projector;
    this.productProjector = productProjector;
  }

  public List<ResolutionRuleView> list() {
    return repository.findAllBySupersededAtIsNullOrderByRecordedAtDesc().stream()
        .map(this::toView)
        .toList();
  }

  @Transactional
  public ResolutionRuleView save(UserRole actor, String actorEmail, RuleInput input) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);
    validate(input);
    Instant now = CLOCK.instant();
    Optional<ResolutionRule> current = repository.lockCurrent(input.entityType(), input.fieldKey());
    if (current.isPresent()) {
      current.get().supersede(now, actorEmail);
      repository.saveAndFlush(current.get());
    }
    ResolutionRule saved =
        repository.save(
            ResolutionRule.create(
                input.entityType(),
                input.fieldKey(),
                input.strategy(),
                input.customLogic(),
                input.sourcePriority(),
                actorEmail,
                now));
    if ("daily_sales".equals(input.entityType())) {
      projector.recomputeAll();
    }
    if ("product_sales".equals(input.entityType())) {
      productProjector.recomputeAll();
    }
    return toView(saved);
  }

  @Transactional
  public void delete(UserRole actor, String actorEmail, String id) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);
    UUID ruleId;
    try {
      ruleId = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid rule id: " + id);
    }
    ResolutionRule current =
        repository
            .findCurrentById(ruleId)
            .orElseThrow(() -> new IllegalArgumentException("No current rule with id " + id));
    current.supersede(CLOCK.instant(), actorEmail);
    if ("daily_sales".equals(current.entityType())) {
      projector.recomputeAll();
    }
    if ("product_sales".equals(current.entityType())) {
      productProjector.recomputeAll();
    }
  }

  public Optional<ResolutionRule> findCurrent(String entityType, String fieldKey) {
    return repository.findCurrent(entityType, fieldKey);
  }

  /** The current non-superseded rule for an entity/field pair, for provenance drill-down. */
  public Optional<ResolutionRuleView> currentView(String entityType, String fieldKey) {
    return repository.findCurrent(entityType, fieldKey).map(this::toView);
  }

  public Optional<Instant> lastChangedAt() {
    return repository.findFirstByOrderByRecordedAtDesc().map(ResolutionRule::recordedAt);
  }

  private static void validate(RuleInput input) {
    if (input.entityType() == null || !ENTITY_TYPES.contains(input.entityType())) {
      throw new IllegalArgumentException("Unknown entity type: " + input.entityType());
    }
    if (input.fieldKey() == null || input.fieldKey().isBlank()) {
      throw new IllegalArgumentException("Field key is required.");
    }
    if (input.strategy() == null || !STRATEGIES.contains(input.strategy())) {
      throw new IllegalArgumentException("Unknown strategy: " + input.strategy());
    }
    if ("custom".equals(input.strategy())
        && (input.customLogic() == null || !CUSTOM_LOGIC.contains(input.customLogic()))) {
      throw new IllegalArgumentException("Unknown custom logic: " + input.customLogic());
    }
    if ("priority".equals(input.strategy())
        && (input.sourcePriority() == null || input.sourcePriority().isEmpty())) {
      throw new IllegalArgumentException("Priority strategy requires a source order.");
    }
  }

  private ResolutionRuleView toView(ResolutionRule r) {
    return new ResolutionRuleView(
        r.id(),
        r.entityType(),
        r.fieldKey(),
        r.strategy(),
        r.sourcePriority(),
        r.customLogic(),
        r.recordedAt(),
        r.actorEmail());
  }

  /** A rule authored through the API. */
  public record RuleInput(
      String entityType,
      String fieldKey,
      String strategy,
      String customLogic,
      List<String> sourcePriority) {}
}
