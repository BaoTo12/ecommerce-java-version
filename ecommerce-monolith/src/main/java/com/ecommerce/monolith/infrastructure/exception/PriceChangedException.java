package com.ecommerce.monolith.infrastructure.exception;

import java.math.BigDecimal;

/** Edge Case #4: Price changed between cart add-time and checkout. */
public class PriceChangedException extends RuntimeException {
  private final BigDecimal currentPrice;
  private final BigDecimal snapshotPrice;

  public PriceChangedException(String sku, BigDecimal snapshotPrice, BigDecimal currentPrice) {
    super(
        "Price of product '"
            + sku
            + "' changed from "
            + snapshotPrice
            + " to "
            + currentPrice
            + ". Please refresh your cart.");
    this.currentPrice = currentPrice;
    this.snapshotPrice = snapshotPrice;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public BigDecimal getSnapshotPrice() {
    return snapshotPrice;
  }
}
