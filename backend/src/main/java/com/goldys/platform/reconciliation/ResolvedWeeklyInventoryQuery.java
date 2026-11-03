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
        select observation_date, reason, count(*) as lines, count(source_value) as valued_lines, sum(source_value) as value
        from resolved_inventory_observation where kind='WASTE' and observation_date between ? and ?
        group by observation_date, reason order by observation_date, reason
        """,
        (rs, n) ->
            new Waste(
                rs.getObject("observation_date", LocalDate.class),
                rs.getString("reason"),
                rs.getLong("lines"),
                rs.getLong("valued_lines"),
                rs.getBigDecimal("value")),
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
