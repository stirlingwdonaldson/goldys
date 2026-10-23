package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.reconciliation.ProductSalesProjector;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CtbProductSalesRecoveryIntegrationTest {
  @Autowired IngestionService ingestion;
  @Autowired CanonicalProductSalesIngest products;
  @Autowired ProductSalesProjector projector;
  @Autowired JdbcTemplate jdbc;

  @Test
  void realUngroupedSampleReachesCanonicalAndResolvedWithoutDoubleCountingReplay()
      throws Exception {
    byte[] bytes;
    try (var in = getClass().getResourceAsStream("/fixtures/ctb/sale-items-null-groups.json")) {
      bytes = in.readAllBytes();
    }
    var raw =
        ingestion.ingestPush(
            "CTB",
            "sample-recovery",
            FetchMethod.API,
            "application/json",
            bytes,
            "UTF-8",
            "ctb-revenue");
    var day = LocalDate.of(2026, 7, 25);
    var items = new CtbSaleItemParser().parse(bytes);
    var batch = new CtbProductSalesBatch(day);
    items.forEach(item -> batch.add(item, raw));
    products.recordAll(batch.totals());
    products.recordAll(batch.totals());
    projector.recomputeAll();
    var expected = items.stream().map(CtbSaleItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_product_sales where trading_date=? and source_product_ref is not null and superseded_at is null",
                Integer.class,
                day))
        .isEqualTo(186);
    assertThat(
            jdbc.queryForObject(
                "select sum(amount) from resolved_product_sales where trading_date=? and product_name_key like 'product %'",
                BigDecimal.class, day))
        .isEqualByComparingTo(expected);
  }
}
