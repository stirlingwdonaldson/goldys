package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeDefinition;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "canonical_recipe_definition")
class CanonicalRecipeDefinition extends BitemporalEntity {
  @Embedded private RecipeHeaderFact header;

  @Column(name = "stocks_raw_record_id", nullable = false, updatable = false)
  private UUID stocksRawRecordId;

  @Column(name = "usages_raw_record_id", nullable = false, updatable = false)
  private UUID usagesRawRecordId;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "canonical_recipe_definition_stocks",
      joinColumns = @JoinColumn(name = "snapshot_id"))
  @OrderColumn(name = "line_order")
  private List<RecipeStockFact> stocks;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "canonical_recipe_definition_usages",
      joinColumns = @JoinColumn(name = "snapshot_id"))
  @OrderColumn(name = "line_order")
  private List<RecipeUsageFact> usages;

  protected CanonicalRecipeDefinition() {}

  CanonicalRecipeDefinition(
      RecipeDefinition value,
      UUID rawRecordId,
      UUID stocksRawRecordId,
      UUID usagesRawRecordId,
      Instant recordedAt) {
    super(
        UUID.nameUUIDFromBytes(
            ("recipe-definition:" + value.header().recipeId()).getBytes(StandardCharsets.UTF_8)),
        "CTB",
        "recipe-definition:" + value.header().recipeId(),
        rawRecordId,
        value.observedAt(),
        recordedAt);
    this.header = RecipeHeaderFact.from(value.header());
    this.stocksRawRecordId = stocksRawRecordId;
    this.usagesRawRecordId = usagesRawRecordId;
    this.stocks = value.stocks().stream().map(RecipeStockFact::from).toList();
    this.usages = value.usages().stream().map(RecipeUsageFact::from).toList();
  }

  RecipeDefinition toValue() {
    return new RecipeDefinition(
        header.toValue(),
        stocks.stream().map(RecipeStockFact::toValue).toList(),
        usages.stream().map(RecipeUsageFact::toValue).toList(),
        validFrom(),
        List.of(rawRecordId(), stocksRawRecordId, usagesRawRecordId));
  }
}
