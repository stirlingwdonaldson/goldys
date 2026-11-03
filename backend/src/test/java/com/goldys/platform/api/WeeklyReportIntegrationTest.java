package com.goldys.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        .andExpect(jsonPath("$.weeks[0].rows.length()").value(59));

    jdbc.update(
        """
        insert into resolved_payment_day
          (trading_date, payment_type_name, amount, tip, payment_count,
           resolution_type, authoritative_source, has_conflict, resolved_at)
        values ('2026-10-26', 'Cash', 80, 0, 2, 'single', 'LIGHTSPEED', false, now()),
               ('2026-10-26', 'Card', 30, 10, 1, 'single', 'LIGHTSPEED', false, now())
        """);
    mvc.perform(
            get("/api/reports/weekly")
                .param("from", "2026-10-26")
                .param("weeks", "1")
                .session(session))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.weeks[0].rows[?(@.id == 'payments')].total")
                .value(org.hamcrest.Matchers.contains(110.0)))
        .andExpect(
            jsonPath(
                    "$.weeks[0].rows[?(@.id == 'payments')].notes[?(@ =~ /.*authoritative source: LIGHTSPEED.*/)]")
                .isNotEmpty());
  }
}
