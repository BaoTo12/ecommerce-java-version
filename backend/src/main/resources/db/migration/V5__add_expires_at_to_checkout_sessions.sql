-- Migration to add expires_at to checkout_sessions table
ALTER TABLE checkout_sessions ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
