# Booking FAQs

## How do I book an event?

Sign in to your Event Booking account, open the event catalogue, and select the event you want to attend. Choose a ticket type and quantity, then submit the booking request. The booking service creates a booking and asks the inventory service to place a temporary hold on the requested tickets. Follow the confirmation or payment step shown by the application to complete the booking.

## What does a HELD booking status mean?

HELD means the requested tickets have been temporarily reserved for the booking while checkout is completed. A hold is time-limited. If it expires before confirmation, the booking may move to EXPIRED and the inventory hold is released.

## Which booking statuses are supported?

The booking service uses these statuses: PENDING, HELD, CONFIRMED, CANCELLED, EXPIRED, and PAYMENT_FAILED. The status shown for a booking is the authoritative status; ask the booking service for a specific booking's current state.

## How can I view my bookings?

Use the authenticated bookings area of the application to view your bookings. The backend exposes a "my bookings" endpoint for the signed-in customer. Do not share another customer's booking details.
