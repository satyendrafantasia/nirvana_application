# Spa owner workspace

Open `/owner/login` with a spa owner account and select the spa in the header. The selected spa is included in workspace navigation. Filters on the operational screens are URL parameters so a filtered list can be bookmarked.

## Front desk

The overview agenda uses today's date at the selected spa. The four KPI cards use the selected reporting period and link to the matching booking list. Awaiting confirmation counts confirmed appointments with a pending spa reply. Open the reply to confirm or decline. The booking lifecycle, permission checks, and customer notifications are shared with the rest of Nirvana.

Use Bookings to search by reference, guest, therapist, or service. Day and week calendars use the same paginated results. Rescheduling checks the required therapist, room, guest count, and replacement slot in the existing booking transaction. Check-in, completion, cancellation, and no-show actions are time- and status-dependent.

Payments has two views. Appointment balances use the service date. Collections and refunds use the event date. To record money at the desk, select a due-at-spa appointment, choose the method, and confirm after receiving payment. For a cancelled appointment, staff can record a cash/desk return after reimbursing the customer. Retrying either action cannot produce a second receipt or return.

## Measures and dates

- Reporting ranges include both local dates. Backend comparisons use the half-open interval from local midnight through the midnight after the last date, converted to UTC. Previous-period counts use the immediately preceding equal number of days.
- Booking volume includes all statuses. Completed visits, popular services, and the service-customer directory count completed bookings. Demand measures bookings by their local start time.
- Collections include dated gateway captures, front-desk receipts, and successful spa package purchases at collection (historical purchases use their purchase date). Redemptions contribute no additional collection. Values are grouped by currency. Central package sales are outside a single spa's ledger. Older gateway records without capture timestamps cannot be attributed to a collection date. New partial refunds are reported as incremental postings on the day they are recorded. For bookings refunded before this release, the existing database provides a cumulative amount and the last recorded refund date. Historical spa package refunds with no recorded amount or date cannot be reconstructed. The ledger is an operational report rather than a gateway settlement statement.
- Capacity measures overlapping minutes once per resource. Confirmed, arrived and completed assignments count as occupied. The denominator for therapists uses recorded or weekly roster time minus breaks, leave and closures. Rooms use open scheduled intervals minus closures. Resources without explicit working time do not contribute. Weekly templates and edited schedules reflect the records currently available, so historical utilization is an estimate where schedules have been edited.
- Active subscribers have a successful, active, unexpired subscription with a positive balance. Package buyers are scoped to this spa and purchase period. Purchase terms (price, sessions, service eligibility and expiry) are copied to a subscription and stay with it. An empty service selection means all services at that spa. Each existing tier supports one offer per spa. Set it inactive to pause sales, check Archive to retire the offer, or delete an unused unpublished draft.
- Customer history shows up to 50 records of each kind at the selected spa. The filtered payment CSV can contain up to 10,000 entries per export and excludes customer contact data.

## API

All routes below are relative to `/api/owner/spas/{spaId}/desk` and require authorized spa access.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | /summary | Counts, comparisons, booking and cash trends, top services, outstanding balances and subscriptions |
| GET | /capacity | Room and therapist utilization and local weekday/hour demand |
| GET | /records | Page of appointments, visitors, package purchases or transactions |
| GET | /transactions.csv | Export filtered financial events |
| GET | /customers/{userId} | Spa-scoped appointments, purchases and redemptions |
| GET | /bookings/{id}/history | Staff audit history, where recorded |
| POST | /bookings/{id}/reply | Confirm or decline the pending spa reply, with an optional explanation |
| POST | /bookings/{id}/move | Move a confirmed appointment with slotId and reason |
| POST | /bookings/{id}/collect | Record full desk payment with method and optional reference |
| POST | /bookings/{id}/return | Record full desk refund for a cancelled, desk-paid booking |
| GET | /package-sales | Gross lifetime spa package sales by currency |
| GET | /packages | Buyer, subscription, expiry and session counts by spa package |
| DELETE | /packages/{id} | Delete an inactive, unpublished package with no purchase history |

Use period=TODAY, YESTERDAY, WEEK, MONTH, YEAR or CUSTOM. Custom reports accept from and to as ISO dates and up to 366 days. Records accepts view=APPOINTMENTS, VISITORS, BUYERS or TRANSACTIONS, search, and a zero-based page of 25 rows. Appointment filters include status, payment, serviceId, therapistId and pending. Buyer records also accept packageId. Collections and reports use integer minor units except for the package buyer's price, which is in major currency units.
