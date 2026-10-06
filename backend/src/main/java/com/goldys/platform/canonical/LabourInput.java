package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A normalized labour fact from one source, to be recorded as a canonical version. */
public record LabourInput(
    String sourceSystem,
    String sourceRecordRef,
    String staffRef,
    String department,
    LocalDate labourDate,
    BigDecimal scheduledHours,
    BigDecimal actualHours,
    BigDecimal scheduledCost,
    BigDecimal actualCost,
    Instant shiftStart,
    Instant shiftEnd,
    UUID rawRecordId) {}
