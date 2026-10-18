#!/bin/bash
# Local exit-code/journal signal, no external notification service configured.
set -euo pipefail
[ "$#" = 3 ] || { echo 'Usage: monitor.sh PROJECT ABS_ENV ABS_BACKUP_DIR' >&2; exit 2; }
project="$1"; envfile="$2"; backupdir="$3"
[[ "$project" =~ ^workforce-prod(-[a-z0-9-]+)?$ && "$envfile" = /* && "$backupdir" = /* ]] || exit 2
compose=(docker compose -p "$project" --env-file "$envfile" -f infra/production/compose.yaml)
failed=0
for service in proxy frontend backend keycloak postgres mock-sap; do
 id=$("${compose[@]}" ps -q "$service")
 state=missing
 if [ -n "$id" ]; then state=$(docker inspect --format '{{.State.Health.Status}}' "$id"); fi
 if [ "$state" != healthy ]; then echo "ALERT: $service is $state"; failed=1; fi
done
if [ ! -f "$backupdir/last-success" ] || [ -n "$(find "$backupdir/last-success" -mmin +1560)" ]; then
 echo 'ALERT: no successful local backup in 26 hours'; failed=1
fi
used=$(df -P "$backupdir" | awk 'NR==2 {gsub(/%/,"",$5);print $5}')
if [ "$used" -ge 80 ]; then echo 'ALERT: backup filesystem at least 80% full'; failed=1; fi
available=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)
if [ "$available" -lt 524288 ]; then echo 'ALERT: host available memory below 512 MiB'; failed=1; fi
exit "$failed"
