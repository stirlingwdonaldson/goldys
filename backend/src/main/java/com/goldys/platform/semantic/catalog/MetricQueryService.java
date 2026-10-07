package com.goldys.platform.semantic.catalog;

/** The controlled query interface. Permission-agnostic; enforcement lives in consumers. */
public interface MetricQueryService {
  MetricResult query(MetricQuery query);
}
