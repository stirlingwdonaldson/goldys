package com.goldys.platform.semantic.query;

/** A group-by item: a field and, for DATE fields, an optional time grain (default DAY). */
public record GroupByItem(String field, Grain grain) {

  public GroupByItem {
    grain = grain == null ? Grain.DAY : grain;
  }
}
