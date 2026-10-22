-- Email of the booking owner, captured from the access token, for notifications
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS user_email VARCHAR(255);
