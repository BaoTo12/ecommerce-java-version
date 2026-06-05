-- Create user_cards table
CREATE TABLE IF NOT EXISTS user_cards
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    card_number VARCHAR(20) NOT NULL,
    cvc         VARCHAR(4)  NOT NULL,
    card_name   VARCHAR(100) NOT NULL,
    expiry      VARCHAR(7)  NOT NULL,
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_cards_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_cards_user ON user_cards (user_id);
