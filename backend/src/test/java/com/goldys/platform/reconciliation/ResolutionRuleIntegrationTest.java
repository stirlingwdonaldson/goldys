package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ResolutionRuleIntegrationTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired ResolutionRuleService service;
  @Autowired ResolutionRuleRepository repository;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolution_rule");
  }

  @Test
  void priorityRuleRoundTripsSourcePriorityThroughJsonb() {
    ResolutionRuleView saved =
        service.save(
            OWNER,
            "owner@example.com",
            new ResolutionRuleService.RuleInput(
                "daily_sales", "daily_sales", "priority", null, List.of("CTB", "LIGHTSPEED")));

    assertThat(saved.sourcePriority()).containsExactly("CTB", "LIGHTSPEED");

    ResolutionRuleView listed = service.list().get(0);
    assertThat(listed.sourcePriority()).containsExactly("CTB", "LIGHTSPEED");
    assertThat(listed.strategy()).isEqualTo("priority");
  }

  @Test
  void editingSupersedesThePriorRuleAndListReturnsOnlyCurrent() {
    service.save(
        OWNER,
        "owner@example.com",
        new ResolutionRuleService.RuleInput(
            "daily_sales", "daily_sales", "priority", null, List.of("CTB")));
    service.save(
        OWNER,
        "owner@example.com",
        new ResolutionRuleService.RuleInput(
            "daily_sales", "daily_sales", "priority", null, List.of("LIGHTSPEED")));

    assertThat(service.list()).hasSize(1);
    assertThat(service.list().get(0).sourcePriority()).containsExactly("LIGHTSPEED");
    // The superseded row is still there for the audit; the list excludes it.
    assertThat(repository.findAllByOrderByRecordedAtDesc()).hasSize(2);
  }

  @Test
  void deleteRecordsTheDeleterInTheAudit() {
    ResolutionRuleView saved =
        service.save(
            OWNER,
            "owner@example.com",
            new ResolutionRuleService.RuleInput(
                "daily_sales", "daily_sales", "priority", null, List.of("CTB")));

    service.delete(OWNER, "deleter@example.com", saved.id().toString());

    List<RuleAuditService.RuleAuditEntry> history = new RuleAuditService(repository).history();
    RuleAuditService.RuleAuditEntry deleted =
        history.stream().filter(e -> e.change().equals("deleted")).findFirst().orElseThrow();
    assertThat(deleted.by()).isEqualTo("deleter@example.com");
  }
}
