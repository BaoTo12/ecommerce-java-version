package com.ecommerce.common.exception;

import lombok.Getter;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;

/** Edge Case #4: Price changed between cart add-time and checkout. */
@Getter
public class PriceChangedException extends AppException {
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
            + ". Please refresh your cart.",
        HttpStatus.CONFLICT);
    this.currentPrice = currentPrice;
    this.snapshotPrice = snapshotPrice;
  }

}
