package com.goldys.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.support.PostgresContainerConfiguration;
import com.goldys.platform.support.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
@Transactional
class WeeklyReportIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired TestAccounts accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entityManager;
  @Autowired com.goldys.platform.canonical.CanonicalWastageIngest wastage;
  @Autowired com.goldys.platform.canonical.CanonicalStockCountIngest counts;
  @Autowired com.goldys.platform.canonical.CanonicalStocktakeIngest headers;

  @Test
  void loadsCompletedWeeksThroughRealMetricQueries() throws Exception {
    accounts.createOwner("weekly-owner@example.com", "Weekly Owner", "password123");
    var login =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("email", "weekly-owner@example.com")
                    .param("password", "password123"))
            .andExpect(status().isOk())
            .andReturn();
    var session = (MockHttpSession) login.getRequest().getSession(false);
    mvc.perform(get("/api/reports/weekly").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeks.length()").value(4))
        .andExpect(jsonPath("$.weeks[0].rows.length()").value(61));

    var date = java.time.LocalDate.parse("2026-10-26");
    var raw = raw();
    wastage.record(
        new com.goldys.platform.canonical.WastageInput(
            "CTB",
            90001,
            90001,
            date,
            "flour",
            new java.math.BigDecimal("2"),
            new java.math.BigDecimal("20"),
            null,
            null,
            "KG",
            null,
            null,
            raw));
    wastage.record(
        new com.goldys.platform.canonical.WastageInput(
            "CTB",
            90001,
            90002,
            date,
            "flour",
            new java.math.BigDecimal("3"),
            new java.math.BigDecimal("20"),
            "Knockoff",
            null,
            "KG",
            null,
            null,
            raw));
    headers.record(
        new com.goldys.platform.canonical.StocktakeInput(
            "CTB", 90001, date, "Real query test", null, null, null, null, raw));
    counts.record(
        new com.goldys.platform.canonical.StockCountInput(
            "CTB",
            90001,
            1,
            date,
            "flour",
            java.math.BigDecimal.TEN,
            1,
            "FLOUR",
            "KG",
            java.math.BigDecimal.TEN,
            new java.math.BigDecimal("100"),
            null,
            null,
            null,
            raw));

    mvc.perform(
            put("/api/reports/weekly/targets")
                .session(session)
                .cookie(login.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", login.getResponse().getCookie("XSRF-TOKEN").getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"2026-10-26\",\"values\":{\"sales_target\":125000}}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/reports/weekly/targets").session(session).param("week", "2026-10-19"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.id == 'sales_target')].value")
                .value(org.hamcrest.Matchers.contains(120000.0)));
    mvc.perform(get("/api/reports/weekly/targets").session(session).param("week", "2026-11-02"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.id == 'sales_target')].value")
                .value(org.hamcrest.Matchers.contains(125000.0)));
    mvc.perform(
            put("/api/reports/weekly/targets")
                .session(session)
                .cookie(login.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", login.getResponse().getCookie("XSRF-TOKEN").getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"effectiveFrom\":\"2026-10-26\",\"values\":{\"food_variance_target_min\":0.02}}"))
        .andExpect(status().isBadRequest());

    jdbc.update(
        """
        insert into resolved_payment_day
          (trading_date, payment_type_name, amount, tip, payment_count,
           resolution_type, authoritative_source, has_conflict, resolved_at)
        values ('2026-10-26', 'Cash', 80, 0, 2, 'single', 'LIGHTSPEED', false, now()),
               ('2026-10-26', 'Card', 30, 10, 1, 'single', 'LIGHTSPEED', false, now())
        """);
    jdbc.update(
        "insert into resolved_daily_sales(trading_date,total_sales,net_sales,gst,resolution_type,authoritative_source,has_conflict,resolved_at) values ('2026-10-26',1100,1000,100,'single','CTB',false,now())");
    entityManager.flush();
    mvc.perform(
            get("/api/reports/weekly")
                .param("from", "2026-10-26")
                .param("weeks", "1")
                .session(session))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.weeks[0].rows[?(@.id == 'sales_difference')].total")
                .value(org.hamcrest.Matchers.contains(-124000.0)))
        .andExpect(
            jsonPath("$.weeks[0].rows[?(@.id == 'total_wastage_percent')].total")
                .value(org.hamcrest.Matchers.contains(0.1)))
        .andExpect(jsonPath("$.weeks[0].wastage.value").value(100))
        .andExpect(jsonPath("$.weeks[0].wastage.reasonCoverageLines").value(0.5))
        .andExpect(jsonPath("$.weeks[0].wastage.reasonCoverageValue").value(0.6))
        .andExpect(jsonPath("$.weeks[0].stockCounts[0].value").value(100))
        .andExpect(jsonPath("$.weeks[0].stockCounts[0].partial").isEmpty())
        .andExpect(
            jsonPath("$.weeks[0].rows[?(@.id == 'total_wastage')].total")
                .value(org.hamcrest.Matchers.contains(100.0)))
        .andExpect(
            jsonPath("$.weeks[0].rows[?(@.id == 'payments')].total")
                .value(org.hamcrest.Matchers.contains(110.0)))
        .andExpect(
            jsonPath(
                    "$.weeks[0].rows[?(@.id == 'payments')].notes[?(@ =~ /.*authoritative source: LIGHTSPEED.*/)]")
                .isNotEmpty());
  }

  private java.util.UUID raw() {
    var run = java.util.UUID.randomUUID();
    var raw = java.util.UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) values (?,'CTB','weekly-report-test','SUCCESS',now(),1,1)",
        run);
    jdbc.update(
        "insert into raw_record(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,payload_sha256,payload_byte_length,fetcher_identity,fetched_at) values (?,?,'CTB','API','application/json',?,?,1,'test',now())",
        raw,
        run,
        new byte[] {1},
        "0".repeat(64));
    return raw;
  }
}
