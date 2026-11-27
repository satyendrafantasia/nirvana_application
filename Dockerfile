# syntax=docker/dockerfile:1.7
#
# Nirvana container images. One file, three runnable targets:
#
#   docker build --target app    -t nirvana-app .    # Spring Boot API (default, last stage)
#   docker build --target web    -t nirvana-web .    # React build served by nginx, proxies /api to app
#   docker build --target backup -t nirvana-backup . # mariadb-dump + backup/restore scripts
#
# All runtime configuration comes from environment variables (see docs/operations.md).
# The frontend is compiled at build time, so its VITE_* settings are build arguments.

ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-17
ARG NODE_IMAGE=node:24.22.3-bookworm-slim
# Pin the multi-architecture index so both AMD64 and Graviton pull native Java 17 runtimes.
ARG JRE_IMAGE=eclipse-temurin:17-jre-noble@sha256:ad806840ae514b83d3d8e3a00a5c1b7302fb0a6ae02bbbaf8a8bf1ffdf5367c6
ARG NGINX_IMAGE=nginx:1.28.3-alpine
ARG MARIADB_IMAGE=mariadb:11.4.13-noble
ARG APP_JAR_SOURCE=backend-build

# ---------------------------------------------------------------- frontend build
FROM ${NODE_IMAGE} AS frontend-build
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
# Baked into the bundle. Leave VITE_PUBLIC_SITE_URL empty for a preview build (noindex, no sitemap
# entries); set it to the approved https:// origin for production.
ARG VITE_PUBLIC_SITE_URL=
ARG VITE_OPERATOR_NAME=
ARG VITE_SUPPORT_EMAIL=
ARG VITE_API_BASE_URL=
ARG VITE_ANALYTICS_PATH=
ARG VITE_GOOGLE_MAPS_EMBED_KEY=
ENV VITE_PUBLIC_SITE_URL=${VITE_PUBLIC_SITE_URL} \
    VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_ANALYTICS_PATH=${VITE_ANALYTICS_PATH} \
    VITE_GOOGLE_MAPS_EMBED_KEY=${VITE_GOOGLE_MAPS_EMBED_KEY}
RUN npm run build

# ---------------------------------------------------------------- backend build
FROM ${MAVEN_IMAGE} AS backend-build
WORKDIR /src
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline || true
COPY src ./src
# Tests need a database and run in CI (.github/workflows/ci.yml), not in the image build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && cp target/application-*.jar /src/app.jar

# CI supplies the exact Spring Boot JAR produced by the successful Maven verify job.
FROM scratch AS verified-jar
COPY .ci-artifacts/application-*.jar /src/app.jar

FROM ${APP_JAR_SOURCE} AS app-jar

# ---------------------------------------------------------------- web (nginx)
FROM ${NGINX_IMAGE} AS web
COPY deploy/nginx/nginx.conf /etc/nginx/nginx.conf
COPY deploy/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY deploy/nginx/proxy.inc /etc/nginx/snippets/proxy.inc
COPY --from=frontend-build /src/frontend/dist /usr/share/nginx/html
# nginx renders the template with these at start-up.
ENV API_UPSTREAM=app:8080 \
    NGINX_ENVSUBST_FILTER=^API_UPSTREAM$
RUN chown -R nginx:nginx /var/cache/nginx /etc/nginx/conf.d \
 && touch /tmp/nginx.pid && chown nginx:nginx /tmp/nginx.pid
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1

# ---------------------------------------------------------------- backup tools
FROM ${MARIADB_IMAGE} AS backup
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates tzdata python3-boto3 \
 && rm -rf /var/lib/apt/lists/*
COPY --chmod=0755 scripts/backup-db.sh scripts/restore-db.sh scripts/backup-schedule.sh /opt/nirvana/scripts/
ENV BACKUP_DIR=/backups TZ=Asia/Kolkata
VOLUME /backups
ENTRYPOINT []
CMD ["/opt/nirvana/scripts/backup-schedule.sh"]

# ---------------------------------------------------------------- app (default)
FROM ${JRE_IMAGE} AS app
# curl is only for the container healthcheck.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 nirvana \
 && useradd --system --uid 10001 --gid nirvana --home-dir /app --shell /usr/sbin/nologin nirvana
WORKDIR /app
COPY --from=app-jar --chown=nirvana:nirvana /src/app.jar /app/app.jar
COPY --from=frontend-build --chown=nirvana:nirvana /src/frontend/dist/app.html /app/app.html
USER nirvana:nirvana
ARG VITE_PUBLIC_SITE_URL=
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SEO_APP_SHELL=/app/app.html \
    PUBLIC_SITE_URL=${VITE_PUBLIC_SITE_URL} \
    SERVER_PORT=8080 \
    TZ=Asia/Kolkata
EXPOSE 8080
# /management/health is the one actuator path open without a token (SecurityConfig).
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD curl -fsS -o /dev/null "http://127.0.0.1:${SERVER_PORT}/management/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
