package com.goldys.platform.widget;

import java.util.Objects;

/** One row in a ranked list: a label plus optional primary/secondary values and a status badge. */
public record RankedItem(String label, String primary, String secondary, String badge) {
  public RankedItem {
    Objects.requireNonNull(label, "label");
  }
}
