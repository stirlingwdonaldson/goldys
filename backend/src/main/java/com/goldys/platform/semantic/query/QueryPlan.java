package com.goldys.platform.semantic.query;

import java.util.List;

/**
 * A versioned query-plan request. Every identifier (dataset, field, relationship, aggregate,
 * operator) must resolve through the registered catalogue; the plan carries no raw SQL.
 */
public record QueryPlan(
    int version,
    String dataset,
    List<String> joins,
    List<SelectItem> select,
    FilterNode filter,
    List<GroupByItem> groupBy,
    List<OrderByItem> orderBy,
    int limit,
    int offset) {

  public QueryPlan {
    select = select == null ? List.of() : List.copyOf(select);
    joins = joins == null ? List.of() : List.copyOf(joins);
    groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
    orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
  }
}
