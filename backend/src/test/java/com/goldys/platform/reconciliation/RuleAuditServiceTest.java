package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleAuditServiceTest {

  @Test
  void derivesCreatedUpdatedAndDeleted() {
    Instant t1 = Instant.parse("2026-09-13T09:00:00Z");
    Instant t2 = Instant.parse("2026-09-14T09:00:00Z");
    Instant t3 = Instant.parse("2026-09-15T09:00:00Z");

    ResolutionRule created =
        ResolutionRule.create(
            "daily_sales", "daily_sales", "priority", null, List.of("CTB"), "a@b.com", t1);
    created.supersede(t2, "editor@example.com"); // replaced by the updated rule below

    ResolutionRule updated =
        ResolutionRule.create(
            "daily_sales", "daily_sales", "priority", null, List.of("LIGHTSPEED"), "a@b.com", t2);
    updated.supersede(t3, "deleter@example.com"); // then deleted — no successor row

    List<RuleAuditService.RuleAuditEntry> history =
        RuleAuditService.history(List.of(created, updated));

    assertThat(history).hasSize(3);
    assertThat(history)
        .extracting(RuleAuditService.RuleAuditEntry::change)
        .containsExactlyInAnyOrder("created", "updated", "deleted");
    RuleAuditService.RuleAuditEntry deletedEntry =
        history.stream().filter(e -> e.change().equals("deleted")).findFirst().orElseThrow();
    assertThat(deletedEntry.by()).isEqualTo("deleter@example.com");
  }
}
