package com.goldys.platform.canonical;

import java.time.LocalDate;

/** Published after a canonical labour fact is recorded, so the labour projector can update the
 * affected date's resolved projection in the same transaction. */
public record LabourRecorded(LocalDate labourDate) {}
