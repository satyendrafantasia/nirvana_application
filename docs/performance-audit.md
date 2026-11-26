# Performance audit, November 2026

Measured on the local stack: Spring Boot on port 8081 with the dev MariaDB (about 3,100 spas,
4 Nirvana packages), and the Vite site on port 5173. Response times are from localhost, so
they show server work, not network distance.

## What was measured

| Area | Before | Verdict |
| --- | --- | --- |
| Main JavaScript every visitor downloads | 92 KB gzipped (React, router, React Query, axios); 24 routes already load on demand | Fine. Under a 170 KB budget; not worth more work now |
| Public API reads (spa search, spa page, services, reviews, FAQs) | 10-130 ms, already gzipped, search paginated (10-24 per page) | Fine |
| Spa search box | Searches on submit, not on every keystroke | Fine, no debounce needed |
| **Package catalog** (`/api/packages/catalog`) | **809 KB** (175 KB gzipped), 300-400 ms, because each package listed all ~3,100 spas | **P0, fixed** |
| **Every spa page** | Downloaded that whole catalog to show 1-4 packages | **P0, fixed** |
| **Spa page details** | `/api/spas/{id}` requested twice per visit | **P1, fixed** |
| **Booking with a Nirvana package** | Loaded all ~3,100 eligible spas twice per booking, once under a row lock that also locked those spa rows | **P0, fixed** (concurrency as well as speed) |
| **Home hero image** | 160 KB, 1100x1467, shown about 400-560 px wide | **P1, fixed** |
| **Spa card photos** | Pexels/Unsplash at a fixed width of 1000 px (about 33 KB each) for cards about 400 px wide | **P1, fixed** |
| Public package data | Sent with `no-store`, so browsers could not reuse it | P2, fixed |

## Changes

1. **Catalog without spa lists.** `/api/packages/catalog` now returns `eligibleSpaCount` per
   package (published spas only) instead of every spa, plus a `spaId` filter for one spa's
   packages. Expected size: under 1 KB instead of 809 KB.
2. **Spa search on the server.** New `GET /api/packages/spas?q=&packageId=&limit=` (public)
   finds published spas by every word in their name or city, names starting with the search
   first, at most 50 (cap 100), with the package IDs usable there. The Packages page asks it
   after typing pauses for 300 ms, cancels stale requests, and keeps the last results on screen.
3. **Spa page asks only for its own packages** (`catalog?spaId=`), and the spa details query
   stays fresh for a minute, so it is fetched once.
4. **Package eligibility is one row lookup** (`ServicePackageRepository.countEligible`) instead
   of loading every spa. The locking query no longer joins the spa list, so a package booking
   locks only its subscription and package.
5. **Images sized for the screen.** The hero has 480 / 800 / 1100 px versions (25 / 59 / 160 KB)
   chosen by `srcset`. Card photos from Pexels and Unsplash get a 400-1200 px `srcset`; those
   hosts already send AVIF/WebP. Example: one card photo is 13.5 KB at 500 px against 33 KB at 1000 px.
6. **Five-minute browser caching** for the public catalog and package spa search. These are the
   same for everyone and hold no personal data.

## Checks

- Backend: `PackageCatalogTest` (7 tests) runs against a real MariaDB, checking counts against SQL,
  the spa filter, search by name and city, hiding unpublished spas, package eligibility, caching
  headers and access without signing in. It caught one real bug (`ORDER BY 0` with an empty
  search) before release.
- Frontend: all tests pass, including new ones for the server-backed search, the spa page, and
  photo sizes.
- Not yet measured: the new endpoints' sizes and timings against the dev database. That needs
  the backend restarted with this build.

## Deliberately not done

| Suggestion | Why not now |
| --- | --- |
| Redis cache | Reads are already 10-130 ms. Google Places data may not be cached under Google's terms. Revisit with real traffic. |
| SSR / static generation | The home page already ships pre-rendered SEO content and the site generates a sitemap. A server-rendering layer is a big change for little gain at this stage. |
| CDN, load balancing, more instances | No hosting config is in this repository. Put the built `frontend/dist` behind a CDN with long-lived caching for `/assets/*` (file names are content-hashed) when deploying. |
| Swapping axios for fetch | Saves about 13 KB gzipped, but touches every API call. Low value per risk. |
| Optimistic updates | Not used for slots, bookings or payments; those wait for the server, as they should. |
| List virtualisation | Lists are paginated (24 spas) or capped (50), so there are no long lists to virtualise. |
| Self-hosting fonts | Fonts already load without blocking the page (`media=print` swap). |
| OpenTelemetry tracing | Another change already added request tracing across the browser, booking and checkout. |

## Found in passing (not performance)

- Some seed spa names are double-encoded, for example "Juniper Baths · Indiranagar". The data needs a one-off fix.
- `/packages` sends signed-out visitors to sign in, although its data is public.
