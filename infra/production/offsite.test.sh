#!/bin/bash
# Exercise the real offsite wrapper with synthetic files and stubbed transports. No network.
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd)/offsite.sh"
temporary=$(mktemp -d)
archive="$temporary/workforce-20261024T120000Z-123.tar.age"
cleanup(){ rm -f -- "$archive" "$archive.sha256" "$temporary/last-offsite-success"; rmdir "$temporary"; }
trap cleanup EXIT
printf 'synthetic encrypted-archive stand-in' > "$archive"
(cd "$temporary" && sha256sum "$(basename "$archive")" > "$(basename "$archive").sha256")
scp(){ echo SCP_CALLED; [ "${FAIL_COPY:-0}" = 0 ]; }
ssh(){ echo SSH_CHECKSUM_CALLED; [[ "${*: -1}" == *'sha256sum -c'* ]]; [ "${FAIL_REMOTE:-0}" = 0 ]; }
export -f scp ssh
export BACKUP_SSH_IDENTITY=/synthetic/key BACKUP_KNOWN_HOSTS=/synthetic/hosts
output=$(bash "$script" "$archive" backup@storage.example.test:/archives)
[[ "$output" == *SSH_CHECKSUM_CALLED* && -s "$temporary/last-offsite-success" ]]
echo 'PASS: success recorded only after remote checksum verification'
rm "$temporary/last-offsite-success"
export FAIL_REMOTE=1
if output=$(bash "$script" "$archive" backup@storage.example.test:/archives 2>&1); then exit 1; fi
[[ ! -e "$temporary/last-offsite-success" ]]
echo 'PASS: remote corruption/failure never records success'
export FAIL_REMOTE=0 FAIL_COPY=1
if output=$(bash "$script" "$archive" backup@storage.example.test:/archives 2>&1); then exit 1; fi
[[ "$output" != *SSH_CHECKSUM_CALLED* && ! -e "$temporary/last-offsite-success" ]]
echo 'PASS: transfer failure aborts before remote verification'
export FAIL_COPY=0
if output=$(bash "$script" "$archive" 'backup@storage.example.test:/archives;injection' 2>&1); then exit 1; fi
[[ "$output" != *SCP_CALLED* ]]
echo 'PASS: destination injection rejected before transport'
printf 'corruption' >> "$archive"
if output=$(bash "$script" "$archive" backup@storage.example.test:/archives 2>&1); then exit 1; fi
[[ "$output" != *SCP_CALLED* && ! -e "$temporary/last-offsite-success" ]]
echo 'PASS: local checksum failure aborts before transfer'
