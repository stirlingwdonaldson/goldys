package com.goldys.platform.semantic;

/** Source-independent recipe observation; quantities and costs retain their signs. */
public record RecipeOutletAllocation(
    String entityType, int entityId, int outletId, boolean deallocated) {}
