#!/usr/bin/env bash
# Usage: bash backup-config.sh recovery-public.asc output.tar.gz.gpg FILE [FILE ...]
# Run from the root of the relative paths to archive (often / on a server).
set -Eeuo pipefail
umask 077

(( $# >= 3 )) || { echo "Supply a public recipient key, an output file and at least one settings file" >&2; exit 64; }
recipient=$1
output=$2
shift 2
[[ -r $recipient && ! -e $output ]] || { echo "Check the recipient file and choose a new output path" >&2; exit 64; }
for item in "$@"; do
  [[ $item != /* && /$item/ != */../* && -f $item && ! -L $item ]] || {
    echo "Supply existing regular files relative to the current directory" >&2; exit 64;
  }
done
partial=$(mktemp -- "$output.XXXXXX")
trap 'rm -f -- "$partial"' EXIT
tar -czf - -- "$@" | gpg --batch --yes --recipient-file "$recipient" --output "$partial" --encrypt
# A hard link publishes the completed archive without overwriting an existing backup.
ln -- "$partial" "$output"
