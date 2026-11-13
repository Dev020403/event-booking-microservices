# Tickets and Inventory FAQs

## What happens when I select a ticket?

The inventory service tracks stock for each event ticket type. During checkout, the booking service requests a temporary hold for the selected quantity. A successful hold reduces the quantity available to other customers until the hold is confirmed or released.

## Why can a ticket become unavailable during checkout?

Ticket inventory can change while multiple customers are checking out. A ticket may become unavailable if another customer's hold or confirmed booking consumes the remaining stock before your request is processed. Always use the live event and inventory information returned by the application; FAQ content does not represent real-time stock.

## Does a HELD ticket mean that my booking is confirmed?

No. HELD means inventory is temporarily reserved while checkout is in progress. A booking is confirmed only after the confirmation flow succeeds and the booking status becomes CONFIRMED.

## How do I know the current ticket price or availability?

Prices and availability can change. EventMate must use its live event and inventory tools to retrieve current prices and ticket counts. Never rely on a static FAQ answer for current price or stock.
