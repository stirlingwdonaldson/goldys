package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.reconciliation.DailySalesOverrideService;
import com.goldys.platform.reconciliation.OverrideAuditService;
import com.goldys.platform.reconciliation.ProductSalesExceptionQuery;
import com.goldys.platform.reconciliation.ProductSalesOverrideService;
import com.goldys.platform.reconciliation.ReconciliationExceptionQuery;
import com.goldys.platform.reconciliation.RuleAuditService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationApplicationServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  private final PermissionService permissions = mock(PermissionService.class);
  private final ReconciliationExceptionQuery dailyExceptions =
      mock(ReconciliationExceptionQuery.class);
  private final ProductSalesExceptionQuery productExceptions =
      mock(ProductSalesExceptionQuery.class);
  private final CanonicalDailySalesQuery dailySales = mock(CanonicalDailySalesQuery.class);
  private final CanonicalProductSalesQuery productSales = mock(CanonicalProductSalesQuery.class);
  private final DailySalesOverrideService dailyOverrides = mock(DailySalesOverrideService.class);
  private final ProductSalesOverrideService productOverrides =
      mock(ProductSalesOverrideService.class);
  private final RuleAuditService ruleAudit = mock(RuleAuditService.class);
  private final OverrideAuditService overrideAudit = mock(OverrideAuditService.class);
  private final ReconciliationApplicationService service =
      new ReconciliationApplicationService(
          dailyExceptions,
          productExceptions,
          dailySales,
          productSales,
          dailyOverrides,
          productOverrides,
          ruleAudit,
          overrideAudit,
          permissions);

  @Test
  void readDeniedThrows() {
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    assertThatThrownBy(() -> service.listDailyExceptions(OWNER))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void overrideDailyDelegatesToTheOverrideService() {
    var result = service.overrideDaily(OWNER, "a@b.com", SEP_13, "LIGHTSPEED", "typo");

    verify(dailyOverrides).save(OWNER, "a@b.com", SEP_13, "LIGHTSPEED", "typo");
    assertThat(result.ok()).isTrue();
    assertThat(result.recordId()).isEqualTo("2026-09-13");
  }

  @Test
  void overrideProductDelegatesToTheOverrideService() {
    var result = service.overrideProduct(OWNER, "a@b.com", SEP_13, "chips", "CTB", "typo");

    verify(productOverrides).save(OWNER, "a@b.com", SEP_13, "chips", "CTB", "typo");
    assertThat(result.recordId()).isEqualTo("chips");
  }

  @Test
  void auditCombinesRuleAndOverrideHistoryNewestFirst() {
    Instant older = Instant.parse("2026-09-13T09:00:00Z");
    Instant newer = Instant.parse("2026-09-14T09:00:00Z");
    when(ruleAudit.history())
        .thenReturn(
            List.of(
                new RuleAuditService.RuleAuditEntry(
                    UUID.randomUUID(), "daily_sales", "daily_sales", "created", older, "a@b.com")));
    when(overrideAudit.history())
        .thenReturn(
            List.of(
                new OverrideAuditService.OverrideAuditEntry(
                    "daily_sales", "daily_sales", "LIGHTSPEED", "typo", "a@b.com", newer, null),
                new OverrideAuditService.OverrideAuditEntry(
                    "daily_sales", "daily_sales", "CTB", null, "a@b.com", older, newer)));

    var entries = service.audit(OWNER);

    assertThat(entries).hasSize(3);
    assertThat(entries.get(0))
        .extracting(e -> e.kind(), e -> e.change())
        .containsExactly("override", "set");
    assertThat(entries.get(1))
        .extracting(e -> e.kind(), e -> e.change())
        .containsExactly("override", "removed");
    assertThat(entries.get(2))
        .extracting(e -> e.kind(), e -> e.change())
        .containsExactly("rule", "created");
  }

  @Test
  void auditDeniedThrows() {
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    assertThatThrownBy(() -> service.audit(OWNER)).isInstanceOf(AccessDeniedException.class);
  }
}
