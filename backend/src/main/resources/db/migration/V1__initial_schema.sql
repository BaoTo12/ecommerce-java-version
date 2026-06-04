CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- USERS
CREATE TABLE IF NOT EXISTS users
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    hashed_password TEXT         NOT NULL,
    name            VARCHAR(255),
    phone           VARCHAR(50),
    roles           VARCHAR(255) NOT NULL DEFAULT 'USER',
    token_version   INT          NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- USER ADDRESSES
CREATE TABLE IF NOT EXISTS user_addresses
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    label         VARCHAR(100),
    address_line1 VARCHAR(255) NOT NULL,
    city          VARCHAR(100) NOT NULL,
    postal_code   VARCHAR(50),
    country       VARCHAR(100) NOT NULL,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_addresses_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

-- REFRESH TOKENS
CREATE TABLE IF NOT EXISTS refresh_tokens
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    device_name VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

-- PRODUCTS
CREATE TABLE IF NOT EXISTS products
(
    id          UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    sku         VARCHAR(100)   NOT NULL UNIQUE,
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    price       NUMERIC(15, 2) NOT NULL,
    is_active   BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_products_price_non_negative
        CHECK (price >= 0)
);

-- INVENTORY - One product has exactly one inventory row
CREATE TABLE IF NOT EXISTS inventory
(
    product_id UUID PRIMARY KEY,
    quantity   INT         NOT NULL DEFAULT 0,
    version    BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_inventory_product
        FOREIGN KEY (product_id)
            REFERENCES products (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_inventory_quantity_non_negative
        CHECK (quantity >= 0)
);

-- CARTS
CREATE TABLE IF NOT EXISTS carts
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    status     VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_carts_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_carts_status
        CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED'))
);

-- CART ITEMS
CREATE TABLE IF NOT EXISTS cart_items
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    cart_id        UUID           NOT NULL,
    product_id     UUID           NOT NULL,
    product_name   VARCHAR(255)   NOT NULL,
    quantity       INT            NOT NULL,
    price_snapshot NUMERIC(15, 2) NOT NULL,
    selected       BOOLEAN        NOT NULL DEFAULT TRUE,
    added_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_cart_items_cart
        FOREIGN KEY (cart_id)
            REFERENCES carts (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_cart_items_product
        FOREIGN KEY (product_id)
            REFERENCES products (id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_cart_items_quantity_positive
        CHECK (quantity > 0),

    CONSTRAINT chk_cart_items_price_non_negative
        CHECK (price_snapshot >= 0),

    CONSTRAINT uk_cart_items_cart_product
        UNIQUE (cart_id, product_id)
);

-- ORDERS
CREATE TABLE IF NOT EXISTS orders
(
    id                  UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id             UUID           NOT NULL,
    status              VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    idempotency_key     UUID,
    total_amount        NUMERIC(15, 2) NOT NULL,
    shipping_address_id UUID,
    notes               VARCHAR(500),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_orders_shipping_address
        FOREIGN KEY (shipping_address_id)
            REFERENCES user_addresses (id)
            ON DELETE SET NULL,

    CONSTRAINT chk_orders_total_amount_non_negative
        CHECK (total_amount >= 0),

    CONSTRAINT chk_orders_status
        CHECK (status IN (
                          'PENDING',
                          'CONFIRMED',
                          'PAID',
                          'PROCESSING',
                          'SHIPPED',
                          'DELIVERED',
                          'CANCELLED',
                          'REFUNDED'
            ))
);

-- CHECKOUT SESSIONS
CREATE TABLE IF NOT EXISTS checkout_sessions
(
    idempotency_key UUID PRIMARY KEY,
    user_id         UUID           NOT NULL,
    cart_id         UUID           NOT NULL,
    total_amount    NUMERIC(15, 2) NOT NULL,
    status          VARCHAR(50)    NOT NULL DEFAULT 'CREATED',
    order_id        UUID UNIQUE,
    response_body   TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_checkout_sessions_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_checkout_sessions_cart
        FOREIGN KEY (cart_id)
            REFERENCES carts (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_checkout_sessions_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id)
            ON DELETE SET NULL,

    CONSTRAINT chk_checkout_sessions_total_amount_non_negative
        CHECK (total_amount >= 0),

    CONSTRAINT chk_checkout_sessions_status
        CHECK (status IN (
                          'CREATED',
                          'PAYMENT_PROCESSING',
                          'SUCCESS',
                          'PAYMENT_FAILED'
            ))
);

-- Add FK from orders to checkout_sessions after both tables exist
ALTER TABLE orders DROP CONSTRAINT IF EXISTS fk_orders_checkout_session;
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_checkout_session
        FOREIGN KEY (idempotency_key)
            REFERENCES checkout_sessions (idempotency_key)
            ON DELETE SET NULL;

-- One checkout session should create at most one order.
CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_idempotency_key
    ON orders (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- ORDER ITEMS
CREATE TABLE IF NOT EXISTS order_items
(
    id           UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    order_id     UUID           NOT NULL,
    product_id   UUID           NOT NULL,
    product_name VARCHAR(255)   NOT NULL,
    quantity     INT            NOT NULL,
    unit_price   NUMERIC(15, 2) NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id)
            REFERENCES products (id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_order_items_quantity_positive
        CHECK (quantity > 0),

    CONSTRAINT chk_order_items_unit_price_non_negative
        CHECK (unit_price >= 0)
);

-- PAYMENTS
CREATE TABLE IF NOT EXISTS payments
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    order_id       UUID           NOT NULL UNIQUE,
    user_id        UUID           NOT NULL,
    amount         NUMERIC(15, 2) NOT NULL,
    status         VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(500),
    refund_reason  VARCHAR(500),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_payments_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_payments_amount_non_negative
        CHECK (amount >= 0),

    CONSTRAINT chk_payments_status
        CHECK (status IN (
                          'PENDING',
                          'PROCESSING',
                          'SUCCESS',
                          'FAILED',
                          'REFUNDED'
            ))
);

-- INVENTORY RESERVATIONS
CREATE TABLE IF NOT EXISTS inventory_reservations
(
    id             UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    order_id       UUID,
    product_id     UUID        NOT NULL,
    quantity       INT         NOT NULL,
    operation      VARCHAR(50) NOT NULL,
    status         VARCHAR(50) NOT NULL DEFAULT 'HOLDING',
    expires_at     TIMESTAMPTZ,
    released_at    TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_inventory_reservations_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_inventory_reservations_product
        FOREIGN KEY (product_id)
            REFERENCES products (id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_inventory_reservations_quantity_positive
        CHECK (quantity > 0),

    CONSTRAINT chk_inventory_reservations_operation
        CHECK (operation IN ('RESERVE', 'RELEASE', 'COMMIT')),

    CONSTRAINT chk_inventory_reservations_status
        CHECK (status IN ('HOLDING', 'SUCCEED', 'FAILED'))
);

-- NOTIFICATIONS
CREATE TABLE IF NOT EXISTS notifications
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    order_id        UUID,
    user_id         UUID        NOT NULL,
    type            VARCHAR(50) NOT NULL,
    recipient_email VARCHAR(255),
    subject         VARCHAR(255),
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count     INT         NOT NULL DEFAULT 0,
    max_retry       INT         NOT NULL DEFAULT 3,
    last_error      VARCHAR(1000),
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_notifications_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_notifications_retry_count_non_negative
        CHECK (retry_count >= 0),

    CONSTRAINT chk_notifications_max_retry_non_negative
        CHECK (max_retry >= 0),

    CONSTRAINT chk_notifications_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),

    CONSTRAINT chk_notifications_type
        CHECK (type IN (
                        'ORDER_CREATED',
                        'PAYMENT_SUCCESS',
                        'PAYMENT_FAILED',
                        'ORDER_SHIPPED',
                        'ORDER_CANCELLED'
            ))
);

-- UPDATED_AT TRIGGER
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE
    ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_products_updated_at ON products;
CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE
    ON products
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_carts_updated_at ON carts;
CREATE TRIGGER trg_carts_updated_at
    BEFORE UPDATE
    ON carts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_orders_updated_at ON orders;
CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE
    ON orders
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_payments_updated_at ON payments;
CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE
    ON payments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_notifications_updated_at ON notifications;
CREATE TRIGGER trg_notifications_updated_at
    BEFORE UPDATE
    ON notifications
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_inventory_updated_at ON inventory;
CREATE TRIGGER trg_inventory_updated_at
    BEFORE UPDATE
    ON inventory
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();