package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.ReportingSalesQuery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ResolvedReportingSalesQuery implements ReportingSalesQuery {
  private final JdbcTemplate jdbc;

  public ResolvedReportingSalesQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Sale> sales(LocalDate from, LocalDate to) {
    return jdbc.query(
        """
      select * from resolved_reporting_sales where trading_date between ? and ?
      order by trading_date, product_name_key
      """,
        (rs, n) ->
            new Sale(
                rs.getObject("trading_date", LocalDate.class),
                rs.getString("product_name_key"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("net_amount"),
                rs.getString("authoritative_source"),
                rs.getString("sales_nature")),
        from,
        to);
  }

  // Materialize each bounded basis once: inlining both views can multiply lateral policy lookups.
  @Override
  public List<Sale> salesWithCosts(LocalDate from, LocalDate to) {
    return jdbc.query(
        """
      with sales as materialized (
        select * from resolved_reporting_sales where trading_date between ? and ?
      ), costs as materialized (
        select * from resolved_reporting_cost where trading_date between ? and ?
      )
      select s.*, c.known_cost, coalesce(c.cost_lines,1) cost_lines,
             coalesce(c.costed_lines,0) costed_lines
      from sales s left join costs c
        on c.trading_date=s.trading_date and c.product_name_key=s.product_name_key
        and s.net_amount is not null
      order by s.trading_date,s.product_name_key
      """,
        (rs, n) ->
            new Sale(
                rs.getObject("trading_date", LocalDate.class),
                rs.getString("product_name_key"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("net_amount"),
                rs.getString("authoritative_source"),
                rs.getString("sales_nature"),
                rs.getBigDecimal("known_cost"),
                rs.getLong("cost_lines"),
                rs.getLong("costed_lines")),
        from,
        to,
        from,
        to);
  }
}
