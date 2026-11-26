#!/usr/bin/env bash
# Restore a backup made by backup-db.sh into a database, then print per-table row counts.
#
#   scripts/restore-db.sh [--force] [--compare-with SOURCE_DB] BACKUP.sql.gz TARGET_DB
#
#   --force                 drop and recreate TARGET_DB if it already has tables
#   --compare-with SOURCE   compare exact row counts with another database on the same server
#                           (the restore drill); exits 3 if any table differs
#
# Connection settings are the same as backup-db.sh: DB_HOST, DB_PORT, DB_USER and DB_PASSWORD or
# DB_PASSWORD_FILE. MARIADB_CLIENT=mariadb points at the client if it is not on PATH.
# Stop the app (or point it elsewhere) before restoring over the database it is using.
set -Eeuo pipefail
umask 077

log() { printf '%s restore: %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }
usage() { sed -n '2,13p' "$0" >&2; exit 64; }

force=
compare=
while [[ $# -gt 0 ]]; do
  case $1 in
    --force) force=1; shift ;;
    --compare-with) compare=${2:-}; [[ -n $compare ]] || usage; shift 2 ;;
    -h|--help) usage ;;
    --) shift; break ;;
    -*) usage ;;
    *) break ;;
  esac
done
[[ $# -eq 2 ]] || usage
file=$1
target=$2

[[ $target =~ ^[A-Za-z0-9_]+$ ]] || die "database names may only contain letters, digits and _"
[[ -z $compare || $compare =~ ^[A-Za-z0-9_]+$ ]] || die "database names may only contain letters, digits and _"
[[ -r $file ]] || die "cannot read $file"

DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-3306}
DB_USER=${DB_USER:-root}
CLIENT=${MARIADB_CLIENT:-mariadb}

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
cnf="$work/client.cnf"
esc=${password//\\/\\\\}
esc=${esc//\"/\\\"}
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword="%s"\ndefault-character-set=utf8mb4\n' \
  "$DB_HOST" "$DB_PORT" "$DB_USER" "$esc" >"$cnf"
unset password esc

sql() { "$CLIENT" --defaults-extra-file="$cnf" -N -B "$@"; }

if [[ -f $file.sha256 ]]; then
  expected=$(cut -d' ' -f1 <"$file.sha256")
  actual=$(sha256sum "$file" | cut -d' ' -f1)
  [[ $expected == "$actual" ]] || die "checksum mismatch for $file"
  log "checksum ok"
fi
gzip -t "$file" || die "$file is not a valid gzip file"
gzip -dc "$file" | tail -n 1 | grep -q '^-- Dump completed' || die "$file is an incomplete dump"

tables=$(sql -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$target'")
if [[ $tables != 0 ]]; then
  [[ -n $force ]] || die "$target already has $tables tables; pass --force to replace it"
  log "dropping existing database $target"
  sql -e "DROP DATABASE \`$target\`"
fi
sql -e "CREATE DATABASE IF NOT EXISTS \`$target\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
sql -e "ALTER DATABASE \`$target\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"

log "restoring $file into $target"
start=$SECONDS
gzip -dc "$file" | sql "$target" || die "restore failed; $target is incomplete"
log "restored in $((SECONDS - start))s"

counts() { # db -> "table<TAB>rows" lines, sorted by table
  local query
  query=$(sql -e "SET SESSION group_concat_max_len = 1048576;
    SELECT GROUP_CONCAT(CONCAT('SELECT ', QUOTE(table_name), ', COUNT(*) FROM \`', table_schema, '\`.\`', table_name, '\`')
                        ORDER BY table_name SEPARATOR ' UNION ALL ')
    FROM information_schema.tables WHERE table_schema = '$1' AND table_type = 'BASE TABLE'")
  [[ $query != NULL && -n $query ]] || return 0
  # Fed on stdin: the generated query is long and must not be mangled as a command-line argument.
  sql <<<"$query" | sort
}

counts "$target" >"$work/target.tsv"
log "$(wc -l <"$work/target.tsv" | tr -d ' ') tables, $(awk -F'\t' '{s+=$2} END {print s+0}' "$work/target.tsv") rows in $target"

if [[ -n $compare ]]; then
  counts "$compare" >"$work/source.tsv"
  if diff_out=$(diff "$work/source.tsv" "$work/target.tsv"); then
    log "row counts match $compare for every table"
  else
    log "row counts differ from $compare (< $compare, > $target):"
    printf '%s\n' "$diff_out" >&2
    exit 3
  fi
else
  cat "$work/target.tsv"
fi
