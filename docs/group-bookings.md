# Couples and group bookings

One booker books 2 to 6 guests who all start at the same time. Each guest has their own
treatment and, optionally, their own therapist (a named therapist or a label).

## Data (migration V88)

- `booking_group`: the booker, spa, group size, start time, payment mode, the group total, and the
  single Razorpay order and payment for the group.
- `booking_group_member`: one row per guest booking, with the guest's seat (1 is the booker), name and optional phone.
- `booking_group_hold`: the holds made together for one group.
- `service.side_by_side_capacity`: "couples room / side by side". When set, guests of one group taking
  this service can share one room, up to this many at once (also capped by `spa_room.capacity` when it is set).
  When empty, every guest needs a room of their own. Owners set it in Catalog > a service > Couples room / side by side.
- `payment.shared_gateway_payment_id`: see Payment below.

Every guest gets an ordinary `booking` row owned by the booker. The desk, check-in, receipts, reviews,
payouts, GST invoices and health forms all keep working per guest booking.

## Availability

`POST /api/spas/{spa}/group-availability` returns the start times on a date that fit everyone. A time is
offered only when, at that start time:

- every guest has an open slot for their own treatment (so each guest's own treatment length is checked
  against rooms, therapist shifts, opening hours and closures, exactly as for one guest);
- the guests can be given distinct therapists who are qualified and free (named therapists and labels respected);
- each guest has a room of their own, except guests of the same side-by-side treatment who share one slot's room
  within its capacity.

The assignment is a small backtracking search (`groups/GroupPlanner`).

## Holds and confirmation: all or nothing

`POST /api/booking-groups/holds` and `POST /api/booking-groups` take the spa scheduling lock (the same one single
bookings use), plan every guest, and write every hold or booking in one transaction. If any guest cannot be seated,
nothing is written. Two groups racing for the same rooms cannot both win, and the loser keeps no partial hold.
Converting a group hold releases the group's own holds first and books the exact rooms and therapists held.

## Payment

- Pay at spa: every guest booking is confirmed at once.
- Online: one Razorpay order for the group total (receipt `group-<id>`). The browser pays it with the usual
  checkout and confirms through the lead (booker's) booking: `POST /api/bookings/{lead}/payments/razorpay/confirm`.
  The webhook finds the group by its order. On capture, each guest booking gets its own `payment` row for its share
  (its `remainder_cents`), `transaction_id` empty and `shared_gateway_payment_id` = the Razorpay payment. So invoices,
  GST, payouts and desk receipts stay per booking.
- Switching to pay at spa or retrying payment from any guest's booking acts on the whole group.
- A refund of one guest's share goes to the shared Razorpay payment for that share's amount only. A share's refunded
  total is counted from its own refund cases, never from the provider's total for the shared payment.

## Pricing defaults (once per order)

- The coupon is checked against the booker's treatment and the group subtotal (for minimum order values), and applied once.
- Off-peak discount and loyalty points are also worked out once, on the group subtotal.
- The total discount is split across the guests in proportion to their treatment price. Tax is then worked out per booking.
- The coupon and the points are recorded on the booker's booking, so the coupon counts as one use and the points are
  one ledger entry. Points are returned only if the booker's own booking is cancelled (the existing rule for that booking).
- A gift card is quoted once against the group total; each booking redeems its proportional share, so cancelling one guest
  returns that guest's share under the usual rules.
- Wallet credit and packages are not offered for group bookings.

## Cancelling

- One guest: the usual `POST /api/bookings/{id}/cancel` on that guest's booking. `CancellationEvaluator` applies to that
  booking, and only that guest's share is refunded. The rest of the group stays booked.
- The whole group: `GET /api/booking-groups/{id}/cancellation-quote` then `POST /api/booking-groups/{id}/cancel` with the
  refund total that was shown. Every remaining guest is cancelled under the same rules in one transaction; if any of them
  cannot be cancelled, none are.
- The spa cancels guests one at a time from the desk, as today (full refund of that share).

## Health forms

Each guest's form belongs to their own booking. The confirmation page offers the booker's own form; the other guests'
forms are on their bookings in My bookings (all guest bookings belong to the booker's account, so the booker can fill them
in with the guest), or the guest completes it at the front desk.

## Desk and schedule

`GET /api/owner/spas/{spa}/booking-groups?from&to` (owner or front desk) lists groups and their guests. The desk shows
"Couple / Group of N, guest k: name, with: …" on each appointment and the schedule marks group appointments. Each guest is
checked in, collected and closed on their own appointment.

## Not done here

- Moving one guest to another time is not blocked; it separates them from the group (the group record remains).
- Every guest booking sends its own confirmation and reminder to the booker's contact details.
- The admin refund tools that require a booking's own Razorpay transaction id do not handle shared group payments.
