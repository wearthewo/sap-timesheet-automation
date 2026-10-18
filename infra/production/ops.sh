#!/bin/bash
# Run from repository root. Never prints resolved configuration or secrets.
set -euo pipefail
[ "$#" -ge 3 ] || { echo 'Usage: ops.sh PROJECT ENV_FILE {status|backup|restore ARCHIVE}' >&2; exit 2; }
project="$1"; envfile="$2"; action="$3"
[[ "$project" =~ ^workforce-prod(-[a-z0-9-]+)?$ ]] || { echo 'Use an explicit workforce-prod[-suffix] project' >&2; exit 2; }
[[ "$envfile" = /* ]] || { echo 'Use an absolute environment-file path' >&2; exit 2; }
compose=(docker compose -p "$project" --env-file "$envfile" -f infra/production/compose.yaml -f infra/production/backup.compose.yaml)
if [ "$action" = restore ]; then compose+=(-f infra/production/restore.compose.yaml); fi
"${compose[@]}" config --quiet
case "$action" in
 status) "${compose[@]}" ps; "${compose[@]}" exec -T postgres psql -X -U postgres -d workforce -f /dev/stdin < infra/production/operations.sql;;
 backup)
  install -d -m 0700 /run/workforce; exec 9>"/run/workforce/$project-backup.lock"; flock -n 9 || { echo 'Backup already running' >&2; exit 1; }
  running=$("${compose[@]}" ps --services --status running | grep -E '^(proxy|frontend|backend|keycloak|mock-sap)$' || true)
  restart(){ if [ -n "$running" ]; then "${compose[@]}" start $running; fi; }
  trap restart EXIT
  if [ -n "$running" ]; then "${compose[@]}" stop $running; fi
  "${compose[@]}" run --rm -e WRITERS_QUIESCED=yes backup backup
  ;;
 restore)
  [ "$#" = 4 ] || exit 2
  for service in frontend backend keycloak mock-sap; do
   [ -z "$("${compose[@]}" ps --status running -q "$service")" ] || { echo "Stop $service before restoring" >&2; exit 1; }
  done
  "${compose[@]}" run --rm -e RESTORE_CONFIRM=EMPTY_DISPOSABLE_OR_APPROVED_TARGET -e BACKUP_ARCHIVE="$4" backup restore
  ;;
 *) exit 2;;
esac
