package com.goldys.platform.semantic;

import com.goldys.platform.semantic.ReportingSalesQuery.Sale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;

/** One shared sales allocation and coverage policy for catalogue metrics and weekly reports. */
public record ClassifiedSalesTotals(
    BigDecimal food,
    BigDecimal beverage,
    BigDecimal other,
    BigDecimal unknown,
    BigDecimal net,
    BigDecimal gross,
    BigDecimal coverage,
    BigDecimal knownFood,
    BigDecimal knownBeverage,
    long lines,
    long missingTax,
    long unresolved) {

  private static BigDecimal sum(List<Sale> sales, Function<Sale, BigDecimal> value) {
    return sales.stream().map(value).filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null);
  }

  private static BigDecimal nature(List<Sale> sales, String nature) {
    if (sales.isEmpty()) return null;
    var selected = sales.stream().filter(s -> nature.equals(s.nature())).toList();
    if (selected.isEmpty()) return BigDecimal.ZERO;
    return sum(selected, Sale::net);
  }

  public static ClassifiedSalesTotals summarize(List<Sale> sales) {
    long missingTax = sales.stream().filter(s -> s.gross() != null && s.net() == null).count();
    long unresolved = sales.stream().filter(s -> s.gross() == null).count();
    BigDecimal eligible =
        sum(
            sales.stream().filter(s -> !s.nature().equals("OTHER")).toList(),
            s -> s.net() == null ? null : s.net().abs());
    BigDecimal classified =
        sum(
            sales.stream()
                .filter(s -> s.nature().equals("FOOD") || s.nature().equals("BEVERAGE"))
                .toList(),
            s -> s.net() == null ? null : s.net().abs());
    BigDecimal coverage = ratio(classified == null ? BigDecimal.ZERO : classified, eligible);
    boolean usable =
        missingTax == 0
            && unresolved == 0
            && classified != null
            && eligible != null
            && (eligible.signum() == 0
                || coverage != null && coverage.compareTo(new BigDecimal("0.95")) >= 0);
    return new ClassifiedSalesTotals(
        usable ? nature(sales, "FOOD") : null,
        usable ? nature(sales, "BEVERAGE") : null,
        nature(sales, "OTHER"),
        nature(sales, "UNKNOWN"),
        sum(sales, Sale::net),
        sum(sales, Sale::gross),
        coverage,
        nature(sales, "FOOD"),
        nature(sales, "BEVERAGE"),
        sales.size(),
        missingTax,
        unresolved);
  }

  private static BigDecimal ratio(BigDecimal part, BigDecimal total) {
    return part == null || total == null || total.signum() == 0
        ? null
        : part.divide(total, 8, RoundingMode.HALF_UP);
  }
}
