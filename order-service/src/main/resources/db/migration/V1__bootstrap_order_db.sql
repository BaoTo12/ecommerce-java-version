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

INSERT INTO product_catalog (id, sku, name, description, price) VALUES
                                                                    ('a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', 'PHONE-IP15', 'iPhone 15 Pro Max', 'Apple iPhone 15 Pro Max 256GB', 34990000.00),
                                                                    ('b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', 'PHONE-SS24', 'Samsung Galaxy S24 Ultra', 'Samsung Galaxy S24 Ultra 512GB', 31990000.00),
                                                                    ('c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', 'LAPTOP-MBP', 'MacBook Pro 14"', 'Apple MacBook Pro 14-inch M3 Pro', 49990000.00),
                                                                    ('d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a', 'HEADPHONE-APM', 'AirPods Max', 'Apple AirPods Max - Space Gray', 13490000.00),
                                                                    ('e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b', 'TABLET-IPD', 'iPad Pro 12.9"', 'Apple iPad Pro 12.9-inch M2 256GB', 28990000.00);


CREATE TABLE idempotency_keys (
                                  key             VARCHAR(255)    PRIMARY KEY,
                                  created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);



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


INSERT INTO inventory (product_id, quantity) VALUES
                                                 ('a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', 50),  -- iPhone 15 Pro Max
                                                 ('b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', 30),  -- Samsung Galaxy S24 Ultra
                                                 ('c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', 20),  -- MacBook Pro 14"
                                                 ('d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a', 100), -- AirPods Max
                                                 ('e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b', 40);  -- iPad Pro 12.9"


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


CREATE TABLE users (
                       id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                       email           VARCHAR(255)    NOT NULL UNIQUE,
                       password_hash   VARCHAR(255)    NOT NULL,
                       name            VARCHAR(255)    NOT NULL,
                       phone           VARCHAR(20),
                       is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
                       created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                       updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(is_active) WHERE is_active = TRUE;

-- Addresses table for user shipping/billing addresses
CREATE TABLE user_addresses (
                                id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id         UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                label           VARCHAR(50)     NOT NULL DEFAULT 'Home',
                                address_line1   VARCHAR(255)    NOT NULL,
                                address_line2   VARCHAR(255),
                                city            VARCHAR(100)    NOT NULL,
                                state           VARCHAR(100),
                                postal_code     VARCHAR(20)     NOT NULL,
                                country         VARCHAR(100)    NOT NULL DEFAULT 'Vietnam',
                                is_default      BOOLEAN         NOT NULL DEFAULT FALSE,
                                created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                                updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_addresses_user_id ON user_addresses(user_id);