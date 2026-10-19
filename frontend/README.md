# Nirvana spa frontend — existing API integration

Based on codex/scaffold-modern-react-spa-with-typescript (9a2ce36). Its backend is identical to remove-extra-code (3068566), the latest backend integration commit verified on GitHub on 2026-10-19. The newer Android scaffold (019a80c) also contains the same backend.

## Run

Use Node.js and Java 17. Configure the existing MySQL database and backend integrations in environment variables. The production configuration uses MySQL; the earlier H2 room-booking demo is a different checkout and must not be used as this frontend's API.

```powershell
./mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'
cd frontend
npm ci
npm run dev
```

Open http://localhost:5173. Vite proxies /api to http://localhost:8081. Set API_PROXY_TARGET in the shell to change that target; use VITE_API_BASE_URL for a separately hosted API with appropriate server CORS configuration. For production, serve frontend/dist and reverse-proxy /api to the backend, with SPA history fallback to index.html.

The backend's existing authentication requirements are preserved: sign in before browsing spa records. Existing database accounts are required; credentials from the old room demo do not apply. Razorpay keys, OTP delivery providers and database configuration must be supplied to the backend, never to the frontend.

## Connected customer flows

- Username/password registration and login; accessToken mapping; /api/auth/me; logout token revocation; OTP request, verify and complete; login MFA verification.
- /api/spas/search pagination envelope, catalog and detail fields, currency in major units from backend minor units.
- Service-specific date/guest availability, real slot IDs, therapist and therapist-type choices scoped to that slot.
- Booking creation with guests, OFFLINE/ONLINE modes, notes, coupon, loyalty redemption and existing-package redemption.
- Razorpay order initiation, checkout and signed server confirmation. Failed/dismissed checkout keeps the created booking for retry. No client-only payment success.
- My bookings with pagination, server cancellation eligibility, cancellation confirmation, rescheduling and pending-payment retry.
- Spa reviews and FAQ display.

No hotel room/night flow is used. Admin/provider consoles, social OAuth browser handoff, password reset screens and notification management are not implemented by this frontend change. Account settings shows the existing auth profile only: there is no invented /api/me update endpoint. Privacy/terms content still needs the operator's approved policy before registration is deployed publicly.

## Local integration profile

An optional local-spa profile supports an isolated MariaDB database at 127.0.0.1:3307/nirvana_spa_test. Create the empty database, set SPA_DB_PASSWORD and a strong JWT_SECRET (at least 32 characters), then run:

```powershell
./mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local-spa'
```

SPA_DB_URL and SPA_DB_USER can override local defaults. Flyway applies migrations automatically. Only use disposable data for scripts/smoke_spa.py; it registers test customers and creates/reschedules/cancels appointments. Set SPA_TEST_DATE to a date with at least two available service slots in the seeded test spa. Email/SMS and Razorpay are disabled in this profile. To test Razorpay, set RAZORPAY_ENABLED=true plus RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET and RAZORPAY_WEBHOOK_SECRET. Never put secrets in frontend environment variables.

## Validation

28 backend tests passed against isolated MariaDB, including payment/webhook safety and Spring context startup. Executable JAR packaged successfully. 16 frontend contract/payment tests, production build and ESLint passed. 25 live API checks passed, including slot capacity, ownership, booking, reschedule and cancellation. Browser completed a Pay at spa booking and displayed its persisted CONFIRMED status.

Razorpay gateway calls are mocked in regression tests; merchant test credentials are still required for a real gateway checkout/refund test. OTP delivery was not tested. See BACKEND-REVIEW.txt for details and deployment limits. Deployment and provider configuration are separate from this source change.

## Packages

Packages and My Packages are available in the header and footer menus. The catalog uses Nirvana's central service packages, which are the same subscriptions consumed by booking checkout. It shows configured prices, session counts, validity and eligible spas. Spa-owner-specific level packages remain a separate backend product.

The new /api/packages/catalog and /api/packages/mine endpoints provide customer views. POST /api/packages/{id}/checkout creates or resumes a pending Razorpay order. POST /api/packages/mine/{id}/checkout reconciles payment before retrying, and /confirm verifies the signed result and fetched capture, amount, currency and order. Webhooks also activate verified captures. Purchase price, sessions and validity are snapshotted. Client-declared payment status at the legacy /purchase endpoint is rejected.

V31 adds package checkout snapshots and the unique gateway order ID. V32 normalizes the original seed's legacy PAID package payment status. GLOBAL_BRONZE is retained as a supported legacy catalog tier. Package prices use the configured payments.razorpay.currency (INR by default).

Razorpay merchant keys and capture/webhook settings must be configured for real test-mode checkout. The local profile leaves payments disabled and displays a clear error instead of granting unpaid sessions. Test failures/dismissals leave a resumable purchase in My Packages. A customer can have one active package at a time; pending purchases must be resumed before starting another tier. Gateway-confirmed refunds revoke the package's remaining sessions.
