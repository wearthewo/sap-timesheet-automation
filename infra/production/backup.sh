#!/bin/bash
set -euo pipefail
umask 077
export PGHOST="${PGHOST:-postgres}" PGUSER=postgres PGCONNECT_TIMEOUT=10
export PGPASSWORD="$(cat /run/secrets/PG_ADMIN_PASSWORD)"
mode="${1:-}"
mkdir -p /scratch/work
cd /scratch/work
case "$mode" in
 backup)
  [ "${WRITERS_QUIESCED:-}" = "yes" ] || { echo 'Stop writers using ops.sh backup first' >&2; exit 1; }
  retention="${BACKUP_RETENTION_DAYS:-14}"
  [[ "$retention" =~ ^[1-9][0-9]*$ ]] || exit 1
  for db in workforce keycloak; do
   pg_dump -Fc --no-owner --no-acl --dbname="$db" --file="$db.dump"
   psql -X -v ON_ERROR_STOP=1 -d "$db" -f /opt/manifest.sql > "$db.manifest"
  done
  if [ -f /mock/timesheets.json ]; then cp /mock/timesheets.json mock.json; else printf '[]' > mock.json; fi
  printf 'format=1\npostgres_major=17\ncreated_utc=%s\n' "$(date -u +%FT%TZ)" > metadata
  sha256sum workforce.dump keycloak.dump mock.json workforce.manifest keycloak.manifest metadata > checksums
  name="workforce-$(date -u +%Y%m%dT%H%M%SZ)-$RANDOM.tar.age"
  tar -cf - workforce.dump keycloak.dump mock.json workforce.manifest keycloak.manifest metadata checksums | age -R /run/backup/recipients.txt -o "/out/$name.partial"
  mv "/out/$name.partial" "/out/$name"
  cd /out
  sha256sum "$name" > "$name.sha256"
  printf '%s\n' "$name" > last-success.tmp
  mv last-success.tmp last-success
  find /out -maxdepth 1 -type f -name 'workforce-*.tar.age*' -mtime "+$retention" ! -name '*.partial' -delete
  echo "Backup completed: $name"
  ;;
 restore)
  [ "${RESTORE_CONFIRM:-}" = "EMPTY_DISPOSABLE_OR_APPROVED_TARGET" ] || { echo 'Explicit restore confirmation required' >&2; exit 1; }
  archive="${BACKUP_ARCHIVE:-}"
  [[ "$archive" =~ ^workforce-[0-9TZ]+-[0-9]+\.tar\.age$ ]] || { echo 'Invalid archive filename' >&2; exit 1; }
  for db in workforce keycloak; do
   if ! table_count=$(psql -XAt -v ON_ERROR_STOP=1 -d "$db" -c "SELECT count(*) FROM pg_tables WHERE schemaname='public'"); then
    echo "Cannot verify restore target database: $db; check connectivity and initialization" >&2
    exit 1
   fi
   [ "$table_count" = 0 ] || { echo "Refusing non-empty database: $db" >&2; exit 1; }
  done
  [ ! -e /mock/timesheets.json ] || { echo 'Refusing existing Mock SAP state' >&2; exit 1; }
  (cd /out && sha256sum -c "$archive.sha256")
  age -d -i /run/backup/identity.txt "/out/$archive" > bundle.tar
  tar -tf bundle.tar | sort > actual
  printf '%s\n' checksums keycloak.dump keycloak.manifest metadata mock.json workforce.dump workforce.manifest | sort > expected
  cmp actual expected
  [ "$(tar -tvf bundle.tar | grep -c '^-')" = 7 ]
  tar -xf bundle.tar --no-same-owner
  sha256sum -c checksums
  for db in workforce keycloak; do
   pg_restore --exit-on-error --single-transaction --no-owner --no-acl --role="$db" --dbname="$db" "$db.dump"
   psql -X -v ON_ERROR_STOP=1 -d "$db" -f /opt/manifest.sql > "$db.restored"
   cmp "$db.manifest" "$db.restored"
  done
  cp mock.json /mock/timesheets.json
  chown 1000:1000 /mock/timesheets.json
  echo 'Restore verified: both database table counts/content hashes and Mock SAP state.'
  ;;
 *) echo 'Use backup or restore' >&2; exit 1;;
esac
