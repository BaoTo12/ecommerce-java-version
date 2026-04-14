package com.ecommerce.shared.event;

/**
 * Kafka topic constants shared across all services.
 */
public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String ORDERS_CREATED = "orders.created";
    public static final String ORDERS_STATUS_CHANGED = "orders.status-changed";
    public static final String ORDERS_CANCELLED = "orders.cancelled";

    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_FAILED = "inventory.failed";
    public static final String INVENTORY_RELEASE_REQUESTED = "inventory.release-requested";
    public static final String INVENTORY_RELEASED = "inventory.released";

    public static final String PAYMENT_REQUESTED = "payment.requested";
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_REFUND_REQUESTED = "payment.refund-requested";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
}
