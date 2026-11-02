#!/bin/bash
set -euo pipefail
[ "$#" = 2 ] || { echo 'Usage: offsite.sh ARCHIVE USER@HOST:/ABSOLUTE/DIRECTORY' >&2; exit 2; }
archive="$1"; destination="$2"
[[ "$archive" = *.tar.age && -f "$archive" && -f "$archive.sha256" ]] || exit 2
[[ "$destination" =~ ^[a-zA-Z0-9_.-]+@[a-zA-Z0-9.-]+:/[a-zA-Z0-9_./-]+$ ]] || { echo 'Invalid fixed SSH destination' >&2; exit 2; }
: "${BACKUP_SSH_IDENTITY:?Set private SSH key path}"
: "${BACKUP_KNOWN_HOSTS:?Set independently verified host keys file}"
port="${BACKUP_SSH_PORT:-22}"
[[ "$port" =~ ^[0-9]{1,5}$ ]] && [ "$port" -ge 1 ] && [ "$port" -le 65535 ] || exit 2
name=$(basename "$archive")
[[ "$name" =~ ^workforce-[0-9TZ]+-[0-9]+\.tar\.age$ ]] || exit 2
remote="${destination%%:*}"; directory="${destination#*:}"
options=(-o BatchMode=yes -o ConnectTimeout=15 -o ServerAliveInterval=15 -o ServerAliveCountMax=3 -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$BACKUP_KNOWN_HOSTS" -i "$BACKUP_SSH_IDENTITY")
(cd "$(dirname "$archive")" && sha256sum -c "$name.sha256")
scp -B -P "$port" "${options[@]}" -- "$archive" "$archive.sha256" "$destination/"
ssh -p "$port" "${options[@]}" "$remote" "cd '$directory' && sha256sum -c '$name.sha256'"
date -u +%FT%TZ > "$(dirname "$archive")/last-offsite-success"
echo 'Encrypted offsite copy checksum verified; rehearse recovery from the remote copy separately.'
