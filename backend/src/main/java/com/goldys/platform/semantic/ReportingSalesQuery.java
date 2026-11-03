package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Source-matched ex-tax observations and reviewed dimensions; unknown bases remain null. */
public interface ReportingSalesQuery {
  record Sale(
      LocalDate date,
      String name,
      BigDecimal gross,
      BigDecimal net,
      String source,
      String nature,
      BigDecimal knownCost,
      long costLines,
      long costedLines) {
    public Sale(
        LocalDate date,
        String name,
        BigDecimal gross,
        BigDecimal net,
        String source,
        String nature) {
      this(date, name, gross, net, source, nature, null, 1, 0);
    }
  }

  List<Sale> sales(LocalDate from, LocalDate to);

  /** Cost observations additionally require inventory.cost READ at the application boundary. */
  default List<Sale> salesWithCosts(LocalDate from, LocalDate to) {
    return sales(from, to);
  }

  default List<FoodBevSplit> dailyFoodBev(LocalDate from, LocalDate to) {
    var days = new java.util.LinkedHashMap<LocalDate, java.util.List<Sale>>();
    for (var sale : sales(from, to))
      days.computeIfAbsent(sale.date(), d -> new java.util.ArrayList<>()).add(sale);
    return days.entrySet().stream()
        .map(
            e -> {
              var total = ClassifiedSalesTotals.summarize(e.getValue());
              return new FoodBevSplit(e.getKey(), total.food(), total.beverage());
            })
        .toList();
  }
}
