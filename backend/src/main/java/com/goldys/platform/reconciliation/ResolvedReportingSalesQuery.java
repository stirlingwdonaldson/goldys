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
}
