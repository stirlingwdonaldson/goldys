package com.goldys.platform.semantic;

import java.time.LocalDate;

public interface SaleCostingQuery {
  SaleCosting costing(String saleNumber, LocalDate from, LocalDate to);
}
