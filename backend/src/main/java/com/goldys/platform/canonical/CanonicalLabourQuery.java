package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical labour facts, so modules outside this package never touch
 * the package-private entity or repository directly.
 */
@Service
public class CanonicalLabourQuery {
  private final CanonicalLabourEntryRepository repository;

  public CanonicalLabourQuery(CanonicalLabourEntryRepository repository) {
    this.repository = repository;
  }

  /** All current labour facts, mapped to views. */
  public List<LabourView> currentLabour() {
    return repository.findAllCurrent().stream().map(CanonicalLabourQuery::toView).toList();
  }

  /** Current labour facts whose labour date falls in {@code dates}. */
  public List<LabourView> currentLabourForDates(Collection<LocalDate> dates) {
    return currentLabour().stream().filter(v -> dates.contains(v.labourDate())).toList();
  }

  private static LabourView toView(CanonicalLabourEntry e) {
    return new LabourView(
        e.sourceSystem(),
        e.department(),
        e.labourDate(),
        e.scheduledHours(),
        e.actualHours(),
        e.scheduledCost(),
        e.actualCost());
  }
}
