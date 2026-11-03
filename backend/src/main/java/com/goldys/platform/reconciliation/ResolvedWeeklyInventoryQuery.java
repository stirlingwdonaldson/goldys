package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.WeeklyInventoryQuery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ResolvedWeeklyInventoryQuery implements WeeklyInventoryQuery {
  private final JdbcTemplate jdbc;

  public ResolvedWeeklyInventoryQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Waste> wastage(LocalDate from, LocalDate to) {
    return jdbc.query(
        """
        select wastage_date as observation_date, reason, sales_nature, count(*) as lines,
               count(quantity*source_unit_cost) as valued_lines,
               sum(round(quantity*source_unit_cost,4)) as value
        from resolved_classified_wastage where wastage_date between ? and ?
        group by wastage_date, reason, sales_nature order by wastage_date, reason, sales_nature
        """,
        (rs, n) ->
            new Waste(
                rs.getObject("observation_date", LocalDate.class),
                rs.getString("reason"),
                rs.getLong("lines"),
                rs.getLong("valued_lines"),
                rs.getBigDecimal("value"),
                rs.getString("sales_nature")),
        from,
        to);
  }

  @Override
  public List<StockCount> stockCounts(LocalDate from, LocalDate to) {
    return jdbc.query(
        """
        select observation_date, parent_source_id, count(*) as lines, count(source_value) as valued_lines,
               sum(source_value) as value, bool_or(stocktake_partial) as partial, bool_and(stocktake_locked) as locked
        from resolved_inventory_observation where kind='COUNT' and observation_date between ? and ?
        group by observation_date, parent_source_id order by observation_date, parent_source_id
        """,
        (rs, n) ->
            new StockCount(
                rs.getObject("observation_date", LocalDate.class),
                rs.getInt("parent_source_id"),
                rs.getLong("lines"),
                rs.getLong("valued_lines"),
                rs.getBigDecimal("value"),
                rs.getObject("partial", Boolean.class),
                rs.getObject("locked", Boolean.class)),
        from,
        to);
  }
}
