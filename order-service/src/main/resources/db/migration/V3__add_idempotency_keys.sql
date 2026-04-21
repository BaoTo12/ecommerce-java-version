CREATE TABLE idempotency_keys (
    key             VARCHAR(255)    PRIMARY KEY,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
