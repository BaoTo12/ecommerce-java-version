-- Fix check constraint on orders table to allow all OrderStatus enum values
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN (
                      'PENDING',
                      'CONFIRMED',
                      'PAYMENT_PENDING',
                      'PAID',
                      'PAYMENT_FAILED',
                      'PROCESSING',
                      'SHIPPED',
                      'DELIVERED',
                      'COMPLETED',
                      'CANCELLED',
                      'EXPIRED'
        ));

-- Fix check constraint on payments table to allow all Payment status values used in Java
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_status;
ALTER TABLE payments ADD CONSTRAINT chk_payments_status
    CHECK (status IN (
                      'PENDING',
                      'CHARGED',
                      'FAILED',
                      'REFUND_REQUESTED',
                      'REFUNDED',
                      'REFUND_FAILED'
        ));

-- Fix check constraint on notifications table to allow all Notification type values used in Java
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type
    CHECK (type IN (
                    'ORDER_CREATED',
                    'PAYMENT_SUCCESS',
                    'PAYMENT_FAILED',
                    'ORDER_SHIPPED',
                    'ORDER_CANCELLED',
                    'ORDER_CONFIRMED',
                    'ORDER_COMPLETED'
        ));
