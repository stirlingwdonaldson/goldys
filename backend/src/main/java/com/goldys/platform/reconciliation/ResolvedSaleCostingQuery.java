package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeMetricsQuery;
import com.goldys.platform.semantic.SaleCosting;
import com.goldys.platform.semantic.SaleCostingQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
  private final boolean costsExcludeGst;

  public ResolvedSaleCostingQuery(
      JdbcTemplate jdbc,
      RecipeMetricsQuery recipes,
      @Value("${ctb.recipes.costs-exclude-gst:true}") boolean costsExcludeGst) {
    this.jdbc = jdbc;
    this.recipes = recipes;
    this.costsExcludeGst = costsExcludeGst;
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
               coalesce((adjustment->>'finalExTax')::numeric, amount - total_tax) as revenue_ex_tax from resolved_sale_line
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
    return summarise(lines, costsExcludeGst);
  }

  private static SaleCosting summarise(List<SaleCosting.Line> lines, boolean costsExcludeGst) {
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
    boolean revenueKnown = lines.stream().allMatch(l -> l.revenueExTax() != null);
    BigDecimal revenue =
        revenueKnown
            ? lines.stream()
                .map(SaleCosting.Line::revenueExTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : null;
    // Owner assumption (23 Oct 2026): CTB costs exclude GST; margins are current-recipe estimates.
    BigDecimal profit =
        complete && costsExcludeGst && revenueKnown ? revenue.subtract(total) : null;
    BigDecimal margin =
        profit != null && revenue.signum() != 0
            ? profit.divide(revenue, 8, RoundingMode.HALF_UP)
            : null;
    String qualification = "EX_GST_COST_ASSUMED";
    if (!complete) qualification = "INCOMPLETE_COSTING";
    else if (!costsExcludeGst) qualification = "TAX_BASIS_UNVERIFIED";
    else if (!revenueKnown) qualification = "REVENUE_MISSING";
    else if (revenue.signum() == 0) qualification = "ZERO_NET_REVENUE";
    return new SaleCosting(
        lines.stream().limit(MAX_LINES).toList(),
        complete,
        total,
        profit,
        margin,
        lines.size() > MAX_LINES ? "SALE_TOO_LARGE" : qualification,
        costsExcludeGst ? "ASSUMED_EX_GST" : "UNVERIFIED");
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
      else if (quantity.signum() != 0
          && revenueIncTax != null
          && revenueIncTax.signum() != 0
          && quantity.signum() != revenueIncTax.signum()) status = "QUANTITY_REVENUE_MISMATCH";
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
