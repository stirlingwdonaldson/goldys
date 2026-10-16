package com.goldys.platform.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.SavedQueryDetail;
import com.goldys.platform.semantic.query.SelectItem;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Saved-query round trips: create/update/revisions/restore/run, ownership and re-authorization. */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class SavedQueryIntegrationTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final UserRole STAFF =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("STAFF"));

  @Autowired JdbcTemplate jdbc;
  @Autowired SavedQueryService savedQueries;

  @BeforeEach
  void seed() {
    jdbc.update("truncate table saved_query_revision, saved_query, resolved_daily_sales");
    jdbc.update(
        "insert into resolved_daily_sales (trading_date, total_sales, net_sales, gst, "
            + "resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values ('2026-10-01', 1000, 900, 100, 'rule', 'CTB', false, now())");
  }

  private QueryPlan salesPlan() {
    return new QueryPlan(
        1,
        "resolved_daily_sales",
        List.of(),
        List.of(
            new SelectItem("trading_date", null, false),
            new SelectItem("total_sales", null, false)),
        null,
        List.of(),
        List.of(),
        100,
        0);
  }

  @Test
  void createAndRunRoundTrip() {
    SavedQueryDetail created =
        savedQueries.create("owner@example.com", "daily revenue", salesPlan());

    assertThat(savedQueries.list("owner@example.com")).hasSize(1);
    var result = savedQueries.run(OWNER, "owner@example.com", created.id());
    assertThat(result.rows()).hasSize(1);
    assertThat((BigDecimal) result.rows().get(0).get(1)).isEqualByComparingTo("1000.0000");
  }

  @Test
  void updateCreatesNewRevisionAndRestoreRollsBack() {
    SavedQueryDetail created = savedQueries.create("owner@example.com", "v1", salesPlan());

    QueryPlan v2Plan =
        new QueryPlan(
            1,
            "resolved_daily_sales",
            List.of(),
            List.of(
                new SelectItem("trading_date", null, false),
                new SelectItem("net_sales", null, false)),
            null,
            List.of(),
            List.of(),
            100,
            0);
    savedQueries.update("owner@example.com", created.id(), "v2", v2Plan);

    assertThat(savedQueries.revisions("owner@example.com", created.id())).hasSize(2);
    assertThat(savedQueries.get("owner@example.com", created.id()).revision()).isEqualTo(2);

    SavedQueryDetail restored = savedQueries.restore("owner@example.com", created.id(), 1);
    assertThat(restored.revision()).isEqualTo(3);
    assertThat(restored.plan().select().get(1).field()).isEqualTo("total_sales");
  }

  @Test
  void otherUsersQueryIsDenied() {
    SavedQueryDetail created = savedQueries.create("owner@example.com", "mine", salesPlan());
    assertThatThrownBy(() -> savedQueries.get("other@example.com", created.id()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void runReauthorizesOperands() {
    // A query over the invoice dataset (inventory.cost) — STAFF lacks that permission.
    QueryPlan invoicePlan =
        new QueryPlan(
            1,
            "invoice",
            List.of(),
            List.of(new SelectItem("invoice_number", null, false)),
            null,
            List.of(),
            List.of(),
            100,
            0);
    SavedQueryDetail created = savedQueries.create("owner@example.com", "invoices", invoicePlan);

    assertThatThrownBy(() -> savedQueries.run(STAFF, "owner@example.com", created.id()))
        .isInstanceOf(AccessDeniedException.class);
  }
}
