package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trading date's resolved sales split between food and beverage groups. A side is {@code 0}
 * when no product of that group sold that day (a real zero), and {@code null} when that side is
 * unresolved (a product in the group had no resolved amount that day).
 */
public record FoodBevSplit(LocalDate date, BigDecimal food, BigDecimal bev) {}
