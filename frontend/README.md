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

No hotel room/night flow is used. Customer, spa staff and administration workspaces are now available. Social OAuth browser handoff is connected when a provider is configured. Account settings shows the existing auth profile only: there is no invented /api/me update endpoint. Privacy/terms content still needs the operator's approved policy before registration is deployed publicly.

## Local integration profile

An optional local-spa profile supports an isolated MariaDB database at 127.0.0.1:3307/nirvana_spa_test. Create the empty database, set SPA_DB_PASSWORD and a strong JWT_SECRET (at least 32 characters), then run:

```powershell
./mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local-spa'
```

SPA_DB_URL and SPA_DB_USER can override local defaults. Flyway applies migrations automatically. Only use disposable data for scripts/smoke_spa.py; it registers test customers and creates/reschedules/cancels appointments. Set SPA_TEST_DATE to a date with at least two available service slots in the seeded test spa. Email/SMS and Razorpay are disabled in this profile. To test Razorpay, set RAZORPAY_ENABLED=true plus RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET and RAZORPAY_WEBHOOK_SECRET. Never put secrets in frontend environment variables.

## Validation

43 backend tests passed against isolated MariaDB, including payment/webhook safety and Spring context startup. Executable JAR packaged successfully. 20 frontend contract/payment/role tests, production build and ESLint passed. 25 live API checks passed, including slot capacity, ownership, booking, reschedule and cancellation. Browser completed a Pay at spa booking and displayed its persisted CONFIRMED status.

Razorpay gateway calls are mocked in regression tests; merchant test credentials are still required for a real gateway checkout/refund test. OTP delivery was not tested. See BACKEND-REVIEW.txt for details and deployment limits. Deployment and provider configuration are separate from this source change.

## Packages

Packages and My Packages are available in the header and footer menus. The catalog uses Nirvana's central service packages, which are the same subscriptions consumed by booking checkout. It shows configured prices, session counts, validity and eligible spas. Spa-owner-specific packages are available under My account > Spa Packages, with their own verified Razorpay checkout, purchase recovery, session history and booking redemption option.

The new /api/packages/catalog and /api/packages/mine endpoints provide customer views. POST /api/packages/{id}/checkout creates or resumes a pending Razorpay order. POST /api/packages/mine/{id}/checkout reconciles payment before retrying, and /confirm verifies the signed result and fetched capture, amount, currency and order. Webhooks also activate verified captures. Purchase price, sessions and validity are snapshotted. Client-declared payment status at the legacy /purchase endpoint is rejected.

V31 adds package checkout snapshots and the unique gateway order ID. V32 normalizes the original seed's legacy PAID package payment status. GLOBAL_BRONZE is retained as a supported legacy catalog tier. Package prices use the configured payments.razorpay.currency (INR by default).

Razorpay merchant keys and capture/webhook settings must be configured for real test-mode checkout. The local profile leaves payments disabled and displays a clear error instead of granting unpaid sessions. Test failures/dismissals leave a resumable purchase in My Packages. A customer can have one active package at a time; pending purchases must be resumed before starting another tier. Gateway-confirmed refunds revoke the package's remaining sessions.


## Customer and staff workspaces

My account exposes Notifications, Messages, My Reviews, Payments & Refunds, Corporate Benefits, Package Activity, Spa Packages, Nearby & Directions, Account & Privacy, Availability & Waitlist, Google Booking, account details and password reset. Forms use the API schemas with explicit action definitions in src/features/catalog.json. Required parameters, enums, dates, nested fields and multipart employee uploads are supported; requests contain only the selected action's fields. Backend validation and eligibility remain authoritative.

Manage spa exposes onboarding/profile/address/KYC, spa package setup, calendar/inventory/blackouts, slot synchronization, analytics, and media upload. Administration exposes corporate onboarding/deals/employees, operations, approvals, refunds, vouchers, audit/support, moderation, FAQ/content and notification templates.

Menus and direct routes are restricted using server-returned roles. ROLE_SPA_OWNER and ROLE_SPA_MANAGER (plus the existing ROLE_Spa_MANAGER spelling) see staff workspaces; ROLE_ADMIN and ROLE_PLATFORM_ADMIN see staff and administration. Backend method guards independently enforce these roles and spa ownership. JWT authentication reloads current database roles and rejects disabled accounts. Public registration still cannot self-assign staff/admin access.

Booking now reserves a ten-minute slot hold before checkout and passes its token to booking creation. Customers can join unavailable slots' waitlists. V33 normalizes legacy corporate enum values; employee responses use a bounded DTO rather than serializing user entities. V34 adds unique Razorpay order IDs and currency snapshots for spa package checkout. Central and spa-specific packages are separate redemption options.

Provider-dependent actions require configured Razorpay, email/SMS, Google or S3 credentials as applicable. Local preview does not deliver external messages or execute real payments. No profile-edit endpoint was invented where the backend only supports reading the account.


## Remaining API features connected

- Verified registration: request email OTP, verify, then complete registration with privacy consent. OTP delivery must be configured; disabled delivery produces a visible error and does not bypass verification.
- Session renewal: a protected request that receives 401 refreshes once, shares concurrent refresh work, rotates both tokens, updates server-returned roles, and clears expired sessions. Logging out during refresh cannot restore that session.
- Social login: configured provider buttons call the backend authorization endpoint. The success handler retains the result in the same browser session for a single exchange, expiring after 60 seconds. Tokens are never placed in callback URLs. Set FRONTEND_URL to the exact frontend origin; serve /api and /oauth2 through the same origin/proxy. Existing security.oauth2.client settings now bind explicitly and omit providers with blank credentials. Configure the provider callback as /oauth2/callback/{provider}. Real OAuth provider verification remains a deployment test.
- Manage spa > Full Spa Settings: protected settings reads prefill hours, days, profile, tax/currency, assignments and media metadata. Version is preserved for optimistic locking. Platform commission and featured placement require administrator authority in both UI and backend. Deactivation requires an explicit checkbox. Administrators can load inactive/unlisted spas by ID.
- Manage spa > Onboarding Drafts: save, list, resume, update, extend and delete drafts for registration, business details, address and KYC. Completed drafts are cleared after submission. Older draft formats can be reviewed without silently interpreting them as a different form. Updates return the flushed version so repeated saves work.
- Payments & Refunds > Create payment link: creates or reuses a persisted Razorpay link using the stored payment amount/currency. V35 records the link and verified link order. Subscribe the webhook to payment_link.paid in addition to payment.captured and refund.processed. Server fetches link and payment before settlement; receipt URLs are not replaced with checkout URLs. Payment links do not automatically send SMS/email. External gateway calls are mocked in tests.
- Package eligibility is shown before submitting package-funded bookings. Availability includes a dedicated therapist query. Booking creation still validates eligibility authoritatively.
- Interactive Map: lazy-loaded Leaflet/OpenStreetMap tiles, location selection, nearby-spa markers, pagination and backend travel estimates. Browser location is requested only after clicking Use my location. Server map keys are not returned to the browser. Map tiles require network access and retain OpenStreetMap attribution.
- Administration > File Management: retrieve a link, download a blob or confirm permanent deletion by object key. Admin-only query-parameter routes support keys containing slashes. S3 credentials/storage access are required; media upload remains in Manage spa.

The added /api/spas/{id}/settings route requires the same spa ownership guard as updates. /api/s3/object routes inherit the administrator guard. Drafts are scoped to their owner. The new screens also reject direct navigation for unauthorized roles.
