package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
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

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProductSalesIntegrationTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);
  private static final LocalDate SEP_15 = LocalDate.of(2026, 9, 15);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalProductSalesIngest productSales;
  @Autowired ProductSalesReconciliationService reconciliation;
  @Autowired ProductSalesOverrideService overrides;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_product_sales, product_sales_override");
  }

  @Test
  void perDayScopingAndOverrideResolution() {
    // 14 Sep: garlic aioli conflicts; 15 Sep: agrees (separate day — must not be conflated).
    productSales.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    productSales.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    productSales.record(input("LIGHTSPEED", SEP_15, "garlic aioli", "150", "380.88"));
    productSales.record(input("CTB", SEP_15, "garlic aioli", "150", "380.88"));

    List<ProductSalesConflict> conflicts = reconciliation.conflicts();
    assertThat(conflicts).hasSize(1);
    assertThat(conflicts.get(0).tradingDate()).isEqualTo(SEP_14);
    assertThat(conflicts.get(0).productNameKey()).isEqualTo("garlic aioli");

    overrides.save(OWNER, "a@b.com", SEP_14, "garlic aioli", "CTB", "typo");

    assertThat(reconciliation.conflicts()).isEmpty();
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
