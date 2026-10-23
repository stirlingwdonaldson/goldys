package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeOutletAllocation;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
record RecipeOutletAllocationFact(
    @Column(name = "entity_type", columnDefinition = "text") String entityType,
    @Column(name = "entity_id") int entityId,
    @Column(name = "outlet_id") int outletId,
    @Column(name = "deallocated") boolean deallocated) {
  static RecipeOutletAllocationFact from(RecipeOutletAllocation value) {
    return new RecipeOutletAllocationFact(
        value.entityType(), value.entityId(), value.outletId(), value.deallocated());
  }

  RecipeOutletAllocation toValue() {
    return new RecipeOutletAllocation(entityType, entityId, outletId, deallocated);
  }
}
