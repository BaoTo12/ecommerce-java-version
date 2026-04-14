-- V1__bootstrap_notification_db.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE notifications (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID            NOT NULL,
    user_id         UUID            NOT NULL,
    type            VARCHAR(30)     NOT NULL,
    recipient_email VARCHAR(255)    NOT NULL,
    subject         VARCHAR(500)    NOT NULL,
    status          VARCHAR(10)     NOT NULL DEFAULT 'PENDING',
    retry_count     INT             NOT NULL DEFAULT 0,
    last_error      TEXT,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_notifications_type   CHECK (type IN ('ORDER_CONFIRMED','ORDER_COMPLETED','ORDER_CANCELLED','PAYMENT_FAILED')),
    CONSTRAINT chk_notifications_status CHECK (status IN ('PENDING','SENT','FAILED')),
    CONSTRAINT uq_notifications_order_type UNIQUE (order_id, type)
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_status_pending ON notifications(created_at ASC) WHERE status = 'PENDING';
CREATE INDEX idx_notifications_order_created ON notifications(order_id, created_at DESC);
