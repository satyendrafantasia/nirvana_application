# Nirvana Wellness platform audit

28 November 2026 · Local release build · Baseline commit `792f3f8`

A customer signed in on a mobile-sized browser, selected a published service and available time, placed a pay-at-spa booking, saw its confirmed receipt in booking history, reviewed the cancellation quote and cancelled it. The browser used the Spring Boot API and an isolated MariaDB database throughout. A spa manager had configured two rooms for the same time and a therapist shift with a lunch break through the owner API. The production database, customers and payment gateway were outside this test.

## Findings and fixes

| Severity | Issue | Change and verification |
| --- | --- | --- |
| High | Several public pages initially returned generic, non-indexable HTML. Sharing About and Packages produced poor previews. | The release build emits route-specific titles, descriptions, Open Graph tags and canonical URLs for About, Contact, Packages, Privacy and Terms when the approved public origin is set. Both sitemaps include these pages. Read the HTTP responses before JavaScript ran and checked the Nginx routes. |
| High | A customer opening the admin gift-card URL reached the issuing form before the backend denied access. | The shared route guard checks the role before mounting admin or staff tools. Real customer and admin sessions exercised the page. A regression test asserts that the customer sees the recovery link and sends zero admin requests. Existing API authorization passed its own tests. |
| Medium | A previous notification could clear a newer one. Failures could disappear before someone could act. | Notices use independent success/status and error/alert regions. The active notice owns its timeout, and errors stay until dismissed or replaced. Tested replacement, expiration and dismissal in three engines. |
| Medium | Mobile navigation and account links competed for space, and Escape after a pointer click behaved differently in WebKit. | A single disclosure contains site and account navigation. It closes on route changes, restores focus after Escape and respects reduced motion. The brand links home. On signed-in screens, expanded account and admin menus fit at 320 px. |
| Medium | Wide financial and administrative tables had no local scrolling or keyboard access. | Added named, focusable scroll regions for affected tables. Forms and cards can shrink, and long contact details wrap. The route matrix found zero instances of document-level overflow. Wide tables retain their columns. |
| Medium | The homepage deferred its biggest image until JavaScript ran. Catalogue images could fail visibly or allow a third-party cookie request. | The same hero component now renders into initial HTML and the React page. Responsive WebP has a JPEG fallback. Known resizable image hosts load anonymously and failed catalogue images have a local fallback. The Google discovery cards load on demand. |
| Medium | The Packages footer could move through most of the viewport when the catalogue arrived. | The route and package-loading states reserve space for content. The measured layout shift is recorded below. |
| Medium | An unauthenticated Google search CTA took visitors to another search screen instead of sign-in. | The CTA opens sign-in and preserves the intended Google search destination. Protected deep links retain their query and fragment through sign-in. |
| Medium | A missing page offered little recovery, and gateway or server errors could leak infrastructure wording into the UI. | The branded 404 links to Home and Browse, with a readable HTML fallback. The app has contextual access, connection, validation and temporary-service messages. A malformed error payload cannot crash the error display. Nginx returned 404 for an unknown public page and an unknown spa. |
| Low | Footer contrast, rating text and some heading levels reduced readability. Footer and contact links also needed consistent behavior. | Updated colors, heading order, footer navigation, tap areas, focus styles and form hint associations. The footer shows the current year and the existing Bluemantrix attribution. Existing public email and spa phone links remain actionable. Front-desk contact details now open email or phone apps. |
| Low | Desktop and installation icons relied on a limited set of sizes. | Added 192 px and 512 px PNGs derived from the existing mark. Verified the manifest and icon through Nginx. |

## Functional verification

| Area | Evidence |
| --- | --- |
| Backend | Maven `verify` on the audited source completed with **353 passed, zero failures, one skipped**. The conditional real-export import test requires an external export file. A preliminary run had inherited the workstation’s SMS settings. The reported run used isolated provider configuration. |
| Frontend | **198 unit tests passed**. TypeScript, the production Vite build and ESLint passed. |
| Browser regressions | **33 passed** across installed Chrome, Playwright Firefox and Playwright WebKit. Tests include service prices while signed out, protected booking links, role denial, footer destinations, back/forward, keyboard focus, notifications and HTTP 404. |
| Responsive route coverage | 16 guest routes and 54 authenticated route visits across 320, 375, 390, 430, 768, 1024, 1440 and 1920 px. The authenticated set comprised 23 customer, 20 spa-manager and 11 admin visits, including schedule, services, therapists, packages, payments, support and feature tools. All three accounts signed in through the web form. No document overflow or uncaught JavaScript exceptions were observed in that scan. |
| Discovery, availability and booking | The browser checked live public service data, two-room inventory, a real hold, an offline confirmation and cancellation. Backend tests covered discovery authorization, partial-day leave, overlap, simultaneous booking and owner edits, room allocation and retry idempotency. |
| Online checkout | Desktop and mobile browser scenarios used a simulated gateway to exercise slot conflict, retained future date and therapist preference, rapid selection, duplicate submission, retry and confirmation. Backend tests covered signed payment confirmation, incorrect amounts or currencies, duplicate delivery and refund recovery. |
| Registration and OTP | Firebase email-link registration and later sign-in, account linking and phone proof validation passed backend tests. Browser and unit tests covered the verification UI. Real SMS, email delivery, CAPTCHA and live Razorpay settlement still need provider-side acceptance testing. |
| Links and server responses | All 60 distinct same-site paths collected from signed-in navigation returned 200. All seven footer navigation links resolved. Nginx 1.28.3 served the release files with the project route template and forwarded spa pages to Spring. Sixteen requests covered both expected 404s, indexable pages, a private sign-in page, icons, robots and sitemap. The spa response contained service and currency data in its structured offers. |
| External links | The static Google Maps terms and Google Privacy Policy links returned 200. OpenStreetMap returned 429 to the automated request. Its attribution link was retained. Individual, dynamically supplied spa websites, reviews and map destinations were outside the static-link count. |

## Lighthouse results

Mobile Lighthouse 13.6.0 with simulated throttling on the same Windows laptop and Chrome installation. Values are medians of three cold local navigations per page and revision. Columns show baseline → audited build. Production field timing will depend on the visitor’s device, network and server load.

| Page | Performance | Accessibility | Best practices | SEO | Largest contentful paint | Cumulative layout shift |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Home | 78 → 85 | 96 → 100 | 77 → 100 | 100 → 100 | 3.18 s → 3.03 s | 0.001 → 0.001 |
| About | 93 → 97 | 94 → 100 | 100 → 100 | 54 → 100 | 0.77 s → 1.92 s | 0.113 → 0.000 |
| Packages | 83 → 88 | 95 → 100 | 100 → 100 | 54 → 100 | 2.46 s → 2.60 s | 0.215 → 0.000 |

During this audit the 480 px homepage image decreased from **25,162 to 14,842 bytes**, the 800 px image from **60,331 to 30,528**, and the full-size image from **163,407 to 51,922**. The new homepage HTML exposes the hero to browsers before React starts. Lighthouse originally reported cookies on catalogue image requests. The final representative pages scored 100 for best practices.

## Source and reproducibility

| Area | Main files |
| --- | --- |
| Navigation, access, accessibility and feedback | `frontend/src/App.tsx`, `components/layout/{Navbar,Footer,RequireAuth}.tsx`, `components/common/{Input,Modal,Toast}.tsx`, `routes/AppRouter.tsx`, `main.tsx`, `api/client.ts`, `index.css` under `frontend/src` |
| Page-level behavior | Auth registration, OTP and email-link callback, booking Payment, profile settings and My Bookings, owner catalogue, front desk, schedule and room tools, admin gift cards, rewards and the static 404 page |
| Images and performance | HomeHero, Home, NearbySpas, SpaPhoto, SpaCard, ServiceCard, RatingBadge, SpaDetails, Packages and `frontend/public` |
| Public discovery | `frontend/src/seo`, `frontend/vite.config.ts`, `deploy/nginx/default.conf.template` and `src/main/java/com/nirvana/application/seo/SeoPages.java` |
| Test coverage | `frontend/e2e/platform.pw.ts`, `frontend/playwright.config.ts`, API session and metadata tests, and `.github/workflows/ci.yml` |

To reproduce the browser checks, install the frontend lockfile with `npm ci`, run `npx playwright install chromium firefox webkit`, build with `npm run build` and run `npm run test:browser`. Use `npm test` and `npm run lint` for frontend checks. For indexable release output, set `VITE_PUBLIC_SITE_URL` to the approved HTTPS origin before building. Backend integration tests need a disposable database and isolated provider configuration.

## Remaining verification

The local median performance target of 90 remains open for Home (85) and Packages (88). Native Safari, physical iOS and Android devices, and assistive-technology user testing were unavailable. WebKit and viewport emulation provide coverage for browser behavior and layout. Automated accessibility scores do not establish WCAG 2.2 AA conformance. The audit sampled authenticated records and exercised representative actions. Some device-specific interactions, every possible partner-supplied URL, and real provider delivery or charging remain unverified. Verification used a local release build.
