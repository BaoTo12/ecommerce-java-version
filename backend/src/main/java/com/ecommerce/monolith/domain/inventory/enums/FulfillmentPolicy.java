package com.ecommerce.monolith.domain.inventory.enums;

/**
 * Edge Case #30 — Partial Fulfillment Policy
 *
 * <p>Problem: A user orders [5× Product A, 3× Product B]. Stock: Product A = 5 (enough), Product B
 * = 1 (not enough, needs 3).
 *
 * <p>What should the system do? This is a BUSINESS DECISION, not a technical one. Different
 * businesses have legitimately different answers.
 *
 * <p>Without an explicit policy, developers make ad-hoc decisions inconsistently across different
 * parts of the codebase → unpredictable behavior.
 *
 * <p>The policy is configured per-business-context in application.yml:
 * app.inventory.fulfillment-policy: REJECT_ALL
 *
 * <p>Implementation: InventoryService reads the configured policy and branches accordingly. Adding
 * a new policy = add an enum value + handle in InventoryService. No if-else chains scattered across
 * the codebase.
 */
public enum FulfillmentPolicy {

  /**
   * REJECT_ALL (Default for most e-commerce platforms): If ANY item is out of stock → reject the
   * ENTIRE order.
   *
   * <p>Pro: Simple. Customer always gets exactly what they ordered, or nothing. Con: Customer loses
   * the items they COULD have gotten. Use when: Items are logically related (e.g., a set — missing
   * one ruins the set).
   *
   * <p>Example: "Sorry, Product B is out of stock. Your order was not placed."
   */
  REJECT_ALL,

  /**
   * PARTIAL_FULFILL: Ship what's available. The customer is notified of out-of-stock items. They
   * are charged only for fulfilled items (or charged fully, then refunded for missing items).
   *
   * <p>Pro: Customer gets value immediately. Revenue on available items. Con: Complex billing
   * (partial charge, partial refund). Customer may be confused. Use when: Items are independent
   * (groceries, office supplies).
   *
   * <p>Example: "We shipped 5× Product A. Product B is backordered; you were not charged for it."
   */
  PARTIAL_FULFILL,

  /**
   * BACKORDER: Reserve all items. Immediately available items ship; unavailable items are placed on
   * backorder and shipped when stock is replenished.
   *
   * <p>Pro: Customer gets everything eventually. No order cancellation friction. Con: Complex
   * tracking. Customer may wait a long time. May need to hold payment. Use when: B2B, specialty
   * goods, electronics with predictable restock dates.
   *
   * <p>Example: "Product A shipped today. Product B will ship in 5-7 business days."
   */
  BACKORDER,

  /**
   * WAIT_FOR_ALL: Hold the entire order until ALL items are in stock.
   *
   * <p>Pro: Customer receives a single complete shipment. Con: May wait a long time. All items'
   * inventory held for the wait period. Use when: Subscription boxes, gift sets where partial
   * shipment is unacceptable.
   */
  WAIT_FOR_ALL
}
