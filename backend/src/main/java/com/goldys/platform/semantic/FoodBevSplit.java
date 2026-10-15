package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trading date's resolved sales split between food and beverage groups. {@code food}/{@code
 * bev} are null when that side of the split has no resolved amount for the day (e.g. no food-group
 * product sales resolved that day).
 */
public record FoodBevSplit(LocalDate date, BigDecimal food, BigDecimal bev) {}
