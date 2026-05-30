CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- User tables
CREATE TABLE users(
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE ,
    hashed_password VARCHAR(255),
    name VARCHAR(255),
    phone VARCHAR(255),
    is_active boolean,
    created_at timestamp
)

CREATE TABLE user_addresses(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGSERIAL,
    label VARCHAR(125), -- office, home
    city VARCHAR(125),
    country VARCHAR(125),
    is_default boolean,
    CONSTRAINT fk_user_address FOREIGN KEY (user_id) REFERENCES  users(id) ON DELETE CASCADE
)


CREATE TABLE refresh_token(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGSERIAL REFERENCES users(id),
    token_hash VARCHAR(255),
    expires_at TIMESTAMP,
    created_at TIMESTAMP,
    revoked_at TIMESTAMP
)

-- Cart Tables
CREATE TABLE carts(
    id BIGSERIAL PRIMARY KEY ,
    user_id BIGSERIAL REFERENCES users(id)
)

CREATE TABLE cart_items(
    id BIGSERIAL PRIMARY KEY ,
    cart_id BIGSERIAL REFERENCES carts(id),
    product_id BIGSERIAL, --- TODO: ADD  product table
    quantity INTEGER,
    unit_price INTEGER,
    selected boolean
)

-- Order Tables
CREATE TABLE orders(
    id BIGSERIAL PRIMARY KEY ,
    user_id BIGSERIAL REFERENCES users(id),
    amount
)