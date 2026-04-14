-- V1__bootstrap_inventory_db.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE inventory (
    product_id  UUID        PRIMARY KEY,
    quantity    INT         NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_inventory_quantity CHECK (quantity >= 0)
);

CREATE INDEX idx_inventory_low_stock ON inventory(quantity) WHERE quantity < 10;

CREATE TABLE inventory_reservations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID        NOT NULL,
    product_id      UUID        NOT NULL REFERENCES inventory(product_id),
    quantity        INT         NOT NULL,
    operation       VARCHAR(10) NOT NULL,
    status          VARCHAR(10) NOT NULL,
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reservation_quantity  CHECK (quantity > 0),
    CONSTRAINT chk_reservation_operation CHECK (operation IN ('RESERVE', 'RELEASE')),
    CONSTRAINT chk_reservation_status    CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_reservations_order_id   ON inventory_reservations(order_id);
CREATE INDEX idx_reservations_product_id ON inventory_reservations(product_id);
CREATE INDEX idx_reservations_failed     ON inventory_reservations(created_at DESC) WHERE status = 'FAILED';
