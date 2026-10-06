package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.ProductSalesRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Projects a product/day's exception whenever a canonical product-sales fact is recorded. */
@Component
public class ProductSalesProjectionListener {
  private final ProductSalesProjector projector;

  public ProductSalesProjectionListener(ProductSalesProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(ProductSalesRecorded event) {
    projector.recompute(event.productNameKey(), event.tradingDate());
  }
}
