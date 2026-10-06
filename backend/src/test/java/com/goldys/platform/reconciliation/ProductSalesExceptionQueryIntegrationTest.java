package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProductSalesExceptionQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ReconciliationExceptionRowRepository repository;
  @Autowired ProductSalesExceptionQuery query;

  @BeforeEach
  void seed() {
    jdbc.update("truncate table reconciliation_exception");
    repository.save(
        new ReconciliationExceptionRow(
            "product_sales", "garlic aioli", "product_sales", LocalDate.of(2026, 9, 14), "conflict",
            Instant.EPOCH));
    repository.save(
        new ReconciliationExceptionRow(
            "product_sales", "chips", "product_sales", LocalDate.of(2026, 9, 15), "missing",
            Instant.EPOCH));
  }

  @Test
  void listAllReturnsNewestFirst() {
    var rows = query.listAll();
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).tradingDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(rows.get(0).productNameKey()).isEqualTo("chips");
  }

  @Test
  void countOpenCountsOnlyProductExceptions() {
    assertThat(query.countOpen()).isEqualTo(2);
  }
}
