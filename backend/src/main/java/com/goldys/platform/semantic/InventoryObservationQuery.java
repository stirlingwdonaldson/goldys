package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;

/** Paged reads of the resolved observation projection, never stock balance metrics. */
public interface InventoryObservationQuery {
  List<InventoryObservation> observations(
      LocalDate from, LocalDate to, String kind, int offset, int limit);
}
