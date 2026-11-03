package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.*;
import com.goldys.platform.canonical.*;
import com.goldys.platform.semantic.*;
import com.goldys.platform.support.PostgresContainerConfiguration;
import jakarta.persistence.EntityManager;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.apache.commons.csv.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Non-personal CTB observations captured read-only on 3 November, not a synthetic discovery. */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
@Transactional
class ReportingRealWeekIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired CanonicalWastageIngest waste;
  @Autowired EntityManager em;
  @Autowired WeeklyReportService reports;
  @Autowired ReportingSalesQuery sales;

  @Test
  void completedWeekAccountsForAllRecordedSalesAndWasteWithoutApprovingHeldIdentities()
      throws Exception {
    UUID run = UUID.randomUUID(), raw = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) values (?,'CTB','real-week-regression','SUCCESS',now(),1,1)",
        run);
    jdbc.update(
        "insert into raw_record(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,payload_sha256,payload_byte_length,fetcher_identity,fetched_at) values (?,?,'CTB','API','text/csv',?,?,1,'regression-fixture',now())",
        raw,
        run,
        new byte[] {1},
        "0".repeat(64));
    try (var parser = csv("reporting-week-2026-10-26.csv")) {
      for (var r : parser)
        jdbc.update(
            """
        insert into source_product_sale_fact(id,logical_entity_id,source_system,source_record_ref,raw_record_id,valid_from,recorded_at,
          trading_date,stock_code,product_name,quantity_sold,amount,tax_amount,discounted_amount,site_number)
        values (?,?,'CTB',?,?,now(),now(),?,?,?,?,?,?,?,?)
        """,
            UUID.randomUUID(),
            UUID.randomUUID(),
            r.get("source_record_ref"),
            raw,
            LocalDate.parse(r.get("trading_date")),
            r.get("stock_code"),
            r.get("product_name"),
            decimal(r, "quantity_sold"),
            decimal(r, "amount"),
            decimal(r, "tax_amount"),
            decimal(r, "discounted_amount"),
            Integer.valueOf(r.get("site_number")));
    }
    jdbc.update(
        """
      insert into resolved_product_sales(trading_date,product_name_key,quantity_sold,amount,resolution_type,authoritative_source,has_conflict,resolved_at)
      select trading_date,regexp_replace(btrim(regexp_replace(lower(product_name),'[^a-z0-9]+',' ','g')),'^new ',''),
        sum(quantity_sold),sum(amount),'single','CTB',false,now()
      from source_product_sale_fact where raw_record_id=? group by 1,2
      """,
        raw);
    try (var parser = csv("reporting-waste-2026-10-26.csv")) {
      for (var r : parser)
        waste.record(
            new WastageInput(
                "CTB",
                Integer.parseInt(r.get("wastage_record_id")),
                Integer.parseInt(r.get("detail_id")),
                LocalDate.parse(r.get("wastage_date")),
                r.get("product_name_key"),
                decimal(r, "quantity"),
                decimal(r, "cost"),
                text(r, "reason"),
                null,
                text(r, "unit_of_measurement_name"),
                text(r, "unit_size"),
                r.get("recipe_id").isEmpty() ? null : Integer.valueOf(r.get("recipe_id")),
                raw));
    }
    loadLiveCosts();
    loadMappings("reporting-mappings-2026-10-26.json");
    em.flush();
    jdbc.update(
        """
      insert into resolved_daily_sales(trading_date,total_sales,net_sales,gst,resolution_type,authoritative_source,has_conflict,resolved_at) values
      ('2026-10-26',11733.7386,10669.7076,1064.0310,'rule','CTB',false,now()),
      ('2026-10-27',8596.3900,7815.3400,781.0500,'rule','CTB',false,now()),
      ('2026-10-28',20024.5800,18213.6100,1810.9700,'rule','LIGHTSPEED',false,now()),
      ('2026-10-29',14480.2000,13197.9300,1282.2700,'rule','LIGHTSPEED',false,now()),
      ('2026-10-30',36522.8599,33205.5719,3317.2880,'single','CTB',false,now()),
      ('2026-10-31',41565.8799,37791.5076,3774.3723,'rule','CTB',false,now()),
      ('2026-11-01',23358.4900,21238.5300,2119.9600,'rule','LIGHTSPEED',false,now())
      """);
    var from = LocalDate.parse("2026-10-26");
    var observed = sales.sales(from, from.plusDays(6));
    assertThat(observed).hasSize(1034).allMatch(s -> s.net() != null);
    assertThat(
            observed.stream()
                .map(ReportingSalesQuery.Sale::gross)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("155630.2663");
    var report =
        reports.report(
            new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER")), from, 1);
    var week = report.weeks().getFirst();
    var rows = new HashMap<String, WeeklyReport.Row>();
    for (var r : week.rows()) rows.put(r.id(), r);
    assertThat(rows.get("product_net_sales").total()).isEqualByComparingTo("141527.4119");
    assertThat(rows.get("known_food_sales").total()).isEqualByComparingTo("18711.5742");
    assertThat(rows.get("known_beverage_sales").total()).isEqualByComparingTo("17269.3986");
    assertThat(rows.get("other_sales").total()).isEqualByComparingTo("340");
    assertThat(rows.get("unclassified_sales").total()).isEqualByComparingTo("105206.4391");
    assertThat(rows.get("food_sales").total()).isNull();
    assertThat(rows.get("beverage_sales").total()).isNull();
    assertThat(rows.get("food_wastage").total()).isEqualByComparingTo("5.0196");
    assertThat(rows.get("beverage_wastage").total()).isEqualByComparingTo("452.986");
    assertThat(rows.get("unclassified_wastage").total()).isEqualByComparingTo("102.6997");
    assertThat(week.wastage().lines()).isEqualTo(107);
    assertThat(week.wastage().value()).isEqualByComparingTo("560.7053");
    assertThat(rows.get("sales_reconciliation_difference").total())
        .isEqualByComparingTo("604.7852");
    assertThat(rows.get("food_wastage_percent").total()).isNull();
    assertThat(rows.get("beverage_wastage_percent").total()).isNull();
    var directory = java.nio.file.Path.of("build/reports");
    java.nio.file.Files.createDirectories(directory);
    mapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(directory.resolve("real-week-before.json").toFile(), report);
    loadMappings("reporting-source-decisions-2026-10-26.json");
    loadMappings("reporting-waste-decisions-2026-10-26.json");
    var after =
        reports.report(
            new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER")), from, 1);
    rows.clear();
    for (var r : after.weeks().getFirst().rows()) rows.put(r.id(), r);
    assertThat(rows.get("sales_classified_coverage").days())
        .allSatisfy(v -> assertThat(v).isGreaterThan(new BigDecimal("0.997")));
    assertThat(rows.get("food_sales").days()).doesNotContainNull();
    assertThat(rows.get("beverage_sales").days()).doesNotContainNull();
    assertThat(rows.get("unclassified_sales").total()).isEqualByComparingTo("82.71");
    assertThat(rows.get("other_sales").total()).isEqualByComparingTo("437.07");
    assertThat(
            rows.get("food_sales")
                .total()
                .add(rows.get("beverage_sales").total())
                .add(rows.get("other_sales").total())
                .add(rows.get("unclassified_sales").total()))
        .isEqualByComparingTo("141527.4119");
    assertThat(rows.get("food_wastage").total()).isEqualByComparingTo("24.8493");
    assertThat(rows.get("beverage_wastage").total()).isEqualByComparingTo("535.856");
    assertThat(rows.get("unclassified_wastage").total()).isEqualByComparingTo("0");
    assertThat(rows.get("food_wastage_percent").total()).isNotNull();
    assertThat(rows.get("beverage_wastage_percent").total()).isNotNull();
    assertThat(rows.get("sales_reconciliation_difference").total())
        .isEqualByComparingTo("604.7852");
    assertThat(rows.get("food_theoretical_cogs").total()).isNull();
    assertThat(rows.get("beverage_theoretical_cogs").total()).isNull();
    assertThat(rows.get("total_theoretical_cogs_percent").total()).isNull();
    assertThat(rows.get("total_known_theoretical_cost").total()).isEqualByComparingTo("18914.74");
    assertThat(rows.get("total_cost_coverage_lines").total()).isLessThan(BigDecimal.ONE);
    mapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(directory.resolve("real-week-report.json").toFile(), after);
  }

  private void loadLiveCosts() throws Exception {
    int rows = 0;
    try (var input =
        new java.util.zip.GZIPInputStream(
            getClass()
                .getResourceAsStream("/fixtures/ctb/reporting-cost-week-2026-10-26.json.gz"))) {
      for (var day : mapper.readTree(input)) {
        var date = LocalDate.parse(day.path("date").asText());
        var report = day.path("report");
        var parsed =
            new com.goldys.platform.connectors.ctb.CtbSaleItemParser()
                .parse(mapper.writeValueAsBytes(report));
        assertThat(parsed).hasSize(report.path("totalCount").asInt());
        for (var item : parsed) {
          int updated =
              jdbc.update(
                  """
            update source_product_sale_fact set cost_per_unit=?,total_food_cost=?
            where trading_date=? and stock_code=? and quantity_sold=? and amount=? and tax_amount=?
            """,
                  item.costPerUnit(),
                  item.totalFoodCost(),
                  date,
                  item.stockCode(),
                  item.quantitySold(),
                  item.amount(),
                  item.taxAmount());
          assertThat(updated).isEqualTo(1);
          rows++;
        }
      }
    }
    assertThat(rows).isEqualTo(1034);
  }

  private void loadMappings(String name) throws IOException {
    var mappingFile = getClass().getResourceAsStream("/fixtures/ctb/" + name);
    var mappings = mapper.readTree(mappingFile).get("mappings");
    for (var m : mappings)
      jdbc.update(
          """
      insert into product_reporting_mapping(scope,source_system,entity_key,effective_from,sales_nature,stock_bucket,item_role,business_area,lifecycle,reason,actor)
      values (?,?,?,?,?,?,?,?,?,?,'reviewed-real-week-fixture')
      """,
          m.get("scope").asText(),
          m.get("sourceSystem").asText(),
          m.get("entityKey").asText(),
          LocalDate.parse(m.get("effectiveFrom").asText()),
          m.get("salesNature").asText(),
          m.get("stockBucket").asText(),
          m.get("itemRole").asText(),
          m.get("businessArea").asText(),
          m.get("lifecycle").asText(),
          m.get("reason").asText());
  }

  private CSVParser csv(String name) throws IOException {
    return CSVFormat.DEFAULT
        .builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .build()
        .parse(
            new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/fixtures/ctb/" + name)),
                StandardCharsets.UTF_8));
  }

  private BigDecimal decimal(CSVRecord r, String field) {
    return r.get(field).isEmpty() ? null : new BigDecimal(r.get(field));
  }

  private String text(CSVRecord r, String field) {
    return r.get(field).isEmpty() ? null : r.get(field);
  }
}
