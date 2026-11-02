package com.goldys.platform.semantic;

import java.time.LocalDate;

public interface ReceiptGroupQuery {
  ReceiptGroup group(String receiptNumber, LocalDate from, LocalDate to);
}
