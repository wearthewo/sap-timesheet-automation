#!/bin/bash
# Execute the transport wrapper with a local fake rclone; no cloud credentials/network.
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd)/offsite-b2.sh"
temporary=$(mktemp -d)
trap 'rm -rf -- "$temporary"' EXIT
mkdir "$temporary/bin" "$temporary/remote"
export FIXTURE_ROOT="$temporary" PATH="$temporary/bin:$PATH"
export B2_RCLONE_CONFIG="$temporary/rclone.conf" B2_DESTINATION=workforce-b2:synthetic-bucket/workforce
printf '[workforce-b2]\nendpoint = https://s3.eu-central-003.backblazeb2.com\n' > "$B2_RCLONE_CONFIG"
chmod 600 "$B2_RCLONE_CONFIG"
archive="$temporary/workforce-20261028T120000Z-123.tar.age"
printf 'synthetic ciphertext fixture' > "$archive"
(cd "$temporary" && sha256sum "$(basename "$archive")" > "$(basename "$archive").sha256")
cat > "$temporary/bin/rclone" <<'STUB'
#!/bin/bash
set -euo pipefail
printf '%s\n' "$1" >> "$FIXTURE_ROOT/calls"
case "$1" in
 copyto)
  [ "${FAIL_COPY:-0}" = 0 ] || exit 1
  [[ "$*" == *--immutable* ]] || exit 1
  cp "$2" "$FIXTURE_ROOT/remote/$(basename "$3")" ;;
 cat)
  [ "${FAIL_READ:-0}" = 0 ] || exit 1
  if [ "${CORRUPT:-0}" = 1 ]; then printf 'corrupt remote'; else cat "$FIXTURE_ROOT/remote/$(basename "$2")"; fi ;;
 *) exit 1 ;;
esac
STUB
chmod 700 "$temporary/bin/rclone"
run(){ bash "$script" "$archive" > "$temporary/output" 2>&1; }
run || { cat "$temporary/output"; exit 1; }
[ -s "$temporary/last-offsite-success" ] && grep -q '^cat$' "$temporary/calls"
echo 'PASS: B2 success follows remote ciphertext read-back'
rm "$temporary/last-offsite-success"
for mode in FAIL_COPY FAIL_READ CORRUPT; do
 export "$mode=1"
 if run; then echo "Unexpected success: $mode"; exit 1; fi
 [ ! -e "$temporary/last-offsite-success" ]
 unset "$mode"
 echo "PASS: $mode does not record success"
done
for destination in 'workforce-b2:bucket/workforce;evil' 'other:synthetic-bucket/workforce' 'workforce-b2:synthetic-bucket/../other'; do
 if B2_DESTINATION="$destination" run; then exit 1; fi
done
echo 'PASS: destination namespace and injection checks'
chmod 644 "$B2_RCLONE_CONFIG"
if run; then exit 1; fi
chmod 600 "$B2_RCLONE_CONFIG"
echo 'PASS: insecure credential-file permissions rejected'
printf 'no_check_certificate = true\n' >> "$B2_RCLONE_CONFIG"
if run; then exit 1; fi
sed -i '$d' "$B2_RCLONE_CONFIG"
echo 'PASS: TLS bypass rejected'
before=$(wc -l < "$temporary/calls")
printf corruption >> "$archive"
if run; then exit 1; fi
[ "$(wc -l < "$temporary/calls")" = "$before" ]
echo 'PASS: local corruption fails before transport'
