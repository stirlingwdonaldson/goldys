package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.MerchandiseHistoryQuery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResolvedMerchandiseHistoryQuery implements MerchandiseHistoryQuery {
  private final JdbcTemplate jdbc;

  public ResolvedMerchandiseHistoryQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Coverage> coverage() {
    return jdbc.query(
        """
        select 'Lightspeed merchandise lines' as dataset, min(trading_date) as first_date,
          max(trading_date) as last_date, count(distinct trading_date) as days, count(*) as rows
        from resolved_sale_line where source_system = 'LIGHTSPEED'
        union all
        select 'Reconciled product/day metrics', min(trading_date), max(trading_date),
          count(distinct trading_date), count(*) from resolved_product_sales
        """,
        (rs, i) ->
            new Coverage(
                rs.getString("dataset"),
                rs.getObject("first_date", LocalDate.class),
                rs.getObject("last_date", LocalDate.class),
                rs.getLong("days"),
                rs.getLong("rows")));
  }

  private static final String FILTER =
      """
      from resolved_sale_line
      where source_system = 'LIGHTSPEED' and trading_date between ? and ?
        and (?::text is null or position(lower(?) in lower(coalesce(item_name, ''))) > 0)
      """;
  private static final String GROUPING =
      " group by site_number, product_number, sku, item_name, category_name ";

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<Product> products(LocalDate from, LocalDate to, int limit) {
    return products(from, to, null, 0, limit).items();
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ProductPage products(
      LocalDate from, LocalDate to, String productName, int page, int limit) {
    long matchingGroups =
        jdbc.queryForObject(
            "select count(*) from (select 1 " + FILTER + GROUPING + ") matched",
            Long.class,
            from,
            to,
            productName,
            productName);
    var rows =
        jdbc.query(
            """
        select site_number, product_number, sku, item_name, category_name,
          case when count(quantity_sold) = count(*) then sum(quantity_sold) end as quantity,
          case when count(amount) = count(*) then sum(amount) end as amount,
          count(distinct trading_date) as days, min(trading_date) as first_date,
          max(trading_date) as last_date, count(*) as lines
        """
                + FILTER
                + GROUPING
                + """
        order by amount desc nulls last, site_number nulls last, product_number nulls last,
          sku nulls last, item_name nulls last, category_name nulls last
        limit ? offset ?
        """,
            (rs, i) ->
                new Product(
                    rs.getObject("site_number", Long.class),
                    rs.getString("product_number"),
                    rs.getString("sku"),
                    rs.getString("item_name"),
                    rs.getString("category_name"),
                    rs.getBigDecimal("quantity"),
                    rs.getBigDecimal("amount"),
                    rs.getLong("days"),
                    rs.getObject("first_date", LocalDate.class),
                    rs.getObject("last_date", LocalDate.class),
                    rs.getLong("lines")),
            from,
            to,
            productName,
            productName,
            limit,
            (long) page * limit);
    return new ProductPage(rows, matchingGroups, page, limit);
  }
}
