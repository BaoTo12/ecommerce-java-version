-- Add expires_at column to inventory_reservations if not exists
ALTER TABLE inventory_reservations ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

-- Drop and recreate check constraint on inventory_reservations status
ALTER TABLE inventory_reservations DROP CONSTRAINT IF EXISTS chk_inventory_reservations_status;
ALTER TABLE inventory_reservations ADD CONSTRAINT chk_inventory_reservations_status
    CHECK (status IN ('HOLDING', 'SUCCEED', 'FAILED', 'COMPLETED', 'EXPIRED', 'HELD'));

-- Drop and recreate check constraint on checkout_sessions status
ALTER TABLE checkout_sessions DROP CONSTRAINT IF EXISTS chk_checkout_sessions_status;
ALTER TABLE checkout_sessions ADD CONSTRAINT chk_checkout_sessions_status
    CHECK (status IN ('CREATED', 'PAYMENT_PROCESSING', 'SUCCESS', 'PAYMENT_FAILED', 'EXPIRED'));
