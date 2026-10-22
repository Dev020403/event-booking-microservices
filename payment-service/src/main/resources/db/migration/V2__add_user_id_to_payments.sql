-- Owner of the payment (JWT subject); nullable for pre-existing rows, which are admin-only.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS user_id UUID;

CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments(user_id);
