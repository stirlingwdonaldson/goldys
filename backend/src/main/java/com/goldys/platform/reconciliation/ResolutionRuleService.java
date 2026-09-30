package com.goldys.platform.reconciliation;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, edits, and deletes standing resolution rules, permission-gated and append-only. */
@Service
public class ResolutionRuleService {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");
  private static final Clock CLOCK = Clock.systemUTC();

  private final ResolutionRuleRepository repository;
  private final PermissionService permissions;

  public ResolutionRuleService(ResolutionRuleRepository repository, PermissionService permissions) {
    this.repository = repository;
    this.permissions = permissions;
  }

  public List<ResolutionRuleView> list() {
    return repository.findAllBySupersededAtIsNullOrderByRecordedAtDesc().stream()
        .map(this::toView)
        .toList();
  }

  @Transactional
  public ResolutionRuleView save(UserRole actor, String actorEmail, RuleInput input) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);
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
    return toView(saved);
  }

  @Transactional
  public void delete(UserRole actor, String actorEmail, String id) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);
    repository
        .findById(UUID.fromString(id))
        .ifPresent(rule -> rule.supersede(CLOCK.instant(), actorEmail));
  }

  public Optional<ResolutionRule> findCurrent(String entityType, String fieldKey) {
    return repository.findCurrent(entityType, fieldKey);
  }

  public Optional<Instant> lastChangedAt() {
    return repository.findFirstByOrderByRecordedAtDesc().map(ResolutionRule::recordedAt);
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
