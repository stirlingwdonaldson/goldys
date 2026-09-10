package com.goldys.platform.canonical;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Example canonical entity (spec Requirement 2 / 7) - placeholder fields, same
 * caveat as CanonicalShift: finalize once the entity-matching design session
 * has happened.
 */
@Entity
@Table(name = "canonical_sale_item")
public class CanonicalSaleItem extends BitemporalEntity {

    private String itemName;
    private Integer quantitySold;
    private BigDecimal amount;

    protected CanonicalSaleItem() {
        super();
    }

    public CanonicalSaleItem(Instant validFrom, Instant recordedAt, String itemName,
                              Integer quantitySold, BigDecimal amount) {
        super(validFrom, recordedAt);
        this.itemName = itemName;
        this.quantitySold = quantitySold;
        this.amount = amount;
    }

    public String getItemName() { return itemName; }
    public Integer getQuantitySold() { return quantitySold; }
    public BigDecimal getAmount() { return amount; }
}
