-- V1__bootstrap_user_db.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

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
