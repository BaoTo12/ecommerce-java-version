erDiagram

    USERS {
        UUID id PK
        VARCHAR email UK
        TEXT hashed_password
        VARCHAR name
        VARCHAR phone
        VARCHAR roles
        INT token_version
        BOOLEAN is_active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    USER_ADDRESSES {
        UUID id PK
        UUID user_id FK
        VARCHAR label
        VARCHAR address_line1
        VARCHAR address_line2
        VARCHAR city
        VARCHAR state
        VARCHAR postal_code
        VARCHAR country
        BOOLEAN is_default
        TIMESTAMPTZ created_at
    }

    REFRESH_TOKENS {
        UUID id PK
        UUID user_id FK
        VARCHAR token_hash UK
        TIMESTAMPTZ expires_at
        VARCHAR device_name
        TIMESTAMPTZ created_at
        TIMESTAMPTZ revoked_at
    }

    PRODUCTS {
        UUID id PK
        VARCHAR sku UK
        VARCHAR name
        TEXT description
        NUMERIC price
        BOOLEAN is_active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    INVENTORY {
        UUID product_id PK, FK
        INT quantity
        BIGINT version
        TIMESTAMPTZ updated_at
    }

    INVENTORY_RESERVATIONS {
        UUID id PK
        UUID order_id
        UUID product_id FK
        INT quantity
        VARCHAR operation
        VARCHAR status
        TIMESTAMPTZ expires_at
        TIMESTAMPTZ released_at
        VARCHAR failure_reason
        TIMESTAMPTZ created_at
    }

    CARTS {
        UUID id PK
        UUID user_id FK
        VARCHAR status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    CART_ITEMS {
        UUID id PK
        UUID cart_id FK
        UUID product_id FK
        VARCHAR product_name
        INT quantity
        NUMERIC price_snapshot
        BOOLEAN selected
        TIMESTAMPTZ added_at
    }

    ORDERS {
        UUID id PK
        UUID user_id FK
        VARCHAR status
        VARCHAR idempotency_key UK
        NUMERIC total_amount
        UUID shipping_address_id
        VARCHAR notes
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    ORDER_ITEMS {
        UUID id PK
        UUID order_id FK
        UUID product_id FK
        VARCHAR product_name
        INT quantity
        NUMERIC unit_price
        TIMESTAMPTZ created_at
    }

    ORDER_STATUS_HISTORY {
        UUID id PK
        UUID order_id FK
        VARCHAR from_status
        VARCHAR to_status
        VARCHAR reason
        TIMESTAMPTZ created_at
    }

    PAYMENTS {
        UUID id PK
        UUID order_id FK, UK
        UUID user_id FK
        NUMERIC amount
        VARCHAR status
        VARCHAR gateway_transaction_id
        VARCHAR failure_reason
        VARCHAR refund_reason
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    NOTIFICATIONS {
        UUID id PK
        UUID order_id FK
        UUID user_id FK
        VARCHAR type
        VARCHAR recipient_email
        VARCHAR subject
        VARCHAR status
        INT retry_count
        INT max_retry
        VARCHAR last_error
        TIMESTAMPTZ sent_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    OUTBOX_MESSAGES {
        UUID id PK
        VARCHAR event_type
        VARCHAR aggregate_id
        TEXT payload
        BOOLEAN published
        TIMESTAMPTZ published_at
        INT retry_count
        INT max_retry
        VARCHAR last_error
        TIMESTAMPTZ next_retry_at
        TIMESTAMPTZ created_at
    }

    COUPONS {
        UUID id PK
        VARCHAR code UK
        VARCHAR discount_type
        NUMERIC discount_value
        NUMERIC min_order_amount
        NUMERIC max_discount_amount
        INT max_uses
        INT used_count
        INT max_uses_per_user
        TIMESTAMPTZ valid_from
        TIMESTAMPTZ valid_until
        BOOLEAN is_active
        TIMESTAMPTZ created_at
    }

    COUPON_USAGES {
        UUID id PK
        UUID coupon_id FK
        UUID user_id FK
        UUID order_id FK
        NUMERIC discount_applied
        TIMESTAMPTZ used_at
    }

    DEAD_LETTER_MESSAGES {
        UUID id PK
        UUID original_outbox_id
        VARCHAR event_type
        VARCHAR aggregate_id
        TEXT payload
        TEXT failure_reason
        INT retry_count
        TIMESTAMPTZ created_at
        TIMESTAMPTZ replayed_at
        VARCHAR replayed_by
    }

    USERS ||--o{ USER_ADDRESSES : has
    USERS ||--o{ REFRESH_TOKENS : owns
    USERS ||--o{ CARTS : owns
    USERS ||--o{ ORDERS : places
    USERS ||--o{ PAYMENTS : makes
    USERS ||--o{ NOTIFICATIONS : receives
    USERS ||--o{ COUPON_USAGES : uses

    PRODUCTS ||--|| INVENTORY : has
    PRODUCTS ||--o{ INVENTORY_RESERVATIONS : reserved_in
    PRODUCTS ||--o{ CART_ITEMS : added_as
    PRODUCTS ||--o{ ORDER_ITEMS : ordered_as

    CARTS ||--o{ CART_ITEMS : contains

    ORDERS ||--o{ ORDER_ITEMS : contains
    ORDERS ||--o{ ORDER_STATUS_HISTORY : records
    ORDERS ||--|| PAYMENTS : paid_by
    ORDERS ||--o{ NOTIFICATIONS : triggers
    ORDERS ||--o{ COUPON_USAGES : applies_coupon

    COUPONS ||--o{ COUPON_USAGES : used_in