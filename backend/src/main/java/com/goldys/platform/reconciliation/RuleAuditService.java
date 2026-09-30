package com.goldys.platform.reconciliation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Derives the rule change history from the append-only rule rows. */
@Service
public class RuleAuditService {
  private final ResolutionRuleRepository repository;

  public RuleAuditService(ResolutionRuleRepository repository) {
    this.repository = repository;
  }

  public List<RuleAuditEntry> history() {
    return history(repository.findAllByOrderByRecordedAtDesc());
  }

  /**
   * Pure derivation (also used directly by tests): group rows by (entityType, fieldKey), sort
   * oldest-first, and label each row "created" (first for its key), "updated" (superseded with a
   * successor), or "deleted" (superseded with no successor).
   */
  static List<RuleAuditEntry> history(List<ResolutionRule> rows) {
    Map<String, List<ResolutionRule>> byKey = new LinkedHashMap<>();
    for (ResolutionRule r : rows) {
      byKey
          .computeIfAbsent(r.entityType() + "\u0000" + r.fieldKey(), k -> new ArrayList<>())
          .add(r);
    }
    List<RuleAuditEntry> out = new ArrayList<>();
    for (List<ResolutionRule> group : byKey.values()) {
      group.sort(Comparator.comparing(ResolutionRule::recordedAt));
      for (int i = 0; i < group.size(); i++) {
        ResolutionRule r = group.get(i);
        String change = i == 0 ? "created" : "updated";
        out.add(
            new RuleAuditEntry(
                r.id(), r.entityType(), r.fieldKey(), change, r.recordedAt(), r.actorEmail()));
      }
      // A delete supersedes the last row with no successor: emit it as an extra event.
      ResolutionRule last = group.get(group.size() - 1);
      if (last.supersededAt() != null) {
        out.add(
            new RuleAuditEntry(
                last.id(),
                last.entityType(),
                last.fieldKey(),
                "deleted",
                last.supersededAt(),
                last.actorEmail()));
      }
    }
    out.sort(Comparator.comparing(RuleAuditEntry::at).reversed());
    return out;
  }

  public record RuleAuditEntry(
      UUID ruleId, String entityType, String fieldKey, String change, Instant at, String by) {}
}
