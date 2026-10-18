#!/bin/bash
set -euo pipefail
[ "$#" = 2 ] || { echo 'Usage: offsite.sh ARCHIVE USER@HOST:/ABSOLUTE/DIRECTORY' >&2; exit 2; }
archive="$1"; destination="$2"
[[ "$archive" = *.tar.age && -f "$archive" && -f "$archive.sha256" ]] || exit 2
[[ "$destination" =~ ^[a-zA-Z0-9_.-]+@[a-zA-Z0-9.-]+:/[a-zA-Z0-9_./-]+$ ]] || { echo 'Invalid fixed SSH destination' >&2; exit 2; }
: "${BACKUP_SSH_IDENTITY:?Set private SSH key path}"
: "${BACKUP_KNOWN_HOSTS:?Set independently verified host keys file}"
scp -B -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$BACKUP_KNOWN_HOSTS" -i "$BACKUP_SSH_IDENTITY" -- "$archive" "$archive.sha256" "$destination/"
date -u +%FT%TZ > "$(dirname "$archive")/last-offsite-success"
echo 'Encrypted archive and checksum copied; rehearse recovery from the remote copy separately.'
