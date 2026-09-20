package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.List;

/** A trading date whose sources disagree ("conflict") or where a source is absent ("missing"). */
public record DailySalesConflict(LocalDate tradingDate, List<SourceTotal> sources, String status) {}
