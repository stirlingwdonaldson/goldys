package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeMetricsQuery;
import com.goldys.platform.semantic.SaleCosting;
import com.goldys.platform.semantic.SaleCostingQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SQL views expose current source versions; recipe observations are selected by the existing
 * resolver.
 */
@Service
public class ResolvedSaleCostingQuery implements SaleCostingQuery {
  private static final int MAX_LINES = 10000;
  private final JdbcTemplate jdbc;
  private final RecipeMetricsQuery recipes;

  public ResolvedSaleCostingQuery(JdbcTemplate jdbc, RecipeMetricsQuery recipes) {
    this.jdbc = jdbc;
    this.recipes = recipes;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SaleCosting costing(String saleNumber, LocalDate from, LocalDate to) {
    var links =
        jdbc.query(
            "select * from resolved_sale_recipe_link",
            (rs, row) ->
                new SaleRecipeMatcher.Link(
                    rs.getString("sale_item_stock_description"),
                    rs.getInt("recipe_id"),
                    rs.getObject("is_recipe_active", Boolean.class)));
    var matcher = new SaleRecipeMatcher(recipes.costing(), links);
    List<SaleCosting.Line> lines =
        jdbc.query(
            """
        select receipt_line_id, item_name, quantity_sold, amount,
               (adjustment->>'finalExTax')::numeric as revenue_ex_tax from resolved_sale_line
        where sale_number=? and trading_date between ? and ?
        order by trading_date, receipt_line_id limit ?
        """,
            (rs, row) ->
                cost(
                    rs.getString("receipt_line_id"),
                    rs.getString("item_name"),
                    rs.getBigDecimal("quantity_sold"),
                    rs.getBigDecimal("amount"),
                    rs.getBigDecimal("revenue_ex_tax"),
                    matcher),
            saleNumber,
            from,
            to,
            MAX_LINES + 1);
    boolean complete =
        !lines.isEmpty()
            && lines.size() <= MAX_LINES
            && lines.stream().allMatch(l -> l.status().equals("CURRENT_RECIPE_ESTIMATE"));
    BigDecimal total =
        complete
            ? lines.stream()
                .map(SaleCosting.Line::recipeCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : null;
    // Source tax/effective cost bases remain unverified; never manufacture historical actual GP.
    return new SaleCosting(
        lines.stream().limit(MAX_LINES).toList(),
        complete,
        total,
        null,
        null,
        lines.size() > MAX_LINES
            ? "SALE_TOO_LARGE"
            : complete ? "TAX_BASIS_UNVERIFIED" : "INCOMPLETE_COSTING");
  }

  private static SaleCosting.Line cost(
      String id,
      String name,
      BigDecimal quantity,
      BigDecimal revenueIncTax,
      BigDecimal revenueExTax,
      SaleRecipeMatcher matcher) {
    var match = matcher.match(name);
    var r = match.recipe();
    BigDecimal unit = r == null ? null : r.costPerPortion();
    String status = match.status();
    if (r != null) {
      if (quantity == null || unit == null || unit.signum() < 0) status = "COST_MISSING";
      else if (r.capturedAt() == null) status = "COST_DATE_UNKNOWN";
      else if (r.stale()) status = "STALE_COST";
      else status = "CURRENT_RECIPE_ESTIMATE";
    }
    return new SaleCosting.Line(
        id,
        name,
        quantity,
        revenueIncTax,
        revenueExTax,
        r == null ? null : r.recipeId(),
        r == null ? null : r.name(),
        match.method(),
        unit,
        unit == null || quantity == null ? null : unit.multiply(quantity),
        r == null ? null : r.capturedAt(),
        r == null ? null : r.costSource(),
        status);
  }
}
