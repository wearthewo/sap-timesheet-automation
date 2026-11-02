#!/bin/bash
set -euo pipefail
: "${BACKUP_DIR:?}" "${MONITOR_CURL_CONFIG:?}"
# Backups intentionally stop writers. Never send a false healthy signal while maintenance runs.
exec 8>/run/workforce/maintenance.lock
flock -s -n 8 || exit 0
bash infra/production/monitor.sh workforce-prod /etc/workforce/production.env "$BACKUP_DIR"
if [ ! -f "$BACKUP_DIR/last-offsite-success" ] || [ -n "$(find "$BACKUP_DIR/last-offsite-success" -mmin +1560)" ]; then
 echo 'ALERT: no verified offsite copy in 26 hours' >&2; exit 1
fi
# Only success pulses: the remote service alerts on missed pulses, including VPS/network loss.
# Configuration contains the secret endpoint; never print it or provider error bodies.
curl --config "$MONITOR_CURL_CONFIG" --proto '=https' --max-time 15 --silent --fail --output /dev/null
