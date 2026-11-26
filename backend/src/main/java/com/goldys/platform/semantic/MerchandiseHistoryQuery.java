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
      long observedDays) {}

  List<Coverage> coverage();

  List<Product> products(LocalDate from, LocalDate to, int limit);
}
