#!/usr/bin/env bash
# Consistent, compressed MariaDB backup with daily/weekly retention and an optional S3 copy.
#
#   DB_NAME=nirvana DB_PASSWORD_FILE=/run/secrets/db_root scripts/backup-db.sh
#
# Environment (see docs/operations.md):
#   DB_NAME (required)        database to dump
#   DB_HOST=127.0.0.1  DB_PORT=3306  DB_USER=root
#   DB_PASSWORD or DB_PASSWORD_FILE   never passed on a command line
#   BACKUP_DIR=./backups      files land in $BACKUP_DIR/daily and $BACKUP_DIR/weekly
#   BACKUP_KEEP_DAILY=14  BACKUP_KEEP_WEEKLY=8
#   BACKUP_WEEKLY_DAY=7       ISO weekday (1=Mon .. 7=Sun) whose backup is also kept as weekly
#   BACKUP_S3_BUCKET          set to upload; BACKUP_S3_PREFIX=nirvana/db  AWS_REGION=ap-south-1
#   BACKUP_S3_ENDPOINT        optional, for S3-compatible storage (default https://<bucket>.s3.<region>.amazonaws.com)
#   MARIADB_DUMP=mariadb-dump path to the client if it is not on PATH
#
# Exit codes: 0 ok, 1 backup failed (nothing kept), 2 backup kept locally but the S3 upload failed.
set -Eeuo pipefail
umask 077

log() { printf '%s backup: %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

DB_NAME=${DB_NAME:?DB_NAME is required}
DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-3306}
DB_USER=${DB_USER:-root}
BACKUP_DIR=${BACKUP_DIR:-./backups}
KEEP_DAILY=${BACKUP_KEEP_DAILY:-14}
KEEP_WEEKLY=${BACKUP_KEEP_WEEKLY:-8}
WEEKLY_DAY=${BACKUP_WEEKLY_DAY:-7}
DUMP=${MARIADB_DUMP:-mariadb-dump}

[[ $DB_NAME =~ ^[A-Za-z0-9_]+$ ]] || die "DB_NAME may only contain letters, digits and _"
[[ $KEEP_DAILY =~ ^[1-9][0-9]*$ && $KEEP_WEEKLY =~ ^[0-9]+$ ]] || die "retention counts must be whole numbers (daily >= 1)"

if [[ -n ${DB_PASSWORD_FILE:-} ]]; then
  [[ -r $DB_PASSWORD_FILE ]] || die "cannot read DB_PASSWORD_FILE"
  password=$(<"$DB_PASSWORD_FILE")
  password=${password%$'\r'}
elif [[ -n ${DB_PASSWORD+set} ]]; then
  password=$DB_PASSWORD
else
  die "set DB_PASSWORD or DB_PASSWORD_FILE"
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
# The password goes into a private option file, so it never shows in `ps`.
cnf="$work/client.cnf"
esc=${password//\\/\\\\}
esc=${esc//\"/\\\"}
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword="%s"\n' "$DB_HOST" "$DB_PORT" "$DB_USER" "$esc" >"$cnf"
unset password esc

daily="$BACKUP_DIR/daily"
weekly="$BACKUP_DIR/weekly"
mkdir -p "$daily" "$weekly"

stamp=$(date -u '+%Y%m%dT%H%M%SZ')
name="$DB_NAME-$stamp.sql.gz"
partial="$daily/.$name.partial"
trap 'rm -rf "$work" "$partial"' EXIT

log "dumping $DB_NAME from $DB_HOST:$DB_PORT"
# --single-transaction gives a consistent InnoDB snapshot without locking writers.
"$DUMP" --defaults-extra-file="$cnf" \
  --single-transaction --quick --routines --triggers --events \
  --hex-blob --default-character-set=utf8mb4 --no-tablespaces \
  "$DB_NAME" | gzip -6 >"$partial" || die "mariadb-dump failed"

gzip -t "$partial" || die "compressed dump is corrupt"
gzip -dc "$partial" | tail -n 1 | grep -q '^-- Dump completed' || die "dump is incomplete (no completion marker)"

mv "$partial" "$daily/$name"
(cd "$daily" && sha256sum "$name" >"$name.sha256")
size=$(wc -c <"$daily/$name" | tr -d ' ')
log "wrote $daily/$name ($size bytes)"

made_weekly=
if [[ $KEEP_WEEKLY -gt 0 ]] && { [[ $(date '+%u') == "$WEEKLY_DAY" ]] || ! compgen -G "$weekly/$DB_NAME-*.sql.gz" >/dev/null; }; then
  cp "$daily/$name" "$daily/$name.sha256" "$weekly/"
  made_weekly=1
  log "kept as weekly backup"
fi

prune() { # dir keep
  local dir=$1 keep=$2 f old
  local -a found=()
  for f in "$dir/$DB_NAME"-*.sql.gz; do
    [[ -f $f && ${f##*/} =~ ^$DB_NAME-[0-9]{8}T[0-9]{6}Z\.sql\.gz$ ]] && found+=("${f##*/}")
  done
  # Timestamped names sort chronologically; keep the newest $keep.
  while IFS= read -r old; do
    [[ -n $old ]] || continue
    rm -f "$dir/$old" "$dir/$old.sha256"
    log "removed old backup $dir/$old"
  done < <(printf '%s\n' "${found[@]}" | sort -r | tail -n "+$((keep + 1))")
}
prune "$daily" "$KEEP_DAILY"
prune "$weekly" "$KEEP_WEEKLY"

date -u '+%Y-%m-%dT%H:%M:%SZ' >"$BACKUP_DIR/last-success"

upload() { # local-file s3-key
  local file=$1 key=$2
  if command -v aws >/dev/null 2>&1; then
    # Uses the normal AWS credential chain (env, profile, instance/task role).
    aws s3 cp "$file" "s3://$BACKUP_S3_BUCKET/$key" --only-show-errors --sse AES256 \
      ${BACKUP_S3_ENDPOINT:+--endpoint-url "$BACKUP_S3_ENDPOINT"}
  else
    [[ -n ${AWS_ACCESS_KEY_ID:-} && -n ${AWS_SECRET_ACCESS_KEY:-} ]] \
      || { log "no aws CLI and no AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY"; return 1; }
    local region=${AWS_REGION:-${AWS_DEFAULT_REGION:-ap-south-1}}
    local base=${BACKUP_S3_ENDPOINT:-https://$BACKUP_S3_BUCKET.s3.$region.amazonaws.com}
    [[ -n ${BACKUP_S3_ENDPOINT:-} ]] && base="${BACKUP_S3_ENDPOINT%/}/$BACKUP_S3_BUCKET"
    # Credentials go to curl on stdin (-K -), not on its command line.
    {
      printf 'user = "%s:%s"\n' "$AWS_ACCESS_KEY_ID" "$AWS_SECRET_ACCESS_KEY"
      [[ -n ${AWS_SESSION_TOKEN:-} ]] && printf 'header = "x-amz-security-token: %s"\n' "$AWS_SESSION_TOKEN"
      true
    } | curl -fsS --retry 3 --retry-delay 5 -K - \
      --aws-sigv4 "aws:amz:$region:s3" \
      -H 'x-amz-server-side-encryption: AES256' \
      -T "$file" "$base/$key" >/dev/null
  fi
}

if [[ -n ${BACKUP_S3_BUCKET:-} ]]; then
  prefix=${BACKUP_S3_PREFIX:-nirvana/db}
  prefix=${prefix%/}
  status=0
  kinds=(daily)
  [[ -n $made_weekly ]] && kinds+=(weekly)
  for kind in "${kinds[@]}"; do
    for f in "$name" "$name.sha256"; do
      if [[ $status == 0 ]] && ! upload "$daily/$f" "$prefix/$kind/$f"; then status=2; fi
    done
  done
  if [[ $status != 0 ]]; then
    log "ERROR: S3 upload failed; the local backup $daily/$name is kept"
    exit 2
  fi
  log "uploaded to s3://$BACKUP_S3_BUCKET/$prefix/"
fi

log "done"
