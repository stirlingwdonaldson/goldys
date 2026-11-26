package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Observed Lightspeed merchandise, separate from reconciled product/day metrics. */
public interface MerchandiseHistoryQuery {
  record Coverage(
      String dataset, LocalDate firstDate, LocalDate lastDate, long observedDays, long rows) {}

  record Product(
      Long site,
      String productNumber,
      String sku,
      String name,
      String category,
      BigDecimal quantity,
      BigDecimal amount,
      long observedDays,
      LocalDate firstDate,
      LocalDate lastDate,
      long lines) {}

  record ProductPage(List<Product> items, long matchingGroups, int page, int limit) {
    public ProductPage {
      items = List.copyOf(items);
    }

    public boolean hasMore() {
      return (long) (page + 1) * limit < matchingGroups;
    }
  }

  List<Coverage> coverage();

  List<Product> products(LocalDate from, LocalDate to, int limit);

  /** Case-insensitive literal name substring, applied before grouping and pagination. */
  ProductPage products(LocalDate from, LocalDate to, String productName, int page, int limit);
}
