-- Bug 4 fix: add currency column to bookings table
-- Default to 'INR' for existing rows so the NOT NULL constraint is satisfied
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'INR';
