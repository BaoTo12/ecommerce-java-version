-- V1__bootstrap_payment_db.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE payments (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID            NOT NULL UNIQUE,
    user_id                 UUID            NOT NULL,
    amount                  NUMERIC(15,2)   NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    gateway_transaction_id  VARCHAR(255),
    failure_reason          TEXT,
    refund_reason           TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_status CHECK (status IN (
        'PENDING','CHARGED','FAILED','REFUND_REQUESTED','REFUNDED','REFUND_FAILED'
    ))
);

CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_status_failed ON payments(created_at DESC)
    WHERE status IN ('FAILED','REFUND_FAILED');
