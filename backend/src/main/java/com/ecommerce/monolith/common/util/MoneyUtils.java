package com.ecommerce.monolith.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Edge Case #22 — Money Arithmetic Precision
 *
 * <p>Problem: Floating point arithmetic is WRONG for money calculations.
 *
 * <p>double a = 0.1 + 0.2; // = 0.30000000000000004 (NOT 0.3!) double tax = 999.99 * 0.1;// =
 * 99.99899999999999 (should be 100.00)
 *
 * <p>The "split-cent" problem: Order total: $10.00 Tax rate: 10% Raw tax: $1.000 (exact) But if
 * total is $10.005: Tax raw = $1.0005 Round to 2dp = $1.00 or $1.01? If you use ROUND_DOWN
 * everywhere, you systematically undercharge tax. If you use ROUND_UP, you systematically
 * overcharge. HALF_UP is the legally mandated rounding mode in most financial contexts.
 *
 * <p>Three categories of errors without this class: 1. Floating point representation errors (above)
 * 2. Inconsistent rounding (one place rounds up, another rounds down) 3. Accumulated error (many
 * small rounding errors in a for-loop sum to $0.01+)
 *
 * <p>Rule: NEVER use double/float for money. ALWAYS use BigDecimal. Rule: ALWAYS specify scale (2
 * for currency) and RoundingMode explicitly. Rule: Centralize all money operations in this utility
 * class.
 */
public final class MoneyUtils {

  public static final BigDecimal HUNDRED = new BigDecimal("100");
  public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  /** Standard currency scale: 2 decimal places (cents) */
  public static final int CURRENCY_SCALE = 2;

  /** Financial standard: round half away from zero */
  public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  private MoneyUtils() {}

  /**
   * Round a BigDecimal to standard 2 decimal places (currency precision). Always use this — never
   * call .setScale() directly in business code.
   */
  public static BigDecimal round(BigDecimal amount) {
    return amount.setScale(CURRENCY_SCALE, ROUNDING_MODE);
  }

  /**
   * Calculate percentage: what is pct% of amount? e.g. percentage(123.456, 10) → 12.35 (HALF_UP)
   *
   * <p>Never: amount * pct / 100 with doubles Always: intermediate result at higher precision, then
   * round at the end
   */
  public static BigDecimal percentage(BigDecimal amount, BigDecimal percentageRate) {
    return round(
        amount
            .multiply(percentageRate)
            .divide(HUNDRED, 6, ROUNDING_MODE) // high intermediate precision
        );
  }

  /**
   * Tax calculation. Supports inclusive tax (tax already in price) and exclusive tax (tax added on
   * top).
   *
   * <p>Inclusive (VAT-inclusive price): tax = price * rate / (1 + rate) Exclusive: tax = price *
   * rate
   */
  public static BigDecimal calculateTax(
      BigDecimal netAmount, BigDecimal taxRatePercent, boolean inclusive) {
    BigDecimal rate = taxRatePercent.divide(HUNDRED, 6, ROUNDING_MODE);
    if (inclusive) {
      // Extract tax from inclusive price
      return round(netAmount.multiply(rate).divide(BigDecimal.ONE.add(rate), 6, ROUNDING_MODE));
    } else {
      return round(netAmount.multiply(rate));
    }
  }

  /**
   * Sum a varargs of BigDecimal values safely (null-safe, always 2dp). Prevents accumulated
   * rounding errors from intermediate operations.
   */
  public static BigDecimal sum(BigDecimal... amounts) {
    BigDecimal total = ZERO;
    for (BigDecimal a : amounts) {
      if (a != null) total = total.add(a);
    }
    return round(total);
  }

  /**
   * Compares two money amounts considering rounding equivalence. e.g. 100.000 and 100.00 are
   * considered equal.
   */
  public static boolean equals(BigDecimal a, BigDecimal b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.compareTo(b) == 0; // compareTo ignores scale; .equals() does not
  }

  /**
   * Validates that an amount is positive and has at most 2 decimal places. Use in @Valid validators
   * or service entry points.
   */
  public static boolean isValidAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return false;
    return amount.scale() <= CURRENCY_SCALE;
  }
}
