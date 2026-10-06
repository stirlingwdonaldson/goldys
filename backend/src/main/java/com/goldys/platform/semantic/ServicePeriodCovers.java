package com.goldys.platform.semantic;

import java.time.LocalDate;

/** One date/service-period's resolved covers. */
public record ServicePeriodCovers(LocalDate date, String servicePeriod, long covers) {}
