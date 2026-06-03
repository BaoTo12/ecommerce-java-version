-- ============================================================
-- E-commerce Monolith — Unified Database Schema
-- Migration: V1__monolith_schema.sql
-- ============================================================
-- This single migration replaces all 5 separate service databases.
-- All 20 edge cases are reflected in this schema design.
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- USERS (Edge Case #6: Soft Delete, Edge Case #15: Token Version)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) NOT NULL UNIQUE,
    hashed_password  TEXT NOT NULL,
    name             VARCHAR(100) NOT NULL,
    phone            VARCHAR(20),
    roles            VARCHAR(50) NOT NULL DEFAULT 'USER',

    -- Edge Case #15: Token version — increment to invalidate all JWT sessions
    token_version    INT NOT NULL DEFAULT 0,

    -- Edge Case #6: Soft delete — preserves FK integrity for orders/carts
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(is_active) WHERE is_active = TRUE;

-- ─────────────────────────────────────────────────────────────
-- USER ADDRESSES (Edge Case #16: Ownership check)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE user_addresses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label           VARCHAR(50) NOT NULL,
    address_line1   VARCHAR(255) NOT NULL,
    address_line2   VARCHAR(255),
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100),
    postal_code     VARCHAR(20),
    country         VARCHAR(100) NOT NULL DEFAULT 'Vietnam',
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_address_user ON user_addresses(user_id);

-- ─────────────────────────────────────────────────────────────
-- REFRESH TOKENS (Edge Case #15: Session management)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    device_name VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_hash ON refresh_tokens(token_hash);

-- ─────────────────────────────────────────────────────────────
-- PRODUCTS (Edge Case #4: Price Snapshot)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE products (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku         VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(15, 2) NOT NULL,  -- Live price; snapshotted in cart_items
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_product_sku  ON products(sku);
CREATE INDEX idx_product_active ON products(is_active) WHERE is_active = TRUE;

-- ─────────────────────────────────────────────────────────────
-- INVENTORY (Edge Case #2: @Version, Edge Case #14: Atomic Decrement)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE inventory (
    product_id  UUID PRIMARY KEY REFERENCES products(id),
    quantity    INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    version     BIGINT NOT NULL DEFAULT 0,  -- Edge Case #2: Optimistic lock version
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────
-- INVENTORY RESERVATIONS (Edge Case #5: TTL / Expiry)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE inventory_reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL,
    product_id      UUID NOT NULL REFERENCES products(id),
    quantity        INT NOT NULL,
    operation       VARCHAR(20) NOT NULL,   -- RESERVE, RELEASE
    status          VARCHAR(20) NOT NULL,   -- SUCCESS, FAILED, RELEASED

    -- Edge Case #5: Expiry timestamp for TTL-based release
    expires_at      TIMESTAMPTZ,

    released_at     TIMESTAMPTZ,
    failure_reason  VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_inv_res_order  ON inventory_reservations(order_id);
CREATE INDEX idx_inv_res_expiry ON inventory_reservations(expires_at, status)
    WHERE status = 'SUCCESS' AND released_at IS NULL;

-- ─────────────────────────────────────────────────────────────
-- CARTS (Edge Case #7: Pessimistic Lock, Edge Case #4: Price Snapshot)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE carts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, CHECKED_OUT, ABANDONED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cart_user_status ON carts(user_id, status);

CREATE TABLE cart_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id         UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id),
    product_name    VARCHAR(255) NOT NULL,
    quantity        INT NOT NULL CHECK (quantity > 0),

    -- Edge Case #4: Price at time of add-to-cart; validated at checkout
    price_snapshot  NUMERIC(15, 2) NOT NULL,

    selected        BOOLEAN NOT NULL DEFAULT TRUE,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────
-- ORDERS (Edge Case #1: Idempotency, Edge Case #8: State Machine)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    -- Edge Case #1: Idempotency key prevents duplicate order creation on retry
    idempotency_key     VARCHAR(64) UNIQUE,

    total_amount        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    shipping_address_id UUID,
    notes               VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_order_user      ON orders(user_id);
CREATE INDEX idx_order_status    ON orders(status);
CREATE INDEX idx_order_idem_key  ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE order_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id    UUID NOT NULL REFERENCES products(id),
    product_name  VARCHAR(255) NOT NULL,
    quantity      INT NOT NULL,
    unit_price    NUMERIC(15, 2) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Edge Case #8: Order status history / audit trail
CREATE TABLE order_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    reason      VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_order_history_order ON order_status_history(order_id);

-- ─────────────────────────────────────────────────────────────
-- PAYMENTS (Edge Case #3: Pessimistic Lock, #9: Unique, #10: Refund State)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE payments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Edge Case #9: UNIQUE ensures only ONE payment record per order
    order_id                UUID NOT NULL UNIQUE REFERENCES orders(id),

    user_id                 UUID NOT NULL REFERENCES users(id),
    amount                  NUMERIC(15, 2) NOT NULL,

    -- Edge Case #10: Status machine: PENDING → CHARGED → REFUNDED (terminal)
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    failure_reason          VARCHAR(500),
    refund_reason           VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payment_order ON payments(order_id);
CREATE INDEX idx_payment_user  ON payments(user_id);
CREATE INDEX idx_payment_status ON payments(status);

-- ─────────────────────────────────────────────────────────────
-- NOTIFICATIONS (Edge Case #18: Deduplication)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE notifications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID NOT NULL REFERENCES orders(id),
    user_id          UUID NOT NULL REFERENCES users(id),

    -- Edge Case #18: UNIQUE prevents sending same notification type twice per order
    type             VARCHAR(50) NOT NULL,
    CONSTRAINT uq_notification_order_type UNIQUE (order_id, type),

    recipient_email  VARCHAR(255) NOT NULL,
    subject          VARCHAR(500) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count      INT NOT NULL DEFAULT 0,
    max_retry        INT NOT NULL DEFAULT 3,
    last_error       VARCHAR(500),
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notification_order ON notifications(order_id);
CREATE INDEX idx_notification_user  ON notifications(user_id);

-- ─────────────────────────────────────────────────────────────
-- OUTBOX MESSAGES (Edge Case #11: Transactional Outbox)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE outbox_messages (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type     VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    payload        TEXT NOT NULL,
    published      BOOLEAN NOT NULL DEFAULT FALSE,
    published_at   TIMESTAMPTZ,
    retry_count    INT NOT NULL DEFAULT 0,
    max_retry      INT NOT NULL DEFAULT 5,
    last_error     VARCHAR(1000),
    next_retry_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Partial index: only index unpublished rows (polling query)
CREATE INDEX idx_outbox_pending ON outbox_messages(published, next_retry_at)
    WHERE published = FALSE;

-- ─────────────────────────────────────────────────────────────
-- SEED DATA: Sample products and inventory
-- ─────────────────────────────────────────────────────────────
INSERT INTO products (id, sku, name, description, price, is_active) VALUES
    ('11111111-1111-1111-1111-111111111111', 'LAPTOP-PRO-15', 'Laptop Pro 15"',
     'High-performance 15-inch laptop with M3 chip', 1299.99, TRUE),
    ('22222222-2222-2222-2222-222222222222', 'WIRELESS-MOUSE', 'Wireless Mouse',
     'Ergonomic wireless mouse with 1 year battery life', 49.99, TRUE),
    ('33333333-3333-3333-3333-333333333333', 'USB-C-HUB-7', 'USB-C Hub 7-in-1',
     '7-in-1 USB-C hub with HDMI, USB3, SD card reader', 79.99, TRUE),
    ('44444444-4444-4444-4444-444444444444', 'MECHANICAL-KB', 'Mechanical Keyboard',
     'TKL mechanical keyboard with Cherry MX switches', 129.99, TRUE),
    ('55555555-5555-5555-5555-555555555555', 'MONITOR-27', '27" 4K Monitor',
     '27-inch 4K IPS monitor, 144Hz, HDR400', 449.99, TRUE);

INSERT INTO inventory (product_id, quantity, version) VALUES
    ('11111111-1111-1111-1111-111111111111', 50, 0),
    ('22222222-2222-2222-2222-222222222222', 200, 0),
    ('33333333-3333-3333-3333-333333333333', 100, 0),
    ('44444444-4444-4444-4444-444444444444', 75, 0),
    ('55555555-5555-5555-5555-555555555555', 30, 0);
