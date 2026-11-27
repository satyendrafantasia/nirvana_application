#!/usr/bin/env bash
# Exercise the three locally built images against a fresh database and a private web port.
# Optionally supply APP_IMAGE, WEB_IMAGE and BACKUP_IMAGE to select their local tags.
set -Eeuo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
project="nirvana-smoke-$(openssl rand -hex 12)"
images=("${APP_IMAGE:-nirvana-app:local}" "${WEB_IMAGE:-nirvana-web:local}" "${BACKUP_IMAGE:-nirvana-backup:local}")

# Keep checkout .env/app.env files, Compose overrides, external integrations and existing
# volumes out of this run. The backup directory is a project-scoped volume, too.
cp "$root/docker-compose.yml" "$work/docker-compose.yml"
cat > "$work/smoke.yml" <<'YAML'
volumes:
  smoke-backups: {}
YAML
cat > "$work/app.env" <<'ENV'
PUBLIC_SITE_URL=https://smoke.example.com
SCHEDULING_ENABLED=false
ENV
umask 077
cat > "$work/.env" <<ENV
APP_IMAGE=${images[0]}
WEB_IMAGE=${images[1]}
BACKUP_IMAGE=${images[2]}
DB_NAME=nirvana_smoke
DB_APP_USER=smoke_app
DB_ROOT_PASSWORD=$(openssl rand -hex 20)
DB_APP_PASSWORD=$(openssl rand -hex 20)
JWT_SECRET=$(openssl rand -hex 32)
WEB_PORT=127.0.0.1:
BACKUP_HOST_DIR=smoke-backups
ENV

compose() {
  env -i PATH="$PATH" HOME="$HOME" XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-}" \
    docker compose --project-name "$project" --env-file "$work/.env" \
    -f "$work/docker-compose.yml" -f "$work/smoke.yml" "$@"
}

cleanup() {
  local status=$?
  set +e
  if (( status != 0 )); then
    compose ps --all
    compose logs --no-color --tail 60
  fi
  compose down --volumes --remove-orphans || status=1
  rm -rf "$work"
  exit "$status"
}
trap cleanup EXIT

native=$(docker version --format '{{.Server.Os}}/{{.Server.Arch}}')
for image in "${images[@]}"; do
  built=$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$image")
  if [[ $built != "$native" ]]; then
    echo "$image was built for $built; expected $native" >&2
    exit 1
  fi
done

compose pull db redis
compose up --no-build --pull never --wait --wait-timeout 360
address=$(compose port web 8080)
[[ $address == 127.0.0.1:* ]]
curl --fail --silent --show-error --max-time 10 "http://$address/healthz" | grep -Fx ok

# Check the public proxy, the database-backed API, the dynamic sitemap and the actual
# JavaScript referenced by the server-rendered directory and detail pages.
python3 - "$address" <<'PY'
import json
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

base = "http://" + sys.argv[1]
origin = "https://smoke.example.com"

def get(path):
    with urllib.request.urlopen(base + path, timeout=20) as response:
        assert response.status == 200, path
        return response.read().decode("utf-8")

def app_asset(html):
    match = re.search(r'<script\b[^>]*\bsrc="(/assets/[^"]+\.js)"', html)
    assert match, "Page is missing its built JavaScript entry point"
    return match.group(1)

entry = app_asset(get("/"))
assert app_asset(get("/owner")) == entry
assert get(entry).strip()
assert isinstance(json.loads(get("/api/packages/catalog")), list)
assert "Sitemap: " + origin + "/sitemap.xml" in get("/robots.txt")
sitemap = ET.fromstring(get("/sitemap.xml"))
urls = [node.text for node in sitemap.findall(".//{*}loc")]
assert origin + "/spas" in urls
detail = next(url[len(origin):] for url in urls if re.fullmatch(re.escape(origin) + r"/spas/\d+", url))
for path in ("/spas", detail):
    html = get(path)
    assert "data-server-head" in html and app_asset(html) == entry, path
    if path == detail:
        assert "DaySpa" in html
PY

# Exercise the scheduler image and its dump/verification tools against the migrated test DB.
compose exec -T backup /opt/nirvana/scripts/backup-db.sh
compose exec -T backup sh -ec 'test -s /backups/last-success && cd /backups/daily && sha256sum -c ./*.sha256'
echo "Compose smoke test passed on $native."
