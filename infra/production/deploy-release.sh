#!/bin/bash
# Install root-owned as /usr/local/sbin/workforce-deploy. Receives only release JSON on stdin.
# A forced-command SSH key may invoke this; Docker/root access is privileged operator access.
set -euo pipefail
umask 077
[ "$(id -u)" = 0 ] || { echo 'Root operator required' >&2; exit 1; }
install -d -m 0700 /run/workforce
exec 9>/run/workforce/maintenance.lock
flock -n 9 || { echo 'Another backup/deployment is active' >&2; exit 1; }
temporary=$(mktemp -d /run/workforce/release.XXXXXX)
revision=unvalidated
outcome=FAILED
install -d -m 0700 /var/log/workforce
finish(){
 result=$?
 printf '%s revision=%s outcome=%s exit=%s\n' "$(date -u +%FT%TZ)" "$revision" "$outcome" "$result" >> /var/log/workforce/deployments.log
 rm -f "$temporary/release.json"
 rmdir "$temporary"
}
trap finish EXIT
head -c 65537 > "$temporary/release.json"
[ "$(wc -c < "$temporary/release.json")" -le 65536 ] || exit 1
revision=$(node /opt/workforce/tools/release.mjs check "$temporary/release.json")
[[ "$revision" =~ ^[a-f0-9]{40}$ ]] || exit 1
release="/opt/workforce/releases/$revision"
[ -d "$release/.git" ] || { echo 'Operator must install the reviewed source checkout first' >&2; exit 1; }
[ "$(git -C "$release" rev-parse HEAD)" = "$revision" ] || exit 1
[ -z "$(git -C "$release" status --porcelain --untracked-files=no)" ] || { echo 'Tracked source changes rejected' >&2; exit 1; }
cd "$release"
node infra/production/validate-env.mjs --env-file /etc/workforce/production.env
node --input-type=module - "$temporary/release.json" <<'NODE'
import {readFileSync} from 'node:fs';
const release=JSON.parse(readFileSync(process.argv[2]));
const lock=JSON.parse(readFileSync('infra/production/images.lock.json'));
// The helper-patched image is published under our namespace, never as official postgres.
// Preserve the reviewed PostgreSQL major/root in the exact source checkout as well.
if(!readFileSync('infra/production/postgres.Dockerfile','utf8').split('\n').includes('FROM '+lock.postgres.reference)) throw Error('PostgreSQL root must match reviewed source pin');
if(!release.images.postgres.startsWith('ghcr.io/wearthewo/sap-timesheet-automation/postgres@sha256:')) throw Error('Reviewed PostgreSQL helper image required');
NODE
# Override contains immutable registry digests, never mutable release tags.
node /opt/workforce/tools/release.mjs compose "$temporary/release.json" release.compose.json
compose=(docker compose -p workforce-prod --env-file /etc/workforce/production.env -f infra/production/compose.yaml -f infra/production/backup.compose.yaml -f release.compose.json)
"${compose[@]}" config --quiet
"${compose[@]}" --profile operations pull --policy always
if [ -L /opt/workforce/current ]; then
 current=$(readlink -f /opt/workforce/current)
 [[ "$current" =~ ^/opt/workforce/releases/[a-f0-9]{40}$ ]] || exit 1
 # Backup/copy failure aborts before the new application is started.
 (cd "$current" && node --input-type=module <<'NODE'
import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {parseEnv} from './infra/production/validate-env.mjs';
const settings=parseEnv(readFileSync('/etc/workforce/operations.env','utf8'));
const allowed=['BACKUP_DIR','BACKUP_DESTINATION','BACKUP_SSH_IDENTITY','BACKUP_KNOWN_HOSTS','BACKUP_SSH_PORT','MONITOR_CURL_CONFIG'];
if(Object.keys(settings).some(k=>!allowed.includes(k))) throw Error('Unexpected operations setting');
const result=spawnSync('bash',['infra/production/scheduled-backup.sh','--lock-held'],{stdio:'inherit',env:{...process.env,...settings}});
process.exit(result.status??1);
NODE
 )
elif [ -n "$(docker ps -aq --filter label=com.docker.compose.project=workforce-prod)" ] || \
     [ -n "$(docker volume ls -q --filter label=com.docker.compose.project=workforce-prod)" ]; then
 echo 'Production data exists without a current release; require manual recovery review' >&2; exit 1
fi
echo 'Starting reviewed release. Failure does not trigger destructive or schema-unsafe rollback.'
"${compose[@]}" stop proxy frontend backend keycloak mock-sap
"${compose[@]}" up -d --no-build --wait --wait-timeout 600
cp "$temporary/release.json" release.json
# Recover a stale temporary symlink from an interrupted prior pointer switch.
[ ! -e /opt/workforce/current.next ] && [ ! -L /opt/workforce/current.next ] || {
 [ -L /opt/workforce/current.next ] || { echo 'Unexpected current.next file; operator review required' >&2; exit 1; }
 rm /opt/workforce/current.next
}
ln -s "$release" /opt/workforce/current.next
mv -Tf /opt/workforce/current.next /opt/workforce/current
outcome=HEALTHY_AWAITING_BUSINESS_ACCEPTANCE
echo "Release healthy: $revision. Operator must complete HTTPS/login/business acceptance."
