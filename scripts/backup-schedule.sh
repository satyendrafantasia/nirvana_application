#!/usr/bin/env bash
# Runs backup-db.sh once a day at BACKUP_AT (HH:MM, container time zone TZ). Used as the backup
# container's command. BACKUP_RUN_ON_START=true also takes one backup immediately.
set -Eeuo pipefail

at=${BACKUP_AT:-02:30}
[[ $at =~ ^([01][0-9]|2[0-3]):[0-5][0-9]$ ]] || { echo "BACKUP_AT must be HH:MM" >&2; exit 64; }
here=$(cd "$(dirname "$0")" && pwd)

run() {
  local code=0
  "$here/backup-db.sh" || code=$?
  [[ $code == 0 ]] || echo "$(date '+%Y-%m-%dT%H:%M:%S%z') schedule: backup exited with $code" >&2
}

[[ ${BACKUP_RUN_ON_START:-false} == true ]] && run

while true; do
  now=$(date +%s)
  next=$(date -d "today $at" +%s)
  (( next > now )) || next=$(date -d "tomorrow $at" +%s)
  echo "$(date '+%Y-%m-%dT%H:%M:%S%z') schedule: next backup at $(date -d "@$next" '+%Y-%m-%d %H:%M %Z')" >&2
  sleep $(( next - now ))
  run
done
