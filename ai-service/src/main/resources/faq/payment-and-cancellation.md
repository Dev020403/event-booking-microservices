# Payments and Cancellations FAQs

## Which payment methods are supported?

The backend currently simulates CARD, UPI, and WALLET payment methods. These are simulated payment flows in this project and should not be treated as real payment processing.

## What happens if a payment fails?

If payment fails during booking confirmation, the booking flow releases the inventory hold and the booking is marked PAYMENT_FAILED. The exact current booking state should be checked through the booking service.

## What happens if inventory confirmation fails after payment succeeds?

The booking workflow includes compensation logic. If a payment succeeds but confirming the inventory hold fails, the workflow attempts to refund the payment rather than leave the customer charged for an unconfirmed booking.

## Can I cancel a booking?

The backend exposes a booking cancellation operation. Cancellation may release the inventory hold and, if a payment was already taken, trigger the refund flow. Use the application's booking cancellation action and check the resulting booking status.

## What is the refund policy and how long does a refund take?

The project implements simulated payment and refund operations. It does not define a customer-facing refund eligibility policy or a real-world refund settlement timeline in these FAQs. Do not invent a refund promise; direct the customer to the event organizer or platform support for policy-specific decisions.
