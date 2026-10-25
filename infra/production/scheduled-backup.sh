#!/bin/bash
# Called by systemd from the reviewed current release; never source untrusted environment files.
set -euo pipefail
umask 077
if [ "${1:-}" != --lock-held ]; then
 install -d -m 0700 /run/workforce
 exec 8>/run/workforce/maintenance.lock
 flock -n 8 || { echo 'Maintenance already active' >&2; exit 1; }
fi
: "${BACKUP_DIR:?Set BACKUP_DIR}" "${BACKUP_DESTINATION:?Set BACKUP_DESTINATION}"
bash infra/production/ops.sh workforce-prod /etc/workforce/production.env backup
archive=$(cat "$BACKUP_DIR/last-success")
[[ "$archive" =~ ^workforce-[0-9TZ]+-[0-9]+\.tar\.age$ ]] || exit 1
bash infra/production/offsite.sh "$BACKUP_DIR/$archive" "$BACKUP_DESTINATION"
