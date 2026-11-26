# Error tracking and search visibility

## Error tracking (Sentry)

Error reporting is off by default. Nothing is sent to Sentry until a DSN is configured.

### Browser (`frontend/`)

`@sentry/react` 11.12.0 is pinned. It is set at build time:

| Variable | Default | Meaning |
| --- | --- | --- |
| `VITE_SENTRY_DSN` | unset | The project's DSN (`https://<key>@<host>/<project>`). If it is unset or invalid, the Sentry chunk never loads and no data is sent. |
| `VITE_SENTRY_ENVIRONMENT` | Vite mode (`production`) | Environment tag, such as `production` or `staging`. |
| `VITE_SENTRY_RELEASE` | `nirvana-frontend@<package.json version>` | Release tag. Set it to the commit SHA in CI so errors match source maps. |
| `VITE_SENTRY_TRACES_SAMPLE_RATE` | `0.05` | Share of page loads traced for performance, capped at `0.2`. `0` turns tracing off. |

- `src/monitoring/index.ts` reads the settings. It loads `src/monitoring/sentry.ts` as a separate chunk only when a DSN is present.
- `RecoveryBoundary` wraps the whole app. It reports render errors with the component stack and shows a page with *Try again*, *My bookings* and *Return home*.
- Personal data (`src/monitoring/scrub.ts`). In v11, `sendDefaultPii` was replaced by `dataCollection`. That option is set to collect no user info, cookies, headers, query strings or request/response bodies. Before anything is sent, the following are also applied:
  - The user, `extra` data and everything in the request except a scrubbed URL are removed.
  - Query strings and fragments are removed from every URL. Numeric ids become `:id`, and private `/b/<token>` links become `/b/:token`.
  - Emails, Indian mobile numbers, 6-digit codes, `otp=`/`token=`/`password=`-style values, bearer tokens, JWTs and long random tokens are masked in messages, exception values, breadcrumbs and span names and attributes.
  - Console breadcrumbs are dropped, along with breadcrumb fields named body, input, arguments, headers or cookie.
  - Trace headers are sent only to this site's own `/api/` routes.
- Session Replay and user feedback are not enabled.

### Backend (Spring Boot)

`io.sentry:sentry-spring-boot-starter-jakarta` 8.64.0 is pinned. Its auto-configuration runs only when `sentry.dsn` is set. Spring reads the standard environment variables:

| Variable | Meaning |
| --- | --- |
| `SENTRY_DSN` | Turns reporting on. Use a separate Sentry project from the browser one. |
| `SENTRY_ENVIRONMENT` | Environment tag (defaults to `production`). |
| `SENTRY_RELEASE` | Release tag, such as the commit SHA. |

`application.yaml` sets `send-default-pii: false` and `max-request-body-size: none`. Only ERROR logs and unhandled exceptions are reported. Backend tracing stays on the existing OTLP exporter, so Sentry traces are not enabled here. `config/SentryScrubbing` removes the user, server name, headers, cookies, query string, body and extras. It applies the same masking to messages, exception values and breadcrumbs.

## Search engines and link previews

The browser app renders only in the browser, so crawlers and link-preview bots (WhatsApp, Slack, X) would see an empty page. The backend serves the crawlable pages itself.

| Path | Served by | Content |
| --- | --- | --- |
| `/robots.txt` | backend | Allows crawling, except `/api/`, `/owner`, `/admin`, `/me`, `/booking`, `/auth`, `/staff` and `/b/`. Points at the sitemap. |
| `/sitemap.xml` | backend | Home, `/spas`, one `/spas?city=<City>` per city that has a published spa, and every published spa (`is_active=1 AND is_verified=1`), each with `lastmod` from `updated_at`. |
| `/spas/{id}` | backend | The built `app.html` shell, with the spa's title, description, canonical URL, Open Graph and Twitter tags, and `DaySpa` JSON-LD. The JSON-LD includes the address, geo coordinates, opening hours, price range from the visible menu and `aggregateRating` from visible reviews. The root element holds a readable summary that the app replaces when it starts. Unpublished and unknown spas return 404 with `noindex`. |
| `/spas`, `/spas?city=X` | backend | The listing, with an `ItemList` of up to 100 published spas. The city spelling stored in the database gives the canonical URL. Any other filter, such as `serviceName`, is served `noindex`. |

Configuration:

| Variable | Meaning |
| --- | --- |
| `PUBLIC_SITE_URL` | The approved HTTPS origin, such as `https://nirvana.example.com`. Use the same value as the frontend's `VITE_PUBLIC_SITE_URL`. If it is unset, every page is `noindex`, robots.txt blocks crawling and the sitemap is empty. Localhost, `.test` and IP origins are rejected at startup. |
| `SEO_APP_SHELL` | Absolute path to the deployed `frontend/dist/app.html`. If it is unset, pages still carry their metadata, but the browser app does not load, and a warning is logged. The file is re-read when it changes, so a frontend deploy needs no backend restart. |

### Reverse proxy routing

Route these paths to the backend. Everything else stays on the static host, as described in `production-readiness.md`:

```
location = /robots.txt   { proxy_pass http://backend; }
location = /sitemap.xml  { proxy_pass http://backend; }
location = /spas         { proxy_pass http://backend; }
location ~ ^/spas/[^/]+$ { proxy_pass http://backend; }
```

The frontend build still writes its own `robots.txt` and `sitemap.xml` for `vite preview`. The `robots.txt` rules match the backend's.

People get the normal app. The server tags are marked `data-server-head`. `src/seo/PageHead.tsx` keeps them on the page the visitor arrived on, and replaces them with the app's own tags after client-side navigation to another page. HTML is sent with `Cache-Control: no-cache`, so a renamed or unpublished spa is not served stale. The sitemap and robots responses can be cached for an hour.

### Tests

- `SeoPagesTest` (integration, needs MariaDB):
  - The sitemap is valid XML and contains only published spas and their cities.
  - robots.txt has the right rules.
  - Names and descriptions containing quotes, `<script>`, `&` and U+2028 are escaped in the title, meta tags and body. The JSON-LD stays parseable and cannot close its `<script>` tag.
  - The DaySpa fields are correct.
  - Unpublished spas return 404 with `noindex`.
  - City listings have a single canonical URL.
- `SeoMarkupTest`: escaping, preview (unconfigured) behaviour, origin validation and backend scrubbing.
- `frontend/src/monitoring/monitoring.test.ts`: DSN gating, environment and release tags, the sampling cap, and scrubbing of events, breadcrumbs, URLs and spans.
