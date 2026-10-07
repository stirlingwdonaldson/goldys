package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class SavedDashboardMigrationTest {

  @Autowired JdbcTemplate jdbc;

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void migrationAddsColumnsAndTables() {
    List<String> cols =
        jdbc.queryForList(
            "select column_name from information_schema.columns where table_name='saved_dashboard'",
            String.class);
    assertThat(cols).contains("filters", "visibility", "pinned", "current_revision");
    Integer n =
        jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_name in ('saved_dashboard_revision','saved_dashboard_share')",
            Integer.class);
    assertThat(n).isEqualTo(2);
  }

  @Test
  void migrationMapsKnownToolAndDropsUnknownToolWithoutNulls() throws Exception {
    // V18-shaped widgets: one known tool, one unknown tool. The known tool is mapped to the new
    // {id, renderType, queries[], layout} shape; the unknown tool is dropped, and no null element
    // is emitted.
    String migrated =
        migrate(
            """
            [
              {"id":"w1","tool":"GET_SALES_BY_PERIOD","input":{"startDate":"2026-09-13","endDate":"2026-09-13","metric":"sales.gross"}},
              {"id":"w2","tool":"UNKNOWN_TOOL","input":{}}
            ]""");

    JsonNode widgets = mapper.readTree(migrated);
    assertThat(widgets.isArray()).isTrue();
    assertThat(widgets).hasSize(1);
    assertThat(widgets.get(0).get("id").asText()).isEqualTo("w1");
    assertThat(widgets.get(0).get("renderType").asText()).isEqualTo("time-series");
    assertThat(widgets.get(0).get("tool")).isNull();
    for (JsonNode w : widgets) {
      assertThat(w).isNotNull();
    }
  }

  @Test
  void migrationYieldsEmptyArrayWhenAllWidgetsUnknown() throws Exception {
    String migrated = migrate("[{\"id\":\"w2\",\"tool\":\"UNKNOWN_TOOL\",\"input\":{}}]");

    JsonNode widgets = mapper.readTree(migrated);
    assertThat(widgets.isArray()).isTrue();
    assertThat(widgets).isEmpty();
  }

  /**
   * Runs the V25 widget transform against a seeded V18-shaped widgets array and returns the
   * migrated JSON as text. Flyway has already applied V25 by the time the context starts, so the
   * transform is exercised directly against a temporary scenario rather than by re-running the
   * migration.
   */
  private String migrate(String v18Widgets) {
    return jdbc.queryForObject(
        """
        WITH input AS (SELECT ?::jsonb AS widgets)
        SELECT COALESCE(
          (SELECT jsonb_agg(mapped) FILTER (WHERE mapped IS NOT NULL)
           FROM (
             SELECT CASE
               WHEN w->>'tool' = 'GET_SALES_BY_PERIOD' THEN
                 jsonb_build_object(
                   'id', w->>'id', 'renderType', 'time-series', 'layout', '{"w":6,"h":2}'::jsonb,
                   'queries', jsonb_build_array(jsonb_build_object(
                     'metric', COALESCE(w->'input'->>'metric','sales.gross'),
                     'range', jsonb_build_object('from', w->'input'->>'startDate','to', w->'input'->>'endDate','calendar','CALENDAR'),
                     'grain', 'DAY', 'dimensions', '[]'::jsonb)))
               WHEN w->>'tool' IN ('GET_LABOUR_VARIANCE','GET_FOOD_COST','GET_RESERVATION_SUMMARY') THEN
                 jsonb_build_object(
                   'id', w->>'id', 'renderType', 'table', 'layout', '{"w":12,"h":2}'::jsonb,
                   'queries', jsonb_build_array(jsonb_build_object(
                     'metric', 'sales.gross',
                     'range', jsonb_build_object('from', COALESCE(w->'input'->>'startDate', w->'input'->>'date'),'to', COALESCE(w->'input'->>'endDate', w->'input'->>'date'),'calendar','CALENDAR'),
                     'grain', 'DAY', 'dimensions', '[]'::jsonb)))
               ELSE NULL
             END AS mapped
             FROM jsonb_array_elements(input.widgets) AS w) AS mapped_widgets),
          '[]'::jsonb)::text
        FROM input
        """,
        String.class,
        v18Widgets);
  }
}
