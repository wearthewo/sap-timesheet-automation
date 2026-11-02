#!/bin/bash
# Exercise the real restore entrypoint without a database, secrets or writes.
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd)/backup.sh"
cat() { printf 'synthetic-test-only'; }
mkdir() { :; }
cd() { :; }
psql() {
 case "$TEST_DATABASE_STATE" in
  unreachable) return 2 ;;
  populated) printf '1\n' ;;
  *) echo 'Unexpected database access' >&2; return 99 ;;
 esac
}
pg_restore() { echo 'UNEXPECTED_RESTORE' >&2; return 99; }
export -f cat mkdir cd psql pg_restore
export BACKUP_ARCHIVE=workforce-20261018T105039Z-11619.tar.age
export RESTORE_CONFIRM=EMPTY_DISPOSABLE_OR_APPROVED_TARGET
export TEST_DATABASE_STATE=unreachable
if output=$(bash "$script" restore 2>&1); then echo 'Expected connectivity failure' >&2; exit 1; fi
[[ "$output" == *'Cannot verify restore target database: workforce'* ]]
[[ "$output" != *'Refusing non-empty database'* && "$output" != *UNEXPECTED_RESTORE* ]]
echo 'PASS: inaccessible target fails closed with a connectivity diagnostic'
export TEST_DATABASE_STATE=populated
if output=$(bash "$script" restore 2>&1); then echo 'Expected nonempty rejection' >&2; exit 1; fi
[[ "$output" == *'Refusing non-empty database: workforce'* && "$output" != *UNEXPECTED_RESTORE* ]]
echo 'PASS: populated target remains protected'
export BACKUP_ARCHIVE=../../unsafe.tar.age
if output=$(bash "$script" restore 2>&1); then echo 'Expected filename rejection' >&2; exit 1; fi
[[ "$output" == *'Invalid archive filename'* && "$output" != *'Unexpected database access'* ]]
echo 'PASS: invalid filename rejected before database access'
export RESTORE_CONFIRM=no
if output=$(bash "$script" restore 2>&1); then echo 'Expected confirmation rejection' >&2; exit 1; fi
[[ "$output" == *'Explicit restore confirmation required'* && "$output" != *'Unexpected database access'* ]]
echo 'PASS: missing confirmation rejected before database access'
