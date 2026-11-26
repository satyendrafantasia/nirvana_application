# Spa owner workspace

Open `/owner/login` with a spa owner account and select the spa in the header. The selected spa is included in workspace navigation. Filters on the operational screens are URL parameters so a filtered list can be bookmarked.

## Front-desk staff

Under Settings, the owner adds receptionists to the spa's **Front desk team** by the email or phone number of their existing Nirvana account. They sign in at `/owner/login` with their own account and see only Overview, Bookings and Payments for the spas they were added to. They can confirm or decline, check in, complete, cancel, move appointments and record desk payments. Collections and refunds, the payout estimate, the CSV export, analytics, returns, voids, packages and settings stay with the owner. Removing someone takes effect on their next request.

## Front desk

The overview agenda uses today's date at the selected spa. The four KPI cards use the selected reporting period and link to the matching booking list. Awaiting confirmation counts confirmed appointments with a pending spa reply. Open the reply to confirm or decline. The booking lifecycle, permission checks, and customer notifications are shared with the rest of Nirvana.

Use Bookings to search by reference, guest (name or phone), therapist, or service. Day and week calendars use the same paginated results. Rescheduling checks the required therapist, room, guest count, and replacement slot in the existing booking transaction. A move made by the spa doesn't apply the guest's limits (two changes, the same-day cutoff) and doesn't use up the guest's own changes; the guest is still notified. Check-in, completion, cancellation, and no-show actions are time- and status-dependent.

Payments has two views. Appointment balances use the service date. Collections and refunds use the event date. To record money at the desk, select a due-at-spa appointment, choose the method, enter the amount received (a part payment such as an advance is allowed; the rest stays due) and an optional tip, and confirm after receiving payment. Each submission carries a request key, so a retry cannot produce a second receipt. On the day a payment was recorded, the owner can void the latest desk payment for an appointment if it was recorded by mistake; the audit history keeps what was removed and why. For a cancelled appointment, the owner records a desk return after reimbursing the customer; it covers every part taken.

## Measures and dates

- Reporting ranges include both local dates. Backend comparisons use the half-open interval from local midnight through the midnight after the last date, converted to UTC. Previous-period counts use the immediately preceding equal number of days.
- Booking volume includes all statuses. Completed visits, popular services, and the service-customer directory count completed bookings. Demand measures bookings by their local start time.
- Collections are split by who holds the money: **Taken at your desk** (front-desk receipts, less desk returns) and **Paid online** (gateway captures and spa package purchases, which Nirvana holds and pays out). The expected payout is online money less online refunds less the spa's commission percentage; it is an estimate, and the payout statement (see Payouts) is final. Both use the same calculation. Tips are reported separately from service amounts.
- Collections include dated gateway captures, front-desk receipts, and successful spa package purchases at collection (historical purchases use their purchase date). Redemptions contribute no additional collection. Values are grouped by currency. Central package sales are outside a single spa's ledger. Older gateway records without capture timestamps cannot be attributed to a collection date. New partial refunds are reported as incremental postings on the day they are recorded. For bookings refunded before this release, the existing database provides a cumulative amount and the last recorded refund date. Historical spa package refunds with no recorded amount or date cannot be reconstructed. The ledger is an operational report rather than a gateway settlement statement.
- Capacity measures overlapping minutes once per resource. Confirmed, arrived and completed assignments count as occupied. The denominator for therapists uses recorded or weekly roster time minus breaks, leave and closures. Rooms use open scheduled intervals minus closures. Resources without explicit working time do not contribute. Weekly templates and edited schedules reflect the records currently available, so historical utilization is an estimate where schedules have been edited.
- Active subscribers have a successful, active, unexpired subscription with a positive balance. Package buyers are scoped to this spa and purchase period. Purchase terms (price, sessions, service eligibility and expiry) are copied to a subscription and stay with it. An empty service selection means all services at that spa. Each existing tier supports one offer per spa. Set it inactive to pause sales, check Archive to retire the offer, or delete an unused unpublished draft.
- Still to arrive counts today's confirmed appointments not yet checked in whose time hasn't ended, late guests included. Scheduling clashes count each clash once: two appointments sharing a therapist, two in the same room in different slots (guests booked into one group slot share its room by design), or an appointment inside a closure.
- Customer lists show the guest's name, or their phone number when they signed up without a name. Customer history shows up to 50 records of each kind at the selected spa. The filtered payment CSV can contain up to 10,000 entries per export, dates entries in the spa's time zone, says who holds each amount, and excludes customer contact data.

## API

All routes below are relative to `/api/owner/spas/{spaId}/desk`. Routes marked *owner* need the spa's owner or manager; the rest are also open to the spa's front-desk staff. For front-desk staff, `/summary` leaves out the money totals, cash trend, payout and service values.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | /summary | Counts, comparisons, booking and cash trends, top services, outstanding balances and subscriptions |
| GET | /capacity | *Owner.* Room and therapist utilization and local weekday/hour demand |
| GET | /records | Page of appointments, visitors, package purchases or transactions (transactions: *owner*) |
| GET | /transactions.csv | *Owner.* Export filtered financial events |
| GET | /customers/{userId} | Spa-scoped appointments, purchases and redemptions |
| GET | /bookings/{id}/history | Staff audit history, where recorded |
| POST | /bookings/{id}/reply | Confirm or decline the pending spa reply, with an optional explanation |
| POST | /bookings/{id}/move | Move a confirmed appointment with slotId and reason |
| POST | /bookings/{id}/collect | Record a desk payment: method, optional reference, amountCents (default: the whole balance), tipCents, requestKey (required for a part payment) |
| POST | /bookings/{id}/void | *Owner.* Remove the latest desk payment recorded today, with a reason |
| POST | /bookings/{id}/return | *Owner.* Record the desk refund of every part taken for a cancelled booking |
| GET | /package-sales | *Owner.* Gross lifetime spa package sales by currency |
| GET | /packages | *Owner.* Buyer, subscription, expiry and session counts by spa package |
| DELETE | /packages/{id} | *Owner.* Delete an inactive, unpublished package with no purchase history |

Front-desk team (owner): `GET`, `POST {contact}` and `DELETE /{userId}` on `/api/owner/spas/{spaId}/staff`. Front-desk staff list their spas with `GET /api/owner/desk-spas`.

Use period=TODAY, YESTERDAY, WEEK, MONTH, YEAR or CUSTOM. Custom reports accept from and to as ISO dates and up to 366 days. Records accepts view=APPOINTMENTS, VISITORS, BUYERS or TRANSACTIONS, search, and a zero-based page of 25 rows. Appointment filters include status, payment, serviceId, therapistId and pending. Buyer records also accept packageId. Collections and reports use integer minor units except for the package buyer's price, which is in major currency units.

## Reception and booking policies

Under Settings the spa owner can choose enquiry only, request to book, or instant booking. Spas created by lead onboarding start in enquiry mode. Changing to request or instant booking records the operator and requires an attestation covering the treatment menu, prices, capacity, hours and a working contact. Booking mode applies to new customer reservations.

A request is for one guest and one advertised slot. It expires at the earlier of the appointment start and two hours after submission. Pending requests hold no capacity and collect no money. Reception accepts or declines under Bookings. Acceptance reruns the normal booking checks in the same transaction, creates a pay-at-spa appointment, and records the answer. Customers can check My bookings for the outcome.

Under Bookings, reception can also enter a phone or walk-in appointment, choose a previous guest from that spa or create a reception-only guest profile, and select an available treatment, room slot and eligible therapist. A reception-only profile has no sign-in or marketing subscription.

Checkout clients should send a stable requestKey (8–64 letters, digits, underscores or hyphens) in POST /api/bookings. On an ambiguous response, repeat the original checkout with the same key. A refreshed hold for the same selection is permitted. Changing booking details with the same key returns a conflict. GET /api/me/checkout-attempts/{key}?spa={spaId} returns the authenticated guest's booking reference and current state when the transaction committed.

## Daily close and bank settlement

Payments → Financial reports uses integer minor units in the API and major currency units in the forms. A shift is one local business day per spa and currency. Reception enters tender totals including tips. A variance requires an explanation. Submission closes that day's desk receipt changes. An owner can approve it or reopen it with a reason. Corrections and sign-off append revisions and retain the preceding snapshot.

Platform administrators use Payments → Payout statements to match a completed bank transfer to a non-overlapping settlement period for a spa and currency. Enter the bank reference, transfer date, amount and any explained adjustment. The statement retains the booking and package lines, refunds, commission and differences. The unmatched list covers the 31 days ending on the selected date. The first version uses manual bank reconciliation. Recording a statement does not initiate a money transfer.

## Payouts

Nirvana collects online money (booking payments and spa package sales) on its own Razorpay account and pays each spa weekly. Desk money is the spa's already and is never in a payout.

- **Settlement run.** Every Monday at 03:30 UTC (`payouts.cron`), and whenever an admin presses *Run settlement now*, Nirvana gathers each spa's online payments, package sales and online refunds that are not yet in a payout, up to a cut-off: midnight India time (`payouts.zone`) at the start of today, less a hold of `payouts.hold-days` (default 2) so most refunds land before the money is paid. One payout per spa and currency: gross online less refunds, less commission at the spa's `commission_pct`, rounded to the paisa. A refund counts only when the money it returns was itself paid out (see `payouts.since`).
- **Exactly once.** Every payment, package sale and refund posting is recorded in `payout_item` under a unique key (kind, source, refund step), so re-running, or two runs at once, can't pay the same money twice. When a spa's refunds exceed its takings, no payout is made and its items carry forward to the next run. A payout of exactly zero is closed as "Nothing to transfer".
- **Transfers.** `payouts.provider=MANUAL` (the default, and the fallback when Razorpay keys are missing) writes statements only: an admin pays by bank transfer and records the bank reference (*Record as paid*). `RAZORPAY_ROUTE` makes one Route direct transfer (`POST /v1/transfers`) per payout to the spa's linked account, with an `X-Transfer-Idempotency` key per attempt, and checks transfers still in progress every hour. Route and direct transfers must be enabled on the Razorpay account. A failed transfer stays failed until an admin retries it. A claim on the payout row stops two callers from sending it at once. A payout left in progress with no answer from Razorpay can be retried after an hour, with the same idempotency key.
- **With the manual payout statement.** Payments → Payout statements (above) matches a bank transfer an admin already made to a range of business days. Both use `payout_business_day`: a payout claims the spa-local business days of the money in it (`payout_id` set), and a manual statement claims its days (`payout_id` empty). A settlement run leaves out money on days already on a manual statement, and a manual statement can't cover a day a payout has claimed, so a day is settled once. Recording a payout as paid registers its bank reference in `spa_transfer_reference`, so one reference can't settle two statements. Days of paid payouts count as matched in the unmatched-days list. Both use the same commission calculation.
- **Bank details.** Under Payouts, the owner enters the account holder, IFSC, account number (twice) and type of business. Nirvana keeps only the last four digits. With Route on, the full number is sent once to Razorpay to create or update the spa's linked account (account, stakeholder, Route product with settlement details) and is never stored or logged. An admin can instead set a linked account made on the Razorpay dashboard. Each payout records the masked account it was sent to.
- **Settings.** `PAYOUTS_PROVIDER`, `PAYOUTS_HOLD_DAYS`, `PAYOUTS_SINCE` (ignore money collected before this ISO date, which was settled some other way; set it before the first production run), `PAYOUTS_ZONE` and `PAYOUTS_CRON`. Route uses the existing `RAZORPAY_*` keys. `payouts.route-category` and `payouts.route-subcategory` set the linked account's business profile (default `healthcare` / `clinic`); check them against Razorpay's category list for spas.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | /api/owner/spas/{spaId}/payouts | *Owner.* Bank account (masked), what's waiting for the next payout, and 25 statements per page |
| GET | /api/owner/spas/{spaId}/payouts/{id} | *Owner.* One statement with every payment and refund in it |
| PUT | /api/owner/spas/{spaId}/payouts/account | *Owner.* Save bank details: accountHolder, ifsc, accountNumber, businessType |
| GET | /api/admin/payouts | *Admin.* Payouts for every spa, by status (FAILED, PENDING, IN_PROGRESS, COMPLETED) and spa |
| POST | /api/admin/payouts/run | *Admin.* Settle now, optionally up to the end of `until` (ISO date), then send what's due |
| GET | /api/admin/payouts/{id} | *Admin.* Statement and the spa's masked bank account |
| POST | /api/admin/payouts/{id}/retry | *Admin.* Send a failed or waiting payout again |
| POST | /api/admin/payouts/{id}/mark-paid | *Admin.* Record a payout paid outside the gateway, with its bank or Razorpay reference |
| PUT | /api/admin/payouts/spas/{spaId}/linked-account | *Admin.* Set the spa's Razorpay linked account id (acc_...) |

The admin screen is at `/admin/payouts` (admin menu: Business, Spa payouts).
