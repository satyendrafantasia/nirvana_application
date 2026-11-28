# Abandoned-checkout reminders

Guests who chose a time but never finished paying get **one** short reminder with a link back to
the booking flow, prefilled with the same spa, service, therapist choice, guests and day.

## When a reminder is sent

`CheckoutRecoveryJob` runs every `checkout-recovery.interval-ms` (5 minutes) on instances where
`app.scheduling.enabled` is on. It looks at online checkouts started between
`max-age-minutes` (6 h) and `delay-minutes` (40 min) ago that are either still `PENDING_PAYMENT`
past their payment window, or were cancelled by the system with `PAYMENT_TIMEOUT`.

Each checkout is claimed under a row lock on the booking and the guest before anything is sent, and
the reminder row is written first, so a restart or a second instance never sends it twice. A send
that fails is recorded as `FAILED` and not retried.

A checkout is skipped (`CheckoutRecoveryService.Skip`) when:

| Rule | Skip |
| --- | --- |
| Pay at spa | `NOT_ONLINE` |
| Confirmed, completed, or cancelled on purpose by the guest or spa | `NOT_ABANDONED` |
| Made by the spa's front desk | `BOOKED_BY_STAFF` |
| A payment was authorised or captured | `PAID` |
| The spa sent a payment link (it has its own deadline) | `PAYMENT_LINK` |
| Still inside its payment window | `STILL_PAYABLE` |
| Too recent / too old | `TOO_RECENT` / `TOO_OLD` |
| The appointment starts in less than `min-lead-minutes` (2 h), or has passed | `SLOT_TOO_SOON` |
| Already reminded | `ALREADY_REMINDED` |
| Account deleted, deactivated or erasure requested | `ACCOUNT_INACTIVE` |
| Guest turned checkout reminders off | `OPTED_OUT` |
| Guest booked anything (or started another checkout) since | `BOOKED_SINCE` |
| Guest got a reminder in the last `per-user-hours` (24 h) | `RATE_LIMITED` |
| No email and no WhatsApp | `NO_CONTACT` |

## Links

Links carry an HMAC-SHA256-signed token: recovery id, a random nonce stored on the row, purpose and
expiry. No email, phone, name or booking reference is in any URL. The key is
`CHECKOUT_RECOVERY_TOKEN_SECRET`, or derived from `JWT_SECRET` when that is unset (rotating it
invalidates links already sent).

- `/booking/resume/<token>` (sign-in required, the same account only): records the click and
  prefills the booking flow. If the original time is free it is selected; if it was released and
  taken, the six nearest free times in the next week are offered. Expires after `link-ttl-hours` (48 h).
- `/booking/stop-reminders?t=<token>` (no sign-in): turns reminders off, with an undo.
- Mail apps get `List-Unsubscribe` / `List-Unsubscribe-Post` (RFC 8058) pointing at
  `POST /api/public/checkout-reminders/unsubscribe?t=<token>`, on `CHECKOUT_RECOVERY_API_BASE_URL`
  or, when unset, `FRONTEND_URL` (nginx forwards `/api`).

The opt-out is `app_user.checkout_reminders_opted_out_at`, separate from `marketing_opt_in`: new
accounts start with marketing off, and these reminders are about a checkout the guest started.
Signed-in guests can also read and change it at `GET/PUT /api/me/checkout-reminders`.

## Conversions and stats

A reminder counts as converted when the same guest makes a booking at the same spa that is
confirmed (or later arrived/completed/no-show) within `attribution-days` (7) of the reminder. The
recovered revenue is that booking's price + tax − discount.

- Admin: `/admin/checkout-recovery` (`GET /api/admin/checkout-recovery/stats?from&to&spaId`).
- Owner: Payments tab of the owner portal (`GET /api/owner/spas/{spa}/checkout-recovery/stats`).

## Channels

**Email** is on, through `EmailService` (SendGrid when `notifications.sendgrid.enabled`, SMTP otherwise).

**WhatsApp** is built but off (`CHECKOUT_RECOVERY_WHATSAPP_ENABLED=false`). When on, a guest who
agreed to WhatsApp updates and verified their mobile gets the reminder on WhatsApp instead of email
(still one per checkout). It also needs `GUEST_WHATSAPP_ENABLED=true` and the WhatsApp number set up
under `spa-alerts.whatsapp`.

### Template to submit to Meta

- Name: `nirvana_checkout_reminder` (or set `CHECKOUT_RECOVERY_WHATSAPP_TEMPLATE`)
- Category: **Marketing** (Meta treats abandoned-cart reminders as marketing; Utility will be rejected
  or recategorised)
- Language: English (`en`, as `SPA_WHATSAPP_LANGUAGE`)
- Body:

  > Hi {{1}}, you were booking {{2}} at {{3}} for {{4}}, but the payment didn't go through, so nothing is booked yet. Tap below to pick up where you left off. We'll show the same time if it's still free.

- Footer: `Reply STOP to opt out` (Meta adds its own marketing opt-out control as well)
- Button: **Visit website**, dynamic URL `https://<your frontend domain>/booking/resume/{{1}}`,
  label `Finish booking`

| Variable | Value | Sample for review |
| --- | --- | --- |
| `{{1}}` | Guest's first name, or "there" | Asha |
| `{{2}}` | Treatment | Deep Tissue Massage |
| `{{3}}` | Spa | Lotus Spa |
| `{{4}}` | Appointment time in the spa's time zone | Wed, 2 Dec 2026, 11:30 AM |
| Button `{{1}}` | Signed resume token | `Y3IxOnI6MTIzOm...` |
