# Operations: build, run, back up, restore

Nirvana ships as three container images built from one multi-stage [`Dockerfile`](../Dockerfile):

| Image (target) | What it is | Port |
|---|---|---|
| `app` (default) | Spring Boot API on Eclipse Temurin 17 JRE, runs as uid 10001 | 8080 |
| `web` | The React build served by nginx (unprivileged), proxying `/api`, `/oauth2` and `/login/oauth2` to `app` | 8080 |
| `backup` | MariaDB 11.4 client tools plus `scripts/backup-db.sh`, `restore-db.sh`, `backup-schedule.sh` | none |

Nothing deploys automatically. CI builds all three images on every push and pull request and
publishes them to GHCR only from `main` (see [CI](#ci)). Pick a host later and run the images
there with the environment below.

Base images are pinned to exact versions in the `ARG` lines at the top of the Dockerfile and in
`docker-compose.yml`. Update them deliberately (and together with CI's MariaDB service).

## Build

Requires Docker with BuildKit (Docker 23+ or `docker buildx`).

```sh
docker build --target app    -t nirvana-app --build-arg VITE_PUBLIC_SITE_URL=https://example.in .
docker build --target web    -t nirvana-web --build-arg VITE_PUBLIC_SITE_URL=https://example.in .
docker build --target backup -t nirvana-backup .
```

Use the same frontend build arguments for `app` and `web`. Spring serves the generated HTML shell
for spa listings, and nginx serves the matching JavaScript and CSS.

| Build argument | Meaning |
|---|---|
| `VITE_PUBLIC_SITE_URL` | Approved `https://` production origin. Empty = preview build: noindex, no canonical URLs or sitemap entries. |
| `VITE_API_BASE_URL` | Leave empty; the `web` image serves the API on the same origin. |
| `VITE_ANALYTICS_PATH` | Optional same-origin event collector path. |
| `VITE_GOOGLE_MAPS_EMBED_KEY` | Optional browser key for the Maps Embed API (restrict it to the site). |

### Routing in the `web` image

[`deploy/nginx/default.conf.template`](../deploy/nginx/default.conf.template) implements the rules in
[production-readiness.md](production-readiness.md). Spring handles `/spas`, individual spa pages,
`/robots.txt` and `/sitemap.xml`. The homepage serves `index.html`; the remaining client routes from
`frontend/src/seo/routes.ts` serve `app.html` with status 200; real files are served directly
(`/assets/*` cached for a year, HTML revalidated); anything else gets `404.html` with status 404.
**When you add a route to `routes.ts`, add it to the nginx template too.** `/management/*` (actuator)
is not proxied, so monitoring must reach the app container directly on the private network.

The `web` container replaces `X-Forwarded-For` with the connecting address so clients cannot pick
the IP the rate limiter sees. If a load balancer sits in front of it, add `set_real_ip_from` for that
load balancer in `deploy/nginx/proxy.inc`. `X-Forwarded-Proto: https` from the load balancer is passed
on so OAuth redirect URLs use `https`.

## Run locally with Docker Compose

[`docker-compose.yml`](../docker-compose.yml) runs MariaDB 11.4 (collation `utf8mb4_0900_ai_ci`),
Redis, the app, the web front and the nightly backup job.

```sh
cp .env.example .env              # fill in DB_APP_PASSWORD, DB_ROOT_PASSWORD, JWT_SECRET
cp app.env.example app.env        # optional: Razorpay, SMS, mail, OAuth, S3 ...
docker compose up -d --build
docker compose ps                 # app turns "healthy" once Flyway has migrated
# open http://localhost:8088
```

`.env` holds compose-level settings (database passwords, JWT secret, ports, backup settings).
`app.env` holds application settings and is the only file passed wholesale to the app container, so
the app never receives the database root password. Both are git-ignored and docker-ignored.

The app connects as `DB_APP_USER`, which MariaDB's entrypoint creates with full rights on
`DB_NAME` only. The backup job connects as root because `mariadb-dump --routines --events` needs
broader read access. The database has no published port; uncomment the `ports` line in the
compose file (it binds to 127.0.0.1) if you need to reach it from the host.

Health: the app image's healthcheck calls `GET /management/health`, the one actuator path that
needs no token. It reports `DOWN` when the database or Redis is unreachable. Without Redis, set
`MANAGEMENT_HEALTH_REDIS_ENABLED=false` (rate limiting then falls back to per-instance memory,
which is wrong for more than one app instance).

Stop with `docker compose down`. `docker compose down -v` also **deletes the database volume**.

## Environment variables

Everything is configured through environment variables (`src/main/resources/application.yaml`).
Any other Spring property can also be set with Spring's relaxed binding (for example
`SPRING_DATA_REDIS_HOST`).

### Required

| Variable | Meaning |
|---|---|
| `SPA_DB_URL` | JDBC URL, e.g. `jdbc:mysql://db:3306/nirvana`. The database must use `utf8mb4` / `utf8mb4_0900_ai_ci`. |
| `SPA_DB_USER`, `SPA_DB_PASSWORD` | Database account. |
| `JWT_SECRET` | Token signing secret, at least 32 random characters. Changing it signs everyone out. |
| `SPRING_JPA_DATABASE_PLATFORM` | `com.nirvana.application.config.LocalMariaDbDialect` when the database is MariaDB (MySQL Connector/J does not detect it). |

### Platform

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port inside the container. |
| `SPRING_PROFILES_ACTIVE` | none | Leave empty in production. `local-spa` is the local preview profile. |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD` | `localhost`, `6379` | Redis for shared rate limiting. |
| `MANAGEMENT_HEALTH_REDIS_ENABLED` | `true` | Set `false` when running without Redis. |
| `FORWARD_HEADERS_STRATEGY` | `none` | `native` behind the `web` container or another trusted proxy. |
| `FRONTEND_URL` | `http://localhost:5173` | Exact public frontend origin (OAuth handoff, links in messages). |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated origins allowed to call the API cross-origin. |
| `API_DOCS_ENABLED` | `false` | Swagger UI and `/v3/api-docs`. |
| `SCHEDULING_ENABLED` | `true` | Background jobs (payment reconciliation, expiry, reminders). Enable on exactly one instance if you run several. |
| `FLYWAY_REPAIR_ON_MIGRATE` | `false` | Never enable routinely; it rewrites migration history. |
| `LEGACY_REGISTRATION_ENABLED` | `false` | Unverified `/api/auth/register`. Keep `false`. |
| `TRACING_SAMPLING_PROBABILITY` | `0.1` | Share of requests traced. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP collector for traces. |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` | JVM flags (set in the image). |
| `TZ` | `Asia/Kolkata` | JVM and log time zone (set in the image). |

### Business settings

| Variable | Default | Meaning |
|---|---|---|
| `BOOKING_PAYMENT_EXPIRY_MINUTES` | `30` | Unpaid online bookings are cancelled after this. |
| `BOOKING_AUTO_COMPLETE_HOURS` | `24` | Unclosed appointments are completed this long after they end; 0 disables. |
| `BOOKING_REMINDER_LEAD_HOURS` | `24` | Customer reminder lead time; 0 disables. |
| `LOYALTY_EARN_PERCENT` | `2` | Points earned on completed appointments. |
| `RATE_LIMIT_AUTH_REQUESTS` | `20` | Auth requests per window per client. |
| `RATE_LIMIT_BYPASS_TOKEN` | empty | Captcha bypass token for trusted automation. |
| `MFA_BYPASS_TOKEN` | empty | Leave empty in production. |

### Integrations (all optional; features stay off or fail visibly without them)

| Area | Variables |
|---|---|
| Razorpay | `RAZORPAY_ENABLED`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` |
| AWS (S3 media, SNS SMS, SES mail) | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (prefer an instance/task role; region is `ap-south-1`) |
| SMS / OTP | `SMS_PROVIDER` (`AWS_SNS`, `TWOFACTOR`, `FIREBASE`), `TWOFACTOR_API_KEY`, `TWOFACTOR_OTP_TEMPLATE`, `SMS_TEMPLATE_OTP` |
| Firebase | `FIREBASE_PROJECT_ID`, `FIREBASE_WEB_API_KEY`, `FIREBASE_AUTH_DOMAIN` |
| Spa booking alerts | `SPA_ALERTS_ENABLED`, `SPA_BOOKING_SMS_ENABLED` (SNS independently of phone OTP), `SPA_ALERTS_CHANNEL` (`SMS`/`WHATSAPP`), `SPA_SMS_SENDER_ID`, `SPA_SMS_DLT_ENTITY_ID`, `SPA_SMS_TEMPLATE_NEW`, `SPA_SMS_TEMPLATE_REMINDER`, `SPA_SMS_TEMPLATE_CANCELLED`, `SPA_SMS_TEMPLATE_MOVED` |
| WhatsApp | `SPA_WHATSAPP_PHONE_NUMBER_ID`, `SPA_WHATSAPP_ACCESS_TOKEN`, `SPA_WHATSAPP_API_VERSION`, `SPA_WHATSAPP_LANGUAGE`, `SPA_WHATSAPP_TEMPLATE_*`, `GUEST_WHATSAPP_ENABLED`, `GUEST_WHATSAPP_TEMPLATE_*` |
| SendGrid | `SENDGRID_API_KEY`, `SENDGRID_FROM_EMAIL`, `SENDGRID_FROM_NAME`, `SPA_OWNER_EMAIL` |
| Google | `GOOGLE_PLACES_API_KEY`, `GOOGLE_MAPS_API_KEY`, `MAP_PROVIDER` (`google`/`mapbox`), `MAPBOX_ACCESS_TOKEN` |
| Social login | `GOOGLE_OAUTH_CLIENT_ID/SECRET`, `APPLE_OAUTH_CLIENT_ID/SECRET`, `FACEBOOK_OAUTH_CLIENT_ID/SECRET` (callback `/oauth2/callback/{provider}`) |

Keep secrets in the host's secret store or the git-ignored env files, never in the image or the
repository.

### `web` image

| Variable | Default | Meaning |
|---|---|---|
| `API_UPSTREAM` | `app:8080` | `host:port` of the app container. |

## Backups

[`scripts/backup-db.sh`](../scripts/backup-db.sh) (Linux, macOS, Git Bash) and
[`scripts/backup-db.ps1`](../scripts/backup-db.ps1) (Windows PowerShell 5.1+) do the same thing:

1. `mariadb-dump --single-transaction --quick --routines --triggers --events --hex-blob` — a
   consistent InnoDB snapshot that does not block the app.
2. gzip, then check the archive and the dump's `-- Dump completed` marker. A failed or partial dump
   is deleted and the script exits 1.
3. Save as `$BACKUP_DIR/daily/<db>-<UTC timestamp>.sql.gz` with a `.sha256` file. On
   `BACKUP_WEEKLY_DAY` (and whenever `weekly/` is empty) the same file is copied to `weekly/`.
4. Keep the newest `BACKUP_KEEP_DAILY` daily and `BACKUP_KEEP_WEEKLY` weekly files; older ones
   are deleted. Only files matching the backup name pattern are ever touched.
5. A checked dump updates `last-local-success`. With a configured bucket the job uploads
   each dump and checksum under the daily or weekly S3 prefix using server-side encryption.
   A successful upload updates `last-remote-success`. `last-success` advances only after all required steps succeed.
   Missing required remote storage or a failed upload exits 2 and retains the local dump.
   The container supports the AWS credential chain (including an instance role) through boto3.
   Windows uses AWS CLI, or curl with explicit credentials.

The password is read from `DB_PASSWORD` or `DB_PASSWORD_FILE` and written only to a private
temporary option file; it never appears on a command line, in `ps` output, or in logs.

| Variable | Default | Meaning |
|---|---|---|
| `DB_NAME` | required | Database to dump. |
| `DB_HOST`, `DB_PORT`, `DB_USER` | `127.0.0.1`, `3306`, `root` | Connection. |
| `DB_PASSWORD` / `DB_PASSWORD_FILE` | one required | Password, or a file containing it. |
| `BACKUP_DIR` | `./backups` (`/backups` in the image) | Local destination. |
| `BACKUP_KEEP_DAILY`, `BACKUP_KEEP_WEEKLY` | `14`, `8` | Retention. |
| `BACKUP_WEEKLY_DAY` | `7` | ISO weekday kept as weekly (1 = Monday, 7 = Sunday). |
| `BACKUP_S3_BUCKET`, `BACKUP_S3_PREFIX` | empty, `nirvana/db` | Off-site copy. |
| `BACKUP_S3_ENDPOINT` | AWS | Endpoint for S3-compatible storage (path-style). |
| `AWS_REGION` | `ap-south-1` | Bucket region. |
| `BACKUP_AT` | `02:30` | Daily run time for `backup-schedule.sh` (container time zone, `Asia/Kolkata`). |
| `BACKUP_RUN_ON_START` | `false` | `backup-schedule.sh` also takes one backup at start-up. |
| `MARIADB_DUMP` / `MARIADB_CLIENT` (sh), `MARIADB_BIN` (ps1) | on `PATH` | Client location. |

S3 retention: the scripts only prune local files. Add a lifecycle rule on the bucket, for
example expire `nirvana/db/daily/` after 15 days and `nirvana/db/weekly/` after 60 days. Give the
backup credentials `s3:PutObject` on that prefix only, and turn on bucket versioning or Object Lock
so a compromised host cannot delete old backups.

### Off-host recovery settings

On production set `BACKUP_REQUIRE_REMOTE=true`, `BACKUP_S3_BUCKET`, and a prefix in `.env`.
Use an EC2 instance role with write access to the designated prefix. With IMDSv2, verify the
backup container can assume that role. A container on bridge networking generally needs an
instance metadata response hop limit of 2. Use a separate recovery identity with read access.
Configure a daily check on a monitor outside the EC2 instance. Put its HTTPS ping URL in
`BACKUP_HEARTBEAT_URL` and its HTTPS failure endpoint in `BACKUP_FAILURE_URL`.
Alert on a missed check after 26 hours so a stopped host or scheduler also raises an incident.
Recreate the backup service after changing its environment. Run one backup and confirm the
dump and checksum under the expected bucket prefix using the recovery identity.

### Recovering host settings and HTTPS

Keep the exact image tags or digests, production `.env` and `app.env`, host proxy configuration,
mount definitions, and referenced credentials in an encrypted off-host copy. Make a fresh copy
after changes. Keep the recovery decryption key and S3 read access outside the instance.
The helper accepts a GnuPG **public** recipient key and a list of regular files relative to the
current directory. Supply the real paths for the deployment and include any externally mounted
Firebase credential files. The private recovery key belongs on the recovery workstation.

```bash
# Example host layout. Use the directories belonging to your installed release.
cd /
bundle="/var/backups/nirvana-config-$(date -u +%Y%m%dT%H%M%SZ).tar.gz.gpg"
sudo bash /opt/nirvana/scripts/backup-config.sh /etc/nirvana/recovery-public.asc "$bundle" \
  opt/nirvana/.env opt/nirvana/app.env etc/nginx/sites-available/nirvana
sudo aws s3 cp "$bundle" "s3://$RECOVERY_BUCKET/nirvana/config/" --sse AES256 --only-show-errors
```

On the recovery workstation, download a known release bundle. Decrypt into a new, private
staging directory, review its file names, and install each file into its intended path with
restricted ownership. This example uses the private key in the operator's GnuPG keyring.

```bash
set -o pipefail
umask 077
stage=$(mktemp -d)
gpg --decrypt "$bundle" | tar -tzf -
gpg --decrypt "$bundle" | tar -xzf - -C "$stage" --no-same-owner
```

On a replacement host, point the domain's A record or the Elastic IP at it. A host using
Certbot and nginx can serve the domain over HTTP first, issue a new certificate for its DNS
names using `sudo certbot --nginx -d nirvana-spa.in`, and validate renewal with
`sudo certbot renew --dry-run`. Add `-d www.nirvana-spa.in` if that hostname also points here.
Load a configuration referencing certificate files only after those files exist. Restore the
loopback upstream and forwarded scheme settings, run `sudo nginx -t`, then reload nginx.
Verify HTTPS, a spa page, sign-in and the signed webhook URL using the intended hostname.
Validate restored upload/media storage independently of the SQL dump.

### Running backups

With compose, the `backup` service runs `backup-schedule.sh`, which takes a backup every day at
`BACKUP_AT` into `./backups` on the host. Take one now:

```sh
docker compose run --rm backup /opt/nirvana/scripts/backup-db.sh
```

Without compose, schedule the script with cron or a systemd timer, e.g.

```cron
30 2 * * *  DB_NAME=nirvana DB_PASSWORD_FILE=/etc/nirvana/db-root BACKUP_DIR=/var/backups/nirvana /opt/nirvana/scripts/backup-db.sh >>/var/log/nirvana-backup.log 2>&1
```

On this Windows dev box (MariaDB on port 3307):

```powershell
$env:MARIADB_BIN = 'C:\Users\hp\Documents\Codex\2026-10-19\ch\work\tools\mariadb\mariadb-11.4.5-winx64\bin'
$env:DB_PASSWORD_FILE = "$HOME\.nirvana\db-root.txt"   # a file only you can read
.\scripts\backup-db.ps1 -DbName nirvana_spa_test -DbPort 3307 -BackupDir "$HOME\nirvana-backups"
```

Use Task Scheduler to run it every day. Windows blocks unsigned scripts by default, so start it as
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\path\to\scripts\backup-db.ps1 -DbName ...`
(with `MARIADB_BIN` and `DB_PASSWORD_FILE` set as user environment variables).

The dump has no `CREATE DATABASE`/`USE`, so it can be restored under any database name.
Triggers and routines keep their `DEFINER`; restore as a user who may create them (root), or make
sure the definer account exists on the target server.

## Restore

[`scripts/restore-db.sh`](../scripts/restore-db.sh) / [`scripts/restore-db.ps1`](../scripts/restore-db.ps1):

```sh
scripts/restore-db.sh [--force] [--compare-with SOURCE_DB] BACKUP.sql.gz TARGET_DB
```

It checks the `.sha256` file (when present), the gzip archive and the completion marker; creates
`TARGET_DB` with `utf8mb4_0900_ai_ci`; loads the dump; and prints the exact row count of every table.
It refuses to touch a database that already has tables unless `--force` (`-Force`) is given, in
which case that database is **dropped** first. `--compare-with` compares the row counts with another
database on the same server and exits 3 if any table differs.

### Restoring production after data loss

1. Stop writers: `docker compose stop app` (or scale the app to zero).
2. Pick the backup: the newest file in `backups/daily/`, or download it and its `.sha256` from S3
   (`aws s3 cp s3://<bucket>/nirvana/db/daily/<file> .`).
3. Restore into a **new** database first and check it:
   `docker compose run --rm backup /opt/nirvana/scripts/restore-db.sh /backups/daily/<file> nirvana_restored`
4. Either point the app at it (`DB_NAME=nirvana_restored`, then grant `DB_APP_USER` access:
   `GRANT ALL ON nirvana_restored.* TO 'nirvana'@'%'`), or restore over the original with
   `--force` once you are sure.
5. Recreate the app and backup services with the restored database settings using `docker compose up -d --force-recreate app backup`. Flyway validates the schema at start-up; check
   `docker compose logs app` and the health endpoint.

Anything written after the backup was taken is lost. For point-in-time recovery, enable the binary
log on the database server and keep it alongside the backups.

### Restore drill (do this monthly and after changing the scripts)

Restore the latest backup next to the live database and compare row counts:

```sh
docker compose run --rm backup /opt/nirvana/scripts/backup-db.sh
latest=$(ls -1 backups/daily/*.sql.gz | sort | tail -n 1)
docker compose run --rm backup /opt/nirvana/scripts/restore-db.sh \
  --compare-with nirvana "/backups/daily/$(basename "$latest")" nirvana_drill
docker compose exec db sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" -e "DROP DATABASE nirvana_drill"'
```

Expect `row counts match nirvana for every table`. On a busy database, rows written between the
backup and the comparison show up as differences; run the drill in a quiet period or compare against
a database restored from the same file. Record the date, backup file, restore time and result.

**Drill record — 2026-11-27**, Windows dev box, throwaway MariaDB 11.4.5 on 127.0.0.1:3334, with both
the PowerShell and the bash (Git Bash) scripts:

| Check | Result |
|---|---|
| Schema migrated by the full backend suite (`mvnw verify`, 257 tests, CI's environment), backed up with `backup-db.ps1`, restored with `restore-db.ps1 -CompareWith` | 92 tables, row counts identical, restore in 7–10 s |
| Same `.ps1` backup restored with `restore-db.sh --compare-with` (and the reverse direction) | identical; `.sha256` files are interchangeable |
| App started against the restored database (`ApplicationTests`) | Flyway validated 68 migrations, "schema is up to date", Hibernate validation passed |
| Synthetic database with a trigger, a procedure, an event, a binary column, Hindi text and 10,004 rows | `CHECKSUM TABLE` identical; trigger, procedure, event and `utf8mb4_0900_ai_ci` restored |
| One row deleted from the comparison database | difference reported, exit 3 |
| Restore over a database that has tables without `--force` | refused, exit 1 |
| Corrupted archive; wrong password; no password | rejected with exit 1, no partial file left |
| Retention with 5 old daily + 5 old weekly files, keep 3/2 | newest 3 daily and 2 weekly kept; unrelated files untouched |
| S3 upload (curl SigV4 path) against a local stand-in endpoint with dummy keys | 4 signed PUTs (daily + weekly, file + checksum) with SSE and session token, bytes match; unreachable endpoint → exit 2, local file kept |

Not exercised here: a real S3 bucket, the `aws` CLI upload path, and the scripts inside the
`backup` container (Docker is not installed on this machine).

## CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs on every push and pull request:

| Job | What it does |
|---|---|
| `backend` | MariaDB 11.4 service container; creates `nirvana_ci` with `utf8mb4_0900_ai_ci`; generates a one-off `JWT_SECRET`; `./mvnw verify` (all backend tests). Maven cache via `setup-java`. Test reports are uploaded when it fails. |
| `frontend` | `npm ci`, `npm run lint`, `npx tsc --noEmit`, `npx vitest run`. npm cache via `setup-node`. |
| `scripts` | Checks each shell script with ShellCheck and `bash -n`. |
| `images` | Packages the JAR from the backend test job into both app images. Native AMD64 and ARM64 Linux runners each build all three images, start an isolated Compose stack and run `scripts/smoke-compose.sh` against it. |
| `publish` | After both architectures pass on a push to `main`, publishes multi-platform `main` and `sha-<commit>` tags for each image at `ghcr.io/<owner>/<repo>/{app,web,backup}`. Verifies that both tags resolve to the same manifest with both architectures. |

Publishing uses the workflow's own `GITHUB_TOKEN` (`packages: write`), so no repository secrets are
needed. The images are private by default under the owner's GitHub packages. Set the repository
variable `VITE_PUBLIC_SITE_URL` to build the published `web` image for the production origin.
Nothing deploys: pulling an image onto a host is a manual step until a deployment target is chosen.


### Booking payments and refund recovery

Set `RAZORPAY_ENABLED`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` on the backend.
In the matching Razorpay test or live dashboard register the public HTTPS endpoint `/api/payments/razorpay/webhook` with the same webhook secret. Subscribe to `payment.captured`, `payment.failed`, `payment_link.paid`, `refund.created`, `refund.processed` and `refund.failed`. The signing secret is the secret chosen when registering the webhook. New online checkout requires all four settings. Check delivery using a test-mode booking and its event history in Razorpay.

A late capture leaves the appointment closed and creates a finance case under **Admin → Spa payouts → Booking refunds**. Review the appointment and the online and front-desk collections before approving or declining the refund. Approved cases and cancellation refunds enter the same queue. The worker retries uncertain submissions using the same provider idempotency key and checks pending refunds every five minutes. Refunds post to the customer ledger after Razorpay confirms settlement. **Retry refund** is available after a verified provider failure.

Historical cancellations that lack a provider refund ID appear as **Review required**. Enter the Razorpay refund ID for the displayed payment to verify the original transaction. When no refund exists, **Recover if never refunded** checks both the captured payment and its refund history before queuing a new submission.
