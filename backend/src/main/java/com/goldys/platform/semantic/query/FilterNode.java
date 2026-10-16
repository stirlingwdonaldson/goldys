package com.goldys.platform.semantic.query;

import java.util.List;

/**
 * A filter tree node: an internal {@code AND}/{@code OR}/{@code NOT} (via {@code logic} + {@code
 * children}), or a leaf {@link Predicate}. Exactly one of {@code logic}/{@code predicate} is set.
 */
public record FilterNode(String logic, List<FilterNode> children, Predicate predicate) {

  public FilterNode {
    children = children == null ? List.of() : List.copyOf(children);
  }

  public static FilterNode leaf(Predicate predicate) {
    return new FilterNode(null, List.of(), predicate);
  }

  public static FilterNode and(List<FilterNode> children) {
    return new FilterNode("AND", children, null);
  }

  public static FilterNode or(List<FilterNode> children) {
    return new FilterNode("OR", children, null);
  }

  public static FilterNode not(FilterNode child) {
    return new FilterNode("NOT", List.of(child), null);
  }

  public boolean isLeaf() {
    return predicate != null;
  }
}
