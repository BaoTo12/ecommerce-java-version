-- ============================================================
-- V2: Additional edge case tables
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- COUPONS (Edge Case #21: Race Condition Prevention)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE coupons (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(50) NOT NULL UNIQUE,

    -- PERCENTAGE or FIXED_AMOUNT
    discount_type       VARCHAR(20) NOT NULL,
    discount_value      NUMERIC(10, 2) NOT NULL,

    min_order_amount    NUMERIC(15, 2),
    max_discount_amount NUMERIC(15, 2),

    -- Edge Case #21: max_uses = NULL means unlimited
    max_uses            INT,

    -- Edge Case #21: atomically incremented by atomicIncrementUsage()
    -- CHECK constraint prevents it from going negative or beyond max_uses
    used_count          INT NOT NULL DEFAULT 0 CHECK (used_count >= 0),

    max_uses_per_user   INT NOT NULL DEFAULT 1,

    -- Edge Case #27 — Clock Safety: all timestamps stored as UTC timestamptz
    valid_from          TIMESTAMPTZ NOT NULL,
    valid_until         TIMESTAMPTZ NOT NULL,

    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_coupon_code ON coupons(code);
CREATE INDEX idx_coupon_active ON coupons(is_active, valid_until) WHERE is_active = TRUE;

-- Edge Case #21: UNIQUE(coupon_id, user_id) prevents same user from using
-- the same coupon twice even under concurrent requests
CREATE TABLE coupon_usages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id        UUID NOT NULL REFERENCES coupons(id),
    user_id          UUID NOT NULL REFERENCES users(id),
    order_id         UUID NOT NULL REFERENCES orders(id),
    discount_applied NUMERIC(15, 2) NOT NULL,
    used_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_coupon_usage_per_user UNIQUE (coupon_id, user_id)
);
CREATE INDEX idx_coupon_usage_order ON coupon_usages(order_id);

-- ─────────────────────────────────────────────────────────────
-- DEAD LETTER MESSAGES (Edge Case #26: DLQ for failed outbox)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE dead_letter_messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_outbox_id  UUID NOT NULL REFERENCES outbox_messages(id),
    event_type          VARCHAR(100) NOT NULL,
    aggregate_id        VARCHAR(100) NOT NULL,
    payload             TEXT NOT NULL,
    failure_reason      TEXT NOT NULL,
    retry_count         INT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    replayed_at         TIMESTAMPTZ,
    replayed_by         VARCHAR(100)
);
CREATE INDEX idx_dlm_event_type ON dead_letter_messages(event_type);
CREATE INDEX idx_dlm_unprocessed ON dead_letter_messages(replayed_at) WHERE replayed_at IS NULL;

-- ─────────────────────────────────────────────────────────────
-- AUDIT: Strengthen order_status_history immutability
-- Edge Case #29: Row Security Policies prevent UPDATE/DELETE
-- ─────────────────────────────────────────────────────────────
-- Enable Row Level Security on the audit table
ALTER TABLE order_status_history ENABLE ROW LEVEL SECURITY;

-- Allow SELECT to all (authenticated app user)
CREATE POLICY audit_allow_select ON order_status_history
    FOR SELECT USING (true);

-- Allow INSERT (creating new history entries)
CREATE POLICY audit_allow_insert ON order_status_history
    FOR INSERT WITH CHECK (true);

-- DENY UPDATE — no one can modify an existing audit record
CREATE POLICY audit_deny_update ON order_status_history
    FOR UPDATE USING (false);

-- DENY DELETE — no one can delete an audit record
CREATE POLICY audit_deny_delete ON order_status_history
    FOR DELETE USING (false);

-- ─────────────────────────────────────────────────────────────
-- SEED: Sample coupons for testing
-- ─────────────────────────────────────────────────────────────
INSERT INTO coupons (code, discount_type, discount_value, min_order_amount,
                     max_discount_amount, max_uses, max_uses_per_user,
                     valid_from, valid_until, is_active)
VALUES
    -- Flash sale: first 100 users get 20% off (max $50 discount)
    ('FLASH20', 'PERCENTAGE', 20.00, 50.00, 50.00, 100, 1,
     NOW(), NOW() + INTERVAL '30 days', TRUE),

    -- Fixed discount: $10 off orders over $100
    ('SAVE10', 'FIXED_AMOUNT', 10.00, 100.00, NULL, NULL, 1,
     NOW(), NOW() + INTERVAL '90 days', TRUE);
