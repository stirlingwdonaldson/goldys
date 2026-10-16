package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.ProductSalesInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Complete fetched-day accumulator: source groups are observations, source products are totals. */
final class CtbProductSalesBatch {
  private final LocalDate date;
  private final Map<GroupKey, Observation> observations = new LinkedHashMap<>();

  CtbProductSalesBatch(LocalDate date) {
    this.date = date;
  }

  void add(CtbSaleItem item, UUID rawId) {
    String identity = ProductSalesInput.sourceIdentity(item.stockCode(), item.siteNumber());
    if (identity == null
        || item.uniqueGroupId() == null
        || item.uniqueGroupId().isBlank()
        || item.quantitySold() == null
        || item.amount() == null)
      throw mismatch("Sale item lacks a stable group/product identity or quantity/amount");
    GroupKey key = new GroupKey(identity, item.uniqueGroupId());
    Observation prior = observations.get(key);
    if (prior != null) {
      // Overlapping pages must not add the same observation twice. A changed group within one
      // fetched report is unsafe to choose between: retain raw evidence and reject this batch.
      if (!prior.item.equals(item))
        throw mismatch("Sale item group changed within the fetched report");
      prior.rawIds.add(rawId);
    } else observations.put(key, new Observation(item, new LinkedHashSet<>(List.of(rawId))));
  }

  List<ProductSalesInput> totals() {
    Map<String, ProductSalesInput> totals = new LinkedHashMap<>();
    observations.forEach(
        (key, observation) -> {
          CtbSaleItem item = observation.item;
          List<UUID> ids = List.copyOf(observation.rawIds);
          ProductSalesInput next =
              new ProductSalesInput(
                  "CTB",
                  date,
                  ProductNameKey.normalize(item.stockDescription()),
                  item.quantitySold(),
                  item.amount(),
                  item.stockCode(),
                  ids.getFirst(),
                  key.product,
                  ids);
          totals.merge(key.product, next, this::combine);
        });
    return List.copyOf(totals.values());
  }

  private ProductSalesInput combine(ProductSalesInput a, ProductSalesInput b) {
    Set<UUID> ids = new LinkedHashSet<>(a.contributorRawIds());
    ids.addAll(b.contributorRawIds());
    return new ProductSalesInput(
        "CTB",
        date,
        b.productNameKey(),
        a.quantitySold().add(b.quantitySold()),
        a.amount().add(b.amount()),
        a.stockCode(),
        a.rawRecordId(),
        a.sourceProductRef(),
        List.copyOf(ids));
  }

  private static ConnectorFetchException mismatch(String message) {
    return new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", message);
  }

  private record GroupKey(String product, String group) {}

  private record Observation(CtbSaleItem item, Set<UUID> rawIds) {}
}
