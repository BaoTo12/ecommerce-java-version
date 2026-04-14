-- V1__bootstrap_order_db.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Product catalog
CREATE TABLE product_catalog (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             VARCHAR(64)     NOT NULL UNIQUE,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    price           NUMERIC(15,2)   NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_product_catalog_price CHECK (price > 0)
);
CREATE INDEX idx_product_catalog_active ON product_catalog(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_product_catalog_name ON product_catalog(name);

-- Carts
CREATE TABLE carts (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_carts_status CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED'))
);
CREATE INDEX idx_carts_user_active ON carts(user_id) WHERE status = 'ACTIVE';

-- Cart items
CREATE TABLE cart_items (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id         UUID            NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id      UUID            NOT NULL REFERENCES product_catalog(id),
    quantity        INT             NOT NULL,
    unit_price      NUMERIC(15,2)   NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_cart_items_unit_price CHECK (unit_price > 0),
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
);
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items(product_id);

-- Checkout sessions
CREATE TABLE checkout_sessions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id             UUID            NOT NULL REFERENCES carts(id),
    user_id             UUID            NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    subtotal_amount     NUMERIC(15,2)   NOT NULL,
    final_amount        NUMERIC(15,2)   NOT NULL,
    order_id            UUID,
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT chk_checkout_status CHECK (status IN ('CREATED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_checkout_subtotal CHECK (subtotal_amount >= 0),
    CONSTRAINT chk_checkout_final CHECK (final_amount >= 0)
);
CREATE INDEX idx_checkout_sessions_user_created ON checkout_sessions(user_id, created_at DESC);
CREATE INDEX idx_checkout_sessions_status_created ON checkout_sessions(status, created_at DESC);

-- Orders
CREATE TABLE orders (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    status          VARCHAR(30)     NOT NULL,
    total_amount    NUMERIC(15,2)   NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_orders_total_amount CHECK (total_amount > 0),
    CONSTRAINT chk_orders_status CHECK (status IN (
        'PENDING','CONFIRMED','PAYMENT_PROCESSING',
        'PAID','PAYMENT_FAILED','FULFILLING','COMPLETED','CANCELLED'
    ))
);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status_active ON orders(status) WHERE status NOT IN ('COMPLETED','CANCELLED');
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

-- Order items
CREATE TABLE order_items (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  UUID            NOT NULL,
    quantity    INT             NOT NULL,
    unit_price  NUMERIC(15,2)   NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_items_quantity   CHECK (quantity > 0),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price > 0)
);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

-- Order status history (append-only)
CREATE TABLE order_status_history (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL REFERENCES orders(id),
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_history_to_status CHECK (to_status IN (
        'PENDING','CONFIRMED','PAYMENT_PROCESSING',
        'PAID','PAYMENT_FAILED','FULFILLING','COMPLETED','CANCELLED'
    ))
);
CREATE INDEX idx_history_order_id ON order_status_history(order_id);
CREATE INDEX idx_history_to_status_created ON order_status_history(to_status, created_at DESC)
    WHERE to_status IN ('CANCELLED','PAYMENT_FAILED');

-- Outbox messages
CREATE TABLE outbox_messages (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(100)    NOT NULL,
    topic           VARCHAR(200)    NOT NULL,
    payload         JSONB           NOT NULL,
    published       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INT             NOT NULL DEFAULT 0,
    last_error      TEXT
);
CREATE INDEX idx_outbox_unpublished ON outbox_messages(created_at ASC) WHERE published = FALSE;

-- Order read model
CREATE TABLE order_read_model (
    order_id             UUID            PRIMARY KEY,
    user_id              UUID            NOT NULL,
    status               VARCHAR(30)     NOT NULL,
    payment_status       VARCHAR(30),
    total_amount         NUMERIC(15,2)   NOT NULL,
    item_count           INT             NOT NULL,
    last_event_at        TIMESTAMPTZ     NOT NULL,
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_read_model_total_amount CHECK (total_amount > 0),
    CONSTRAINT chk_read_model_item_count CHECK (item_count > 0)
);
CREATE INDEX idx_order_read_model_user_updated ON order_read_model(user_id, updated_at DESC);
CREATE INDEX idx_order_read_model_status_updated ON order_read_model(status, updated_at DESC);
