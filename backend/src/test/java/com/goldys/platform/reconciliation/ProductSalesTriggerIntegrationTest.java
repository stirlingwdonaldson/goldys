package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.canonical.ProductSalesInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProductSalesTriggerIntegrationTest {

  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalProductSalesIngest ingest;
  @Autowired ProductSalesOverrideService overrideService;
  @Autowired ResolutionRuleService ruleService;
  @Autowired ReconciliationExceptionRowRepository exceptions;

  @MockitoBean
  PermissionService permissions; // no-op mock so saves succeed without a permission fixture

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table reconciliation_exception, canonical_product_sales, product_sales_override, resolution_rule");
  }

  @Test
  void recordingCanonicalProjectsThePairAutomatically() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));

    assertThat(exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales")).hasSize(1);
  }

  @Test
  void overrideSaveProjectsThePair() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));

    overrideService.save(OWNER, "a@b.com", SEP_14, "garlic aioli", "LIGHTSPEED", "typo");

    assertThat(exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales")).isEmpty();
  }

  @Test
  void productRuleChangeRecomputesAllPairs() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));

    ruleService.save(
        OWNER,
        "a@b.com",
        new ResolutionRuleService.RuleInput(
            "product_sales", "garlic aioli", "priority", null, List.of("CTB")));

    assertThat(exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales")).isEmpty();
  }

  private ProductSalesInput input(
      String source, LocalDate date, String key, String qty, String amount) {
    return new ProductSalesInput(source, date, key, bd(qty), bd(amount), rawRecord());
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'CTB', 'test', 'SUCCESS', now(), 1, 1)",
        runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'CTB', 'API', 'application/json', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
