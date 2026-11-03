package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.ReportingSalesQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
@Transactional
class ReportingSourceCostIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired ReportingSalesQuery query;
  private final LocalDate date = LocalDate.parse("2026-10-26");

  @Test
  void sourceCostRequiresMatchingSelectedSourceAndScopeAndPreservesRefundSigns() {
    UUID run = UUID.randomUUID(), raw = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) values (?,'CTB','cost-regression','SUCCESS',now(),1,1)",
        run);
    jdbc.update(
        "insert into raw_record(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,payload_sha256,payload_byte_length,fetcher_identity,fetched_at) values (?,?,'CTB','API','application/json',?,?,1,'test',now())",
        raw,
        run,
        new byte[] {1},
        "0".repeat(64));
    row(raw, "valid", 2, 20, 2, 4, 0, 1);
    row(raw, "refund", -2, -20, 2, -4, 0, 1);
    row(raw, "zero placeholder", 2, 20, 0, 0, 0, 1);
    row(raw, "inconsistent", 2, 20, 2, 8, 0, 1);
    row(raw, "no tax", 2, 20, 2, 4, 0, null);
    row(raw, "no site", 2, 20, 2, 4, null, 1);
    row(raw, "gross mismatch", 2, 20, 2, 4, 0, 1);
    row(raw, "quantity mismatch", 2, 20, 2, 4, 0, 1);
    row(raw, "different source", 2, 20, 2, 4, 0, 1);
    row(raw, "wrong sign", 2, -20, 2, 4, 0, 1);
    row(raw, "no cost", 2, 20, null, null, 0, 1);
    jdbc.update(
        "update resolved_product_sales set amount=21 where product_name_key='gross mismatch'");
    jdbc.update(
        "update resolved_product_sales set quantity_sold=3 where product_name_key='quantity mismatch'");
    jdbc.update(
        "update resolved_product_sales set authoritative_source='LIGHTSPEED' where product_name_key='different source'");
    var costs = new HashMap<String, ReportingSalesQuery.Sale>();
    query.salesWithCosts(date, date).forEach(s -> costs.put(s.name(), s));
    assertThat(costs.get("valid").knownCost()).isEqualByComparingTo("4");
    assertThat(costs.get("refund").knownCost()).isEqualByComparingTo("-4");
    assertThat(costs.get("valid").costedLines()).isEqualTo(1);
    for (String name :
        List.of(
            "zero placeholder",
            "inconsistent",
            "no tax",
            "no site",
            "gross mismatch",
            "quantity mismatch",
            "different source",
            "wrong sign",
            "no cost")) {
      assertThat(costs.get(name).knownCost()).as(name).isNull();
      assertThat(costs.get(name).costedLines()).as(name).isZero();
    }
    assertThat(query.sales(date, date)).allMatch(s -> s.knownCost() == null);
  }

  private void row(
      UUID raw,
      String name,
      int qty,
      int amount,
      Integer unit,
      Integer cost,
      Integer site,
      Integer tax) {
    jdbc.update(
        """
      insert into source_product_sale_fact(id,logical_entity_id,source_system,source_record_ref,trading_date,stock_code,
        cost_per_unit,total_food_cost,quantity_sold,amount,tax_amount,product_name,site_number,raw_record_id,valid_from,recorded_at)
      values (?,?,'CTB',?,?,'code',?,?,?,?,?,?,?,?,now(),now())
      """,
        UUID.randomUUID(),
        UUID.randomUUID(),
        name,
        date,
        unit,
        cost,
        qty,
        amount,
        tax,
        name,
        site,
        raw);
    jdbc.update(
        """
      insert into resolved_product_sales(trading_date,product_name_key,quantity_sold,amount,resolution_type,authoritative_source,has_conflict,resolved_at)
      values (?,?,?,?,'single','CTB',false,now())
      """,
        date,
        name,
        qty,
        amount);
  }
}
