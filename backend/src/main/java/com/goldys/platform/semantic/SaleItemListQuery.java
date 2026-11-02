package com.goldys.platform.semantic;

import java.time.LocalDate;

public interface SaleItemListQuery {
  DataPage<SaleItemRow> page(
      String category, String saleNumber, LocalDate from, LocalDate to, int page, int size);
}
