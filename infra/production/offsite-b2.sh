#!/bin/bash
# Ciphertext-only transport. Does not delete remote objects or hold decryption keys.
set -euo pipefail
umask 077
[ "$#" = 1 ] || { echo 'Usage: offsite-b2.sh ARCHIVE.tar.age' >&2; exit 2; }
archive=$1
: "${B2_RCLONE_CONFIG:?Set root-owned rclone config file}" "${B2_DESTINATION:?Set workforce-b2:bucket/workforce}"
[[ "$B2_DESTINATION" =~ ^workforce-b2:[a-zA-Z0-9][a-zA-Z0-9-]{5,49}/workforce$ ]] || { echo 'Invalid B2 destination' >&2; exit 2; }
[[ "$archive" = *.tar.age && -f "$archive" && -f "$archive.sha256" ]] || exit 2
name=$(basename "$archive")
[[ "$name" =~ ^workforce-[0-9TZ]+-[0-9]+\.tar\.age$ ]] || exit 2
[ -f "$B2_RCLONE_CONFIG" ] && [ ! -L "$B2_RCLONE_CONFIG" ] || exit 2
[ "$(stat -c '%a' "$B2_RCLONE_CONFIG")" = 600 ] && [ "$(stat -c '%u' "$B2_RCLONE_CONFIG")" = "$(id -u)" ] || { echo 'Private owned config mode 0600 required' >&2; exit 2; }
if grep -q 'REPLACE_WITH' "$B2_RCLONE_CONFIG"; then echo 'Unconfigured B2 credentials' >&2; exit 2; fi
# Prevent accidental alternate destination, plaintext transport, or TLS bypass.
grep -Eq '^endpoint = https://s3\.[a-z0-9-]+\.backblazeb2\.com$' "$B2_RCLONE_CONFIG" || exit 2
if grep -Eiq 'no_check_certificate|insecure_skip_verify|disable_checksum|^\s*(http_proxy|https_proxy)\s*=' "$B2_RCLONE_CONFIG"; then exit 2; fi
(cd "$(dirname "$archive")" && sha256sum -c "$name.sha256")
expected=$(sha256sum "$archive"); expected=${expected%% *}
options=(--config "$B2_RCLONE_CONFIG" --retries 1 --low-level-retries 1 --contimeout 15s --timeout 60s --stats 0 --log-level ERROR)
# timeout bounds the complete transfer, not only individual socket inactivity.
timeout 600 rclone copyto "$archive" "$B2_DESTINATION/$name" --immutable "${options[@]}"
timeout 60 rclone copyto "$archive.sha256" "$B2_DESTINATION/$name.sha256" --immutable "${options[@]}"
actual=$(timeout 600 rclone cat "$B2_DESTINATION/$name" "${options[@]}" | sha256sum)
[ "${actual%% *}" = "$expected" ] || { echo 'B2 ciphertext checksum mismatch' >&2; exit 1; }
# Publish the marker only after the remote ciphertext has been read and verified.
date -u +%FT%TZ > "$(dirname "$archive")/last-offsite-success.tmp"
mv "$(dirname "$archive")/last-offsite-success.tmp" "$(dirname "$archive")/last-offsite-success"
echo 'B2 encrypted copy downloaded and checksum verified. Separate restore rehearsal required.'
