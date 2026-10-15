package com.goldys.platform.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One forecasted day: the median plus the {@code p10}/{@code p90} deciles, the food/bev split, the
 * seasonal-naive baseline, the booked covers on the book, and the external driver flags (holidays,
 * school/university, weather). Every field after {@code median} is nullable: a missing decile or a
 * driver whose source was unavailable degrades to {@code null}, never a failed point.
 */
public record ForecastSeriesPointDto(
    LocalDate date,
    BigDecimal median,
    BigDecimal p10,
    BigDecimal p90,
    BigDecimal food,
    BigDecimal bev,
    BigDecimal baseline,
    BigDecimal bookedCovers,
    Boolean publicHoliday,
    String publicHolidayName,
    Boolean publicHolidayEve,
    Boolean dayAfterPublicHoliday,
    Boolean schoolHoliday,
    Boolean uniInSession,
    BigDecimal serviceTempMaxC,
    BigDecimal serviceRainMm,
    String weatherSource) {}
