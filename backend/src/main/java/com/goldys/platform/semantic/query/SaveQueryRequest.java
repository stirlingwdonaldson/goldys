package com.goldys.platform.semantic.query;

/** Body for creating or updating a saved query. */
public record SaveQueryRequest(String title, QueryPlan plan) {}
