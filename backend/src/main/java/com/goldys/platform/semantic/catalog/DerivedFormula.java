package com.goldys.platform.semantic.catalog;

/** Internal bounded arithmetic definition; operands are ordered, never a free-form expression. */
record DerivedFormula(Operation operation, MetricId left, MetricId right) {
  enum Operation {
    RATIO,
    DIFFERENCE
  }
}
